package app.revanced.extension.music.stats;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads the aggregated listening data used by {@link WrappedReportActivity}.
 * All data comes from {@link ListeningDatabase}; nothing is sent anywhere.
 * {@link #load()} touches SQLite, so call it off the main thread.
 */
@SuppressWarnings("unused")
public final class WrappedReport {
    private WrappedReport() {}

    private static final int MAX_LIST_ITEMS = 5;

    public static final class Track {
        public final String title;
        public final String artist;
        public final long listenedMs;
        public final long plays;
        public final long durationMs;

        Track(String title, String artist, long listenedMs, long plays, long durationMs) {
            this.title = title;
            this.artist = artist;
            this.listenedMs = listenedMs;
            this.plays = plays;
            this.durationMs = durationMs;
        }
    }

    public static final class Artist {
        public final String name;
        public final long listenedMs;

        Artist(String name, long listenedMs) {
            this.name = name;
            this.listenedMs = listenedMs;
        }
    }

    public static final class Data {
        public long totalMs;
        public int plays;
        public int tracks;
        public int artists;
        public int activeDays;
        public final List<Track> topTracks = new ArrayList<>();
        public final List<Artist> topArtists = new ArrayList<>();
        public final long[] listensByHour = new long[24];
        public final long[] listensByDayOfWeek = new long[7];

        public boolean isEmpty() {
            return totalMs <= 0 && plays == 0;
        }
    }

    public static Data load() {
        ListeningDatabase db = ListeningDatabase.getInstance();
        Data data = new Data();
        data.totalMs = db.totalListenedMs();
        data.plays = db.totalPlayCount();
        data.tracks = db.distinctTracks();
        data.artists = db.distinctArtists();
        data.activeDays = db.activeDays();

        try (Cursor c = db.topTracksByListened(MAX_LIST_ITEMS)) {
            while (c.moveToNext()) {
                data.topTracks.add(new Track(c.getString(0), c.getString(1),
                        c.getLong(3), c.getLong(2), c.getLong(4)));
            }
        }

        try (Cursor c = db.topArtistsByListened(MAX_LIST_ITEMS)) {
            while (c.moveToNext()) {
                data.topArtists.add(new Artist(c.getString(0), c.getLong(1)));
            }
        }

        try (Cursor c = db.listensByHour()) {
            while (c.moveToNext()) {
                int hour = c.getInt(0);
                if (hour >= 0 && hour < 24) data.listensByHour[hour] = c.getLong(1);
            }
        }

        try (Cursor c = db.listensByDayOfWeek()) {
            while (c.moveToNext()) {
                int day = c.getInt(0);
                if (day >= 0 && day < 7) data.listensByDayOfWeek[day] = c.getLong(1);
            }
        }

        return data;
    }

    /** "42 min" or "3h 05m". */
    public static String minutes(long ms) {
        long m = ms / 60_000;
        if (m >= 60) {
            return String.format(Locale.US, "%dh %02dm", m / 60, m % 60);
        }
        return m + " min";
    }
}
