package app.revanced.extension.music.stats;

import java.util.List;

import app.revanced.extension.music.settings.Settings;
import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;

/**
 * Called from the injected hooks in {@code ie.m} (metadata) and {@code ie.n}
 * (playback state, ~1x/sec while playing). Maintains the current listen session
 * and persists it to {@link ListeningDatabase} on a background thread.
 *
 * Injection points pass primitives only (no support-lib types cross the boundary).
 */
@SuppressWarnings("unused")
public final class SessionTracker {
    // android.media.session.PlaybackState states we care about.
    private static final int STATE_STOPPED = 1;
    private static final int STATE_PAUSED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_BUFFERING = 6;

    private static final Object lock = new Object();

    // ReVanced note: Music only calls setPlaybackState on state transitions — there are
    // NO periodic ticks while steadily playing. While playing, a self-rescheduling
    // flush timer commits wall-clock listened time every FLUSH_INTERVAL_MS.
    private static final long FLUSH_INTERVAL_MS = 10_000;
    private static final android.os.Handler flushHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    // Current session (guarded by lock).
    // Artwork lookups waiting for playback to pause.
    private static long pendingTrackId = -1;
    private static String pendingTrackTitle;
    private static String pendingTrackArtist;
    private static String pendingArtist;

    private static String currentTitle;
    private static String currentArtist;
    private static long currentTrackId = -1;
    private static long currentSessionId = -1;
    private static long trackDurationMs;
    private static boolean playing;            // playback is currently running
    private static long lastCommitWallMs;     // wall clock of the last commit
    private static long lastPlayingWallMs;    // wall clock of the last PLAYING state
    private static int pausedStreak;          // consecutive PAUSED states
    private static long accumulatedListenedMs; // listened ms committed so far

    private SessionTracker() {}

    /**
     * Injection point: the play queue handed to the media session. Every queue item
     * carries the real cover bitmap, which is the most reliable artwork source.
     * Read reflectively so the extension needs no support-library classes.
     */
    public static void onQueue(java.util.List queue) {
        if (queue == null || queue.isEmpty()) return;
        Utils.runOnBackgroundThread(() -> {
            int count = 0;
            for (Object item : queue) {
                if (count++ > 60) break;
                try {
                    Object description = findDescription(item);
                    if (description == null) continue;
                    android.graphics.Bitmap art = findBitmap(description);
                    if (art == null) continue;
                    List<String> texts = findTexts(description);
                    saveQueueArtwork(art, texts);
                } catch (Exception ex) {
                    Logger.printInfo(() -> "Could not read queue artwork: " + ex.getMessage());
                }
            }
        });
    }

    /** The media description of a queue item, whatever the field is called. */
    private static Object findDescription(Object item) {
        try {
            for (java.lang.reflect.Field field : item.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(item);
                if (value == null) continue;
                String name = value.getClass().getName();
                if (name.contains("MediaDescription")) return value;
            }
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not read queue description: " + ex.getMessage());
        }
        return null;
    }

    /** The cover bitmap inside a media description. */
    private static android.graphics.Bitmap findBitmap(Object description) {
        try {
            for (java.lang.reflect.Field field : description.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (!android.graphics.Bitmap.class.isAssignableFrom(field.getType())) continue;
                android.graphics.Bitmap bitmap = (android.graphics.Bitmap) field.get(description);
                if (bitmap != null && !bitmap.isRecycled()) return bitmap;
            }
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not read queue bitmap: " + ex.getMessage());
        }
        return null;
    }

    /** All the text carried by a media description (title, subtitle, ...). */
    private static List<String> findTexts(Object description) {
        List<String> texts = new java.util.ArrayList<>();
        try {
            for (java.lang.reflect.Field field : description.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(description);
                if (value instanceof CharSequence) {
                    String text = value.toString().trim();
                    if (!text.isEmpty() && !texts.contains(text)) texts.add(text);
                }
            }
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not read queue text: " + ex.getMessage());
        }
        return texts;
    }

    /** Pairs the cover with the track we already know, then stores it. */
    private static void saveQueueArtwork(android.graphics.Bitmap art, List<String> texts) {
        ListeningDatabase database = ListeningDatabase.getInstance();
        for (String first : texts) {
            for (String second : texts) {
                if (first.equals(second)) continue;
                long id = database.trackIdByNames(first, second);
                if (id < 0) id = database.trackIdByNames(second, first);
                if (id < 0) continue;

                String path = ArtworkStore.save(art, first + "|" + second);
                if (path == null) return;
                database.updateArtworkByName(first, second, path);
                database.updateArtworkByName(second, first, path);
                database.setArtistArtwork(second, path);
                database.setArtistArtwork(first, path);
                return;
            }
        }
    }

