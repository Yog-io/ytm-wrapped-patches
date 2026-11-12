package app.revanced.extension.music.stats;

import app.revanced.extension.music.settings.Settings;
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
    private static String currentTitle;
    private static String currentArtist;
    private static long currentTrackId = -1;
    private static long currentSessionId = -1;
    private static long trackDurationMs;
    private static int lastState = STATE_STOPPED;
    private static long lastTickWallMs;      // wall clock of last activity
    private static long lastPositionMs;      // player position at last tick
    private static long accumulatedListenedMs; // listened ms committed so far

    private SessionTracker() {}

    /**
     * Injection point: {@code ie.m(MediaMetadataCompat)} — fires on track/metadata change.
     * smali extracts TITLE, ARTIST, DURATION from the metadata Bundle.
     */
    public static void onTrackInfo(CharSequence title, CharSequence artist, long durationMs) {
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
                }
                if (trackChanged) {
                    endCurrentSessionLocked();
                    long trackId = ListeningDatabase.getInstance().ensureTrack(t, a, durationMs);
                    currentTitle = t;
                    currentArtist = a;
                    currentTrackId = trackId;
                    trackDurationMs = durationMs;
                    // Open a session optimistically; it becomes "active" once state=playing.
                    currentSessionId = ListeningDatabase.getInstance()
                            .startSession(trackId, System.currentTimeMillis());
                    lastState = STATE_STOPPED;
                    lastTickWallMs = System.currentTimeMillis();
                    lastPositionMs = 0;
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

                boolean wasPlaying = isPlaying(lastState);
                boolean nowPlaying = isPlaying(state);

                if (wasPlaying && nowPlaying) {
                    // Still playing: commit elapsed wall time since last commit.
                    long elapsed = now - lastTickWallMs;
                    if (elapsed > 0 && elapsed < 60_000) { // guard against clock jumps
                        accumulatedListenedMs += elapsed;
                        ListeningDatabase.getInstance()
                                .accumulateSession(currentSessionId, elapsed);
                    }
                    scheduleFlushLocked();
                } else if (wasPlaying && !nowPlaying) {
                    // Leaving playback (pause/stop): commit final slice.
                    long elapsed = now - lastTickWallMs;
                    if (elapsed > 0 && elapsed < 60_000) {
                        accumulatedListenedMs += elapsed;
                        ListeningDatabase.getInstance()
                                .accumulateSession(currentSessionId, elapsed);
                    }
                    cancelFlushLocked();
                    if (state == STATE_STOPPED) {
                        endCurrentSessionLocked();
                    }
                } else if (!wasPlaying && nowPlaying) {
                    // Resuming playback: re-anchor the clock and start flushing.
                    scheduleFlushLocked();
                }

                lastState = state;
                lastTickWallMs = now;
                lastPositionMs = positionMs;
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
                if (currentSessionId < 0 || !isPlaying(lastState)) return; // stale timer
                long now = System.currentTimeMillis();
                long elapsed = now - lastTickWallMs;
                if (elapsed > 0 && elapsed < 60_000) {
                    accumulatedListenedMs += elapsed;
                    ListeningDatabase.getInstance()
                            .accumulateSession(currentSessionId, elapsed);
                }
                lastTickWallMs = now;
                scheduleFlushLocked();
            }
        });
    }

    /** Close the open session and roll it up into the track. Caller holds lock. */
    private static void endCurrentSessionLocked() {
        cancelFlushLocked();
        if (isPlaying(lastState) && currentSessionId >= 0) {
            // Playback was still running (e.g. track changed mid-song): commit
            // the pending wall time since the last flush before closing.
            long now = System.currentTimeMillis();
            long elapsed = now - lastTickWallMs;
            if (elapsed > 0 && elapsed < 60_000) {
                accumulatedListenedMs += elapsed;
                lastTickWallMs = now;
            }
        }
        if (currentSessionId >= 0 && currentTrackId >= 0) {
            ListeningDatabase.getInstance().endSession(currentSessionId, currentTrackId,
                    System.currentTimeMillis(), accumulatedListenedMs);
        }
        currentSessionId = -1;
        currentTrackId = -1;
        accumulatedListenedMs = 0;
        lastState = STATE_STOPPED;
    }
}
