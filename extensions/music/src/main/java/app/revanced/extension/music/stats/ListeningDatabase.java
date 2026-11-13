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
    private static final int DB_VERSION = 3;

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
        return ensureTrack(title, artist, durationMs, null);
    }

    /** Same as {@link #ensureTrack(String, String, long)} but also stores artwork. */
    public long ensureTrack(String title, String artist, long durationMs, String artworkUrl) {
        SQLiteDatabase db = db();
        long id = -1;

        // A row created before the artist was known (artist = "") is the same song:
        // reuse it instead of storing a duplicate.
        if (artist != null && !artist.isEmpty()) {
            Cursor orphan = db.query("tracks", new String[]{"id"},
                    "title = ? AND artist = ''", new String[]{title}, null, null, null);
            if (orphan.moveToFirst()) {
                id = orphan.getLong(0);
            }
            orphan.close();
        }

        Cursor c = db.query("tracks", new String[]{"id"},
                "title = ? AND artist = ?", new String[]{title, artist},
                null, null, null);
        if (c.moveToFirst()) {
            id = c.getLong(0);
        }
        c.close();

        if (id >= 0) {
            ContentValues cv = new ContentValues();
            if (durationMs > 0) {
                cv.put("duration_ms", durationMs);
            }
            if (artist != null && !artist.isEmpty()) {
                cv.put("artist", artist);
            }
            if (artworkUrl != null && !artworkUrl.isEmpty()) {
                cv.put("artwork_url", artworkUrl);
                cv.put("artwork_fetched", 1);
            }
            if (cv.size() > 0) {
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
            if (artworkUrl != null && !artworkUrl.isEmpty()) {
                cv.put("artwork_url", artworkUrl);
                cv.put("artwork_fetched", 1);
            }
            id = db.insert("tracks", null, cv);
        }
        return id;
    }

    /** Finds a track by its exact title and artist, or -1. */
    public long trackIdByNames(String title, String artist) {
        if (title == null || title.isEmpty()) return -1;
        Cursor c = db().query("tracks", new String[]{"id"},
                "title = ? AND artist = ?", new String[]{title, artist == null ? "" : artist},
                null, null, null);
        long id = -1;
        if (c.moveToFirst()) id = c.getLong(0);
        c.close();
        return id;
    }

    /** Stores (or replaces) the artwork URL of a track. */
    public void updateTrackArtwork(long trackId, String artworkUrl) {
        if (trackId < 0 || artworkUrl == null || artworkUrl.isEmpty()) return;
        ContentValues cv = new ContentValues();
        cv.put("artwork_url", artworkUrl);
        cv.put("artwork_fetched", 1);
        db().update("tracks", cv, "id = ?", new String[]{String.valueOf(trackId)});
    }

    /** Stores the artwork of a track identified by its name. */
    public void updateArtworkByName(String title, String artist, String artworkUrl) {
        if (artworkUrl == null || artworkUrl.isEmpty()) return;
        ContentValues cv = new ContentValues();
        cv.put("artwork_url", artworkUrl);
        cv.put("artwork_fetched", 1);
        if (artist == null || artist.isEmpty()) {
            db().update("tracks", cv, "title = ?", new String[]{title});
        } else {
            db().update("tracks", cv, "title = ? AND artist = ?",
                    new String[]{title, artist});
        }
    }

    /** True when the artwork of this track was already looked up (or has none). */
    public boolean trackArtworkFetched(long trackId) {
        if (trackId < 0) return true;
        Cursor c = db().query("tracks", new String[]{"artwork_fetched"},
                "id = ?", new String[]{String.valueOf(trackId)}, null, null, null);
        boolean fetched = false;
        if (c.moveToFirst()) fetched = c.getInt(0) == 1;
        c.close();
        return fetched;
    }

    /** Stores the picture of an artist, once. */
    public void setArtistArtwork(String artist, String artworkUrl) {
        if (artist == null || artist.isEmpty()) return;
        ContentValues cv = new ContentValues();
        cv.put("artist", artist);
        cv.put("artwork_url", artworkUrl);
        cv.put("fetched", 1);
        db().insertWithOnConflict("artist_artwork", null, cv,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** The stored picture of an artist, or null. */
    public String artistArtwork(String artist) {
        if (artist == null || artist.isEmpty()) return null;
        Cursor c = db().query("artist_artwork", new String[]{"artwork_url"},
                "artist = ?", new String[]{artist}, null, null, null);
        String url = null;
        if (c.moveToFirst() && !c.isNull(0)) url = c.getString(0);
        c.close();
        return url;
    }

    /** True when the artist picture was already looked up. */
    public boolean artistArtworkFetched(String artist) {
        if (artist == null || artist.isEmpty()) return true;
        Cursor c = db().query("artist_artwork", new String[]{"fetched"},
                "artist = ?", new String[]{artist}, null, null, null);
        boolean fetched = false;
        if (c.moveToFirst()) fetched = c.getInt(0) == 1;
        c.close();
        return fetched;
    }

    /**
     * Update the artist of an existing track row (metadata arrives in two passes;
     * the first one has an empty artist).
     */
    public void updateTrackArtist(long trackId, String artist) {
        if (artist == null || artist.isEmpty()) return;
        ContentValues cv = new ContentValues();
        cv.put("artist", artist);
        db().update("tracks", cv, "id = ?", new String[]{String.valueOf(trackId)});
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
                        "total_listened_ms", "duration_ms", "artwork_url"},
                null, null, null, null, "total_listened_ms DESC",
                String.valueOf(limit));
    }

    public Cursor topArtistsByListened(int limit) {
        // The picture of an artist is the one stored for that artist.
        return db().rawQuery(
                "SELECT t.artist, SUM(t.total_listened_ms) AS listened, COUNT(*) AS tracks, " +
                        "(SELECT artwork_url FROM artist_artwork a " +
                        "WHERE a.artist = t.artist AND a.artwork_url IS NOT NULL " +
                        "AND a.artwork_url != '') AS artwork " +
                        "FROM tracks t GROUP BY t.artist ORDER BY listened DESC LIMIT ?",
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

    /** Distinct session days as day indices (ms since epoch / 86400000), ascending. */
    public Cursor distinctSessionDays() {
        return db().rawQuery(
                "SELECT DISTINCT CAST(start_ms / 86400000 AS INTEGER) FROM sessions ORDER BY 1", null);
    }

    /** Number of tracks first seen at or after the given time. */
    public int tracksFirstSeenSince(long sinceMs) {
        Cursor c = db().rawQuery("SELECT COUNT(*) FROM tracks WHERE first_seen_ms >= ?",
                new String[]{String.valueOf(sinceMs)});
        int v = 0;
        if (c.moveToFirst()) v = c.getInt(0);
        c.close();
        return v;
    }

    /**
     * Top artist per month, most recent last. Columns: month (yyyy-mm), artist,
     * plays.
     */
    public Cursor topArtistPerMonth() {
        return db().rawQuery(
                "SELECT strftime('%Y-%m', s.start_ms/1000, 'unixepoch') AS month, " +
                        "t.artist AS artist, COUNT(*) AS plays " +
                        "FROM sessions s JOIN tracks t ON t.id = s.track_id " +
                        "WHERE t.artist != '' " +
                        "GROUP BY month, t.artist ORDER BY month ASC, plays DESC",
                null);
    }

    /** Artists first heard in the given period. */
    public int distinctArtistsFirstSeenSince(long sinceMs) {
        Cursor c = db().rawQuery(
                "SELECT COUNT(DISTINCT artist) FROM tracks WHERE artist != '' " +
                        "AND first_seen_ms >= ?", new String[]{String.valueOf(sinceMs)});
        int v = 0;
        if (c.moveToFirst()) v = c.getInt(0);
        c.close();
        return v;
    }

    /** Artists heard for the first time since the given moment, newest first. */
    public java.util.List<String> newArtistsSince(long sinceMs, int limit) {
        java.util.List<String> names = new java.util.ArrayList<>();
        Cursor c = db().rawQuery(
                "SELECT artist FROM tracks WHERE artist != '' AND first_seen_ms >= ? " +
                        "GROUP BY artist ORDER BY MIN(first_seen_ms) DESC LIMIT ?",
                new String[]{String.valueOf(sinceMs), String.valueOf(limit)});
        while (c.moveToNext()) names.add(c.getString(0));
        c.close();
        return names;
    }

    /**
     * Artists played in the last 30 days that were silent for a long while before.
     * The "comebacks" of the year.
     */
    public java.util.List<String> comebackArtists() {
        long now = System.currentTimeMillis();
        long recent = now - 30L * 24 * 60 * 60 * 1000;
        long silence = 90L * 24 * 60 * 60 * 1000;

        java.util.List<String> candidates = new java.util.ArrayList<>();
        Cursor c = db().rawQuery(
                "SELECT t.artist FROM sessions s JOIN tracks t ON t.id = s.track_id " +
                        "WHERE t.artist != '' GROUP BY t.artist HAVING MAX(s.start_ms) >= ?",
                new String[]{String.valueOf(recent)});
        while (c.moveToNext()) candidates.add(c.getString(0));
        c.close();

        java.util.List<String> result = new java.util.ArrayList<>();
        for (String artist : candidates) {
            Cursor p = db().rawQuery(
                    "SELECT MAX(s.start_ms) FROM sessions s JOIN tracks t ON t.id = s.track_id " +
                            "WHERE t.artist = ? AND s.start_ms < ?",
                    new String[]{artist, String.valueOf(recent)});
            long previous = 0;
            if (p.moveToFirst() && !p.isNull(0)) previous = p.getLong(0);
            p.close();
            if (previous > 0 && recent - previous > silence) result.add(artist);
        }
        return result;
    }

    /** Most listened track by a given artist, or null. Columns: title, total_listened_ms. */
    public Cursor bestTrackForArtist(String artist) {
        return db().query("tracks", new String[]{"title", "total_listened_ms"},
                "artist = ?", new String[]{artist},
                null, null, "total_listened_ms DESC", "1");
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
                    "artwork_url TEXT, " +
                    "artwork_fetched INTEGER DEFAULT 0, " +
                    "UNIQUE(title, artist))");
            db.execSQL("CREATE TABLE artist_artwork (" +
                    "artist TEXT PRIMARY KEY, " +
                    "artwork_url TEXT, " +
                    "fetched INTEGER DEFAULT 0)");
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
            if (oldV < 2) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN artwork_url TEXT");
            }
            if (oldV < 3) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN artwork_fetched INTEGER DEFAULT 0");
                db.execSQL("CREATE TABLE IF NOT EXISTS artist_artwork (" +
                        "artist TEXT PRIMARY KEY, " +
                        "artwork_url TEXT, " +
                        "fetched INTEGER DEFAULT 0)");
            }
        }
    }
}
