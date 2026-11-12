package app.revanced.extension.music.stats;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import app.revanced.extension.shared.Utils;

/**
 * Local SQLite store for listening data. Runs in-process; all writes must happen
 * on a background thread (see {@link SessionTracker}). Kept dependency-free
 * (plain android.database.sqlite, no Room) because the extension is packaged via .rve
 * and only has the app {@link Context} available.
 */
public final class ListeningDatabase {
    private static final String DB_NAME = "revanced_listening.db";
    private static final int DB_VERSION = 1;

    private static volatile ListeningDatabase instance;
    private final Helper helper;

    private ListeningDatabase(Context context) {
        helper = new Helper(context);
    }

    public static ListeningDatabase getInstance() {
        if (instance == null) {
            synchronized (ListeningDatabase.class) {
                if (instance == null) {
                    instance = new ListeningDatabase(Utils.getContext());
                }
            }
        }
        return instance;
    }

    private SQLiteDatabase db() {
        return helper.getWritableDatabase();
    }

    /**
     * Idempotently ensure a track row exists, keyed by title+artist, and return its id.
     */
    public long ensureTrack(String title, String artist, long durationMs) {
        SQLiteDatabase db = db();
        Cursor c = db.query("tracks", new String[]{"id"},
                "title = ? AND artist = ?", new String[]{title, artist},
                null, null, null);
        long id;
        if (c.moveToFirst()) {
            id = c.getLong(0);
            // Refresh duration if we now have a better value.
            if (durationMs > 0) {
                ContentValues cv = new ContentValues();
                cv.put("duration_ms", durationMs);
                db.update("tracks", cv, "id = ?", new String[]{String.valueOf(id)});
            }
        } else {
            ContentValues cv = new ContentValues();
            cv.put("title", title);
            cv.put("artist", artist);
            cv.put("duration_ms", durationMs);
            cv.put("first_seen_ms", System.currentTimeMillis());
            cv.put("play_count", 0);
            cv.put("total_listened_ms", 0);
            id = db.insert("tracks", null, cv);
        }
        c.close();
        return id;
    }

    /**
     * Open a new listen session for a track. Returns the new session id.
     */
    public long startSession(long trackId, long startMs) {
        ContentValues cv = new ContentValues();
        cv.put("track_id", trackId);
        cv.put("start_ms", startMs);
        cv.put("listened_ms", 0);
        return db().insert("sessions", null, cv);
    }

    /**
     * Close a session: set end time, accumulated listened time, and roll the
     * listened duration + play count up into the track row.
     */
    public void endSession(long sessionId, long trackId, long endMs, long listenedMs) {
        SQLiteDatabase db = db();
        ContentValues cv = new ContentValues();
        cv.put("end_ms", endMs);
        cv.put("listened_ms", listenedMs);
        db.update("sessions", cv, "id = ?", new String[]{String.valueOf(sessionId)});

        // Increment via raw SQL to avoid read-modify-write races.
        db.execSQL("UPDATE tracks SET play_count = play_count + 1, " +
                "total_listened_ms = total_listened_ms + ? WHERE id = ?",
                new Object[]{listenedMs, trackId});
    }

    /** Record that a session gained additional listened time (called on ticks/pause). */
    public void accumulateSession(long sessionId, long additionalMs) {
        if (additionalMs <= 0) return;
        db().execSQL("UPDATE sessions SET listened_ms = listened_ms + ? WHERE id = ?",
                new Object[]{additionalMs, sessionId});
    }

    // --- Report queries ---

    public Cursor topTracksByPlays(int limit) {
        return db().query("tracks", new String[]{"title", "artist", "play_count",
                        "total_listened_ms", "duration_ms"},
                null, null, null, null, "play_count DESC, total_listened_ms DESC",
                String.valueOf(limit));
    }

    public Cursor topTracksByListened(int limit) {
        return db().query("tracks", new String[]{"title", "artist", "play_count",
                        "total_listened_ms", "duration_ms"},
                null, null, null, null, "total_listened_ms DESC",
                String.valueOf(limit));
    }

    public Cursor topArtistsByListened(int limit) {
        return db().rawQuery(
                "SELECT artist, SUM(total_listened_ms) AS listened, COUNT(*) AS tracks " +
                        "FROM tracks GROUP BY artist ORDER BY listened DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
    }

    /** Total listened ms across all tracks. */
    public long totalListenedMs() {
        Cursor c = db().rawQuery("SELECT SUM(total_listened_ms) FROM tracks", null);
        long v = 0;
        if (c.moveToFirst() && !c.isNull(0)) v = c.getLong(0);
        c.close();
        return v;
    }

    public int totalPlayCount() {
        Cursor c = db().rawQuery("SELECT SUM(play_count) FROM tracks", null);
        int v = 0;
        if (c.moveToFirst() && !c.isNull(0)) v = c.getInt(0);
        c.close();
        return v;
    }

    public int distinctTracks() {
        Cursor c = db().rawQuery("SELECT COUNT(*) FROM tracks", null);
        int v = 0;
        if (c.moveToFirst()) v = c.getInt(0);
        c.close();
        return v;
    }

    public int distinctArtists() {
        Cursor c = db().rawQuery("SELECT COUNT(DISTINCT artist) FROM tracks", null);
        int v = 0;
        if (c.moveToFirst()) v = c.getInt(0);
        c.close();
        return v;
    }

    /** Hour-of-day distribution (0-23) of session start times. */
    public Cursor listensByHour() {
        return db().rawQuery(
                "SELECT CAST(strftime('%H', start_ms/1000, 'unixepoch') AS INTEGER) AS hour, " +
                        "COUNT(*) FROM sessions GROUP BY hour", null);
    }

    /** Day-of-week distribution (0=Sunday) of session start times. */
    public Cursor listensByDayOfWeek() {
        return db().rawQuery(
                "SELECT CAST(strftime('%w', start_ms/1000, 'unixepoch') AS INTEGER) AS dow, " +
                        "COUNT(*) FROM sessions GROUP BY dow", null);
    }

    /** Total distinct days with any listening. */
    public int activeDays() {
        Cursor c = db().rawQuery(
                "SELECT COUNT(DISTINCT strftime('%Y-%m-%d', start_ms/1000, 'unixepoch')) " +
                        "FROM sessions", null);
        int v = 0;
        if (c.moveToFirst()) v = c.getInt(0);
        c.close();
        return v;
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE tracks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "title TEXT NOT NULL, " +
                    "artist TEXT NOT NULL, " +
                    "duration_ms INTEGER DEFAULT 0, " +
                    "first_seen_ms INTEGER DEFAULT 0, " +
                    "play_count INTEGER DEFAULT 0, " +
                    "total_listened_ms INTEGER DEFAULT 0, " +
                    "UNIQUE(title, artist))");
            db.execSQL("CREATE TABLE sessions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "track_id INTEGER NOT NULL, " +
                    "start_ms INTEGER NOT NULL, " +
                    "end_ms INTEGER, " +
                    "listened_ms INTEGER DEFAULT 0, " +
                    "FOREIGN KEY(track_id) REFERENCES tracks(id))");
            db.execSQL("CREATE INDEX idx_sessions_track ON sessions(track_id)");
            db.execSQL("CREATE INDEX idx_sessions_start ON sessions(start_ms)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
            // v1: no migrations yet.
        }
    }
}