    /**
     * Injection point: {@code ie.m(MediaMetadataCompat)} — fires on track/metadata change.
     * The whole metadata Bundle is handed over so the extension can also pick up the
     * artwork (thumbnail URL or video id) that YouTube Music puts alongside the title.
     */
    public static void onMetadata(android.os.Bundle metadata) {
        if (metadata == null) return;
        onTrackInfo(
                metadata.getCharSequence("android.media.metadata.TITLE"),
                metadata.getCharSequence("android.media.metadata.ARTIST"),
                metadata.getLong("android.media.metadata.DURATION"),
                extractArtwork(metadata));
    }

    /**
     * Finds a usable artwork URL inside the metadata bundle: either a direct image
     * URL, or a video id that can be turned into a YouTube thumbnail.
     */
    private static boolean probedArt;

    private static String extractArtwork(android.os.Bundle metadata) {
        String videoId = null;
        if (!probedArt) {
            probedArt = true;
            Object art = metadata.getParcelable("android.media.metadata.ALBUM_ART");
            Logger.printInfo(() -> "Art probe: albumArt=" + (art == null ? "null"
                    : art.getClass().getSimpleName() + " " + ((android.graphics.Bitmap) art).getWidth()
                    + "x" + ((android.graphics.Bitmap) art).getHeight())
                    + " keys=" + metadata.keySet().size());
        }
        try {
            for (String key : metadata.keySet()) {
                Object value = metadata.get(key);
                if (value instanceof CharSequence) {
                    String text = value.toString();
                    if (text.startsWith("http")
                            && (text.contains("googleusercontent.com")
                            || text.contains("ytimg.com")
                            || text.contains("/vi_webp/")
                            || text.contains("ggpht"))) {
                        return text;
                    }
                } else if (value instanceof String) {
                    String text = (String) value;
                    if (text.contains("googleusercontent.com") || text.contains("ytimg.com")) {
                        return text;
                    }
                }
                if (videoId == null) {
                    String lower = key.toLowerCase();
                    if (lower.equals("videoid") || lower.equals("video_id")) {
                        Object idValue = metadata.get(key);
                        if (idValue instanceof CharSequence && idValue.toString().length() >= 11) {
                            videoId = idValue.toString();
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Could not read metadata artwork", ex);
        }
        if (videoId != null) {
            return "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg";
        }
        return null;
    }

    /**
     * Injection point: the play queue handed to the media session. Every queue item
     * carries the real cover bitmap, which is the most reliable artwork source.
     * Read reflectively so the extension needs no support-library classes.
     */
    /**
     * Injection point: {@code ie.m(MediaMetadataCompat)} — fires on track/metadata change.
     */
    public static void onTrackInfo(CharSequence title, CharSequence artist, long durationMs) {
        onTrackInfo(title, artist, durationMs, null);
    }

    /** Same as {@link #onTrackInfo(CharSequence, CharSequence, long)} with artwork. */
    public static void onTrackInfo(CharSequence title, CharSequence artist, long durationMs,
                                   String artworkUrl) {
        if (!Settings.LISTENING_STATS.get()) return;

        final String t = title == null ? "" : title.toString().trim();
        final String a = artist == null ? "" : artist.toString().trim();
        if (t.isEmpty() && a.isEmpty()) return; // ignore empty/clear metadata

        Utils.runOnBackgroundThread(() -> {
            synchronized (lock) {
                boolean trackChanged = !t.equals(currentTitle) || currentArtist == null;
                if (!trackChanged && !a.equals(currentArtist)) {
                    // Music sends metadata twice: first without the artist, then
                    // with it. Enrich the existing track instead of forking a new
                    // one (which would split the session and duplicate the row).
                    if (!a.isEmpty()) {
                        currentArtist = a;
                        ListeningDatabase.getInstance().updateTrackArtist(currentTrackId, a);
                    }
                    if (artworkUrl != null && !artworkUrl.isEmpty()) {
                        ListeningDatabase.getInstance()
                                .updateTrackArtwork(currentTrackId, artworkUrl);
                    }
                }
                if (trackChanged) {
                    endCurrentSessionLocked();
                    long trackId = ListeningDatabase.getInstance()
                            .ensureTrack(t, a, durationMs, artworkUrl);
                    currentTitle = t;
                    currentArtist = a;
                    currentTrackId = trackId;
                    trackDurationMs = durationMs;
                    if (artworkUrl != null && ArtworkStore.isLocal(artworkUrl) && !a.isEmpty()) {
                        // The track cover doubles as the artist picture.
                        ListeningDatabase.getInstance().setArtistArtwork(a, artworkUrl);
                    }
                    // Anything still missing is looked up after playback stops, so
                    // no network work ever runs while the music is playing.
                    if ((artworkUrl == null || artworkUrl.isEmpty())
                            && !ListeningDatabase.getInstance().trackArtworkFetched(trackId)) {
                        pendingTrackId = trackId;
                        pendingTrackTitle = t;
                        pendingTrackArtist = a;
                    }
                    if (!a.isEmpty() && !ListeningDatabase.getInstance().artistArtworkFetched(a)) {
                        pendingArtist = a;
                    }
                    // Open a session optimistically; it becomes "active" once state=playing.
                    currentSessionId = ListeningDatabase.getInstance()
                            .startSession(trackId, System.currentTimeMillis());
                    playing = false;
                    pausedStreak = 0;
                    lastCommitWallMs = System.currentTimeMillis();
                    lastPlayingWallMs = 0;
                    accumulatedListenedMs = 0;
                } else if (durationMs > 0 && durationMs != trackDurationMs) {
                    trackDurationMs = durationMs;
                }
            }
        });
    }

    /**
     * Injection point: {@code ie.n(PlaybackStateCompat)} — fires ~1x/sec while playing
     * and on every state change. smali extracts position (long) and state (int).
     */
    public static void onPlaybackState(long positionMs, int state) {
        if (!Settings.LISTENING_STATS.get()) return;

        Utils.runOnBackgroundThread(() -> {
            synchronized (lock) {
                if (currentSessionId < 0) return; // no track yet
                long now = System.currentTimeMillis();

                if (isPlaying(state)) {
                    pausedStreak = 0;
                    lastPlayingWallMs = now;
                    if (!playing) {
                        // Resuming: start the clock from now.
                        playing = true;
                        lastCommitWallMs = now;
                    }
                    scheduleFlushLocked();
                } else if (state == STATE_STOPPED) {
                    commitLocked(now);
                    cancelFlushLocked();
                    playing = false;
                    pausedStreak = 0;
                    endCurrentSessionLocked();
                } else {
                    // PAUSED. Music also emits bursts of PAUSED while it keeps
                    // playing, so only a pause that sticks is treated as a pause.
                    if (playing && now - lastPlayingWallMs < 2000) {
                        pausedStreak = 0;
                        return;
                    }
                    pausedStreak++;
                    if (pausedStreak < 3) return;

                    commitLocked(now);
                    cancelFlushLocked();
                    playing = false;
                    pausedStreak = 0;
                }
            }
        });
    }

    /** Adds the time since the last commit to the session. Caller holds lock. */
    private static void commitLocked(long now) {
        long elapsed = now - lastCommitWallMs;
        if (elapsed > 0 && elapsed < 60_000) { // guard against clock jumps
            accumulatedListenedMs += elapsed;
            ListeningDatabase.getInstance().accumulateSession(currentSessionId, elapsed);
        }
        lastCommitWallMs = now;
    }

    /**
     * Looks up the artwork of the track and artist that were played last. Runs on
     * a background thread while nothing is playing.
     */
    private static void fetchPendingArtwork() {
        final long trackId = pendingTrackId;
        final String title = pendingTrackTitle;
        final String artist = pendingTrackArtist;
        final String artistOnly = pendingArtist;
        pendingTrackId = -1;
        pendingTrackTitle = null;
        pendingTrackArtist = null;
        pendingArtist = null;

        if (trackId < 0 && artistOnly == null) return;
        Utils.runOnBackgroundThread(() -> {
            ListeningDatabase database = ListeningDatabase.getInstance();
            if (trackId >= 0 && title != null) {
                ArtworkResolver.resolveTrack(artist, title, url ->
                        database.updateTrackArtwork(trackId, url));
            }
            if (artistOnly != null) {
                ArtworkResolver.resolveArtist(artistOnly, url ->
                        database.setArtistArtwork(artistOnly, url));
            }
        });
    }

    private static boolean isPlaying(int state) {
        return state == STATE_PLAYING || state == STATE_BUFFERING;
    }

    /** Schedule the next wall-time flush while playing. Caller holds lock. */
    private static void scheduleFlushLocked() {
        cancelFlushLocked();
        flushHandler.postDelayed(SessionTracker::flushRunnable, FLUSH_INTERVAL_MS);
    }

    private static void cancelFlushLocked() {
        flushHandler.removeCallbacksAndMessages(null);
    }

    /** Runs on main thread; hops to a background thread for the DB write. */
    private static void flushRunnable() {
        Utils.runOnBackgroundThread(() -> {
            synchronized (lock) {
                if (currentSessionId < 0 || !playing) return; // stale timer
                commitLocked(System.currentTimeMillis());
                scheduleFlushLocked();
            }
        });
    }

    /** Close the open session and roll it up into the track. Caller holds lock. */
    private static void endCurrentSessionLocked() {
        cancelFlushLocked();
        if (playing && currentSessionId >= 0) {
            // Playback was still running (e.g. track changed mid-song): commit
            // the pending wall time since the last commit before closing.
            commitLocked(System.currentTimeMillis());
        }
        playing = false;
        if (currentSessionId >= 0 && currentTrackId >= 0) {
            ListeningDatabase.getInstance().endSession(currentSessionId, currentTrackId,
                    System.currentTimeMillis(), accumulatedListenedMs);
        }
        currentSessionId = -1;
        currentTrackId = -1;
        accumulatedListenedMs = 0;
        playing = false;
    }
}
