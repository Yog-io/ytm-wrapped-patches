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
        /** Thumbnail URL, when the track has artwork. */
        public final String artworkUrl;

        Track(String title, String artist, long listenedMs, long plays, long durationMs,
              String artworkUrl) {
            this.title = title;
            this.artist = artist;
            this.listenedMs = listenedMs;
            this.plays = plays;
            this.durationMs = durationMs;
            this.artworkUrl = artworkUrl;
        }
    }

    public static final class Artist {
        public final String name;
        public final long listenedMs;
        /** Artwork of the artist's most-played track, when available. */
        public final String artworkUrl;

        Artist(String name, long listenedMs, String artworkUrl) {
            this.name = name;
            this.listenedMs = listenedMs;
            this.artworkUrl = artworkUrl;
        }
    }

    public static final class Data {
        public long totalMs;
        public int plays;
        public int tracks;
        public int artists;
        public int activeDays;
        public int peakHour;
        public int peakDay;
        public int longestStreak;
        public int discoveries30d;
        public float topArtistShare;
        public String topArtistTrack;
        /** Average length of one listening session. */
        public long avgSessionMs;
        /** Average listening time per active day. */
        public long perDayMs;
        /** Play count of the #1 track. */
        public long topTrackPlays;
        /** Plays summed across the ranked track list. */
        public long topListensPlays;
        /** Month labels ("2026-03") and the top artist of each, oldest first. */
        public final List<String> monthlyArtists = new ArrayList<>();
        public final List<String> monthlyArtistNames = new ArrayList<>();
        /** How many of those months the overall top artist led. */
        public int topArtistMonths;
        /** Artists heard for the first time in the last year. */
        public int newArtistsThisYear;
        /** The newest of those artists, with their artwork when it is known. */
        public final List<Artist> newArtists = new ArrayList<>();
        /** Artists that came back after a long silence. */
        public final List<String> comebacks = new ArrayList<>();
        /** Listening personality derived from the peak hour. */
        public String archetype = "";
        /** Supporting line for {@link #archetype}. */
        public String archetypeLine = "";
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
        data.discoveries30d = db.tracksFirstSeenSince(
                System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000);

        try (Cursor c = db.topTracksByListened(MAX_LIST_ITEMS)) {
            while (c.moveToNext()) {
                data.topTracks.add(new Track(c.getString(0), c.getString(1),
                        c.getLong(3), c.getLong(2), c.getLong(4),
                        c.isNull(5) ? null : c.getString(5)));
            }
        }

        try (Cursor c = db.topArtistsByListened(MAX_LIST_ITEMS)) {
            while (c.moveToNext()) {
                data.topArtists.add(new Artist(c.getString(0), c.getLong(1),
                        c.isNull(3) ? null : c.getString(3)));
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

        // Peak hour and weekday.
        long maxHour = 0, maxDay = 0;
        for (int i = 0; i < 24; i++) {
            if (data.listensByHour[i] > maxHour) {
                maxHour = data.listensByHour[i];
                data.peakHour = i;
            }
        }
        for (int i = 0; i < 7; i++) {
            if (data.listensByDayOfWeek[i] > maxDay) {
                maxDay = data.listensByDayOfWeek[i];
                data.peakDay = i;
            }
        }

        // Longest run of consecutive listening days.
        long previousDay = Long.MIN_VALUE;
        int currentRun = 0;
        try (Cursor c = db.distinctSessionDays()) {
            while (c.moveToNext()) {
                long day = c.getLong(0);
                if (previousDay != Long.MIN_VALUE && day == previousDay + 1) {
                    currentRun++;
                } else {
                    currentRun = 1;
                }
                data.longestStreak = Math.max(data.longestStreak, currentRun);
                previousDay = day;
            }
        }

        if (!data.topArtists.isEmpty()) {
            Artist top = data.topArtists.get(0);
            data.topArtistShare = data.totalMs > 0
                    ? (float) top.listenedMs / data.totalMs : 0f;
            try (Cursor c = db.bestTrackForArtist(top.name)) {
                if (c.moveToFirst()) data.topArtistTrack = c.getString(0);
            }
        }

        if (data.plays > 0) data.avgSessionMs = data.totalMs / data.plays;
        if (data.activeDays > 0) data.perDayMs = data.totalMs / data.activeDays;
        if (!data.topTracks.isEmpty()) data.topTrackPlays = data.topTracks.get(0).plays;
        for (Track track : data.topTracks) data.topListensPlays += track.plays;

        // Month by month: who led each month, and how long the top artist led.
        String previousMonth = null;
        String monthArtist = null;
        try (Cursor c = db.topArtistPerMonth()) {
            while (c.moveToNext()) {
                String month = c.getString(0);
                String artist = c.getString(1);
                if (month == null || artist == null) continue;
                if (!month.equals(previousMonth)) {
                    if (monthArtist != null) {
                        data.monthlyArtists.add(previousMonth);
                        data.monthlyArtistNames.add(monthArtist);
                    }
                    previousMonth = month;
                    monthArtist = artist;
                }
            }
            if (monthArtist != null && previousMonth != null) {
                data.monthlyArtists.add(previousMonth);
                data.monthlyArtistNames.add(monthArtist);
            }
        }

        if (!data.topArtists.isEmpty()) {
            String top = data.topArtists.get(0).name;
            for (String artist : data.monthlyArtistNames) {
                if (artist.equals(top)) data.topArtistMonths++;
            }
        }
        data.newArtistsThisYear = db.distinctArtistsFirstSeenSince(
                System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000);

        for (String artist : db.newArtistsSince(
                System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000, 8)) {
            data.newArtists.add(new Artist(artist, 0, db.artistArtwork(artist)));
        }
        data.comebacks.addAll(db.comebackArtists());

        // Listening personality, derived from the hour the user listens most.
        int hour = data.peakHour;
        if (hour >= 5 && hour < 9) {
            data.archetype = "Early Bird";
            data.archetypeLine = "You were already listening when the day began.";
        } else if (hour < 12) {
            data.archetype = "Morning Person";
            data.archetypeLine = "Your soundtrack starts before the noise does.";
        } else if (hour < 17) {
            data.archetype = "Midday Listener";
            data.archetypeLine = "The middle of the day is where you switch on.";
        } else if (hour < 22) {
            data.archetype = "Golden Hour";
            data.archetypeLine = "The day winds down, your music winds up.";
        } else {
            data.archetype = "Night Owl";
            data.archetypeLine = "Your best listening happens after dark.";
        }

        return data;
    }

    /** Compact duration: "22h", "9m", "45s". */
    public static String compact(long ms) {
        if (ms >= 3_600_000) {
            long h = ms / 3_600_000;
            long m = (ms % 3_600_000) / 60_000;
            return m > 0 ? h + "h " + m + "m" : h + "h";
        }
        if (ms >= 60_000) return (ms / 60_000) + "m";
        return (ms / 1000) + "s";
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
