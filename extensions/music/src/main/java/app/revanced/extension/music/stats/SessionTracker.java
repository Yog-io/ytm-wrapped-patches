package app.revanced.extension.music.stats;

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
        final String t = title == null ? "" : title.toString().trim();
        final String a = artist == null ? "" : artist.toString().trim();
        if (t.isEmpty() && a.isEmpty()) return; // ignore empty/clear metadata

        Utils.runOnBackgroundThread(() -> {
            synchronized (lock) {
                boolean trackChanged = !t.equals(currentTitle) || !a.equals(currentArtist);
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
        Utils.runOnBackgroundThread(() -> {
            synchronized (lock) {
                if (currentSessionId < 0) return; // no track yet
                long now = System.currentTimeMillis();

                boolean wasPlaying = isPlaying(lastState);
                boolean nowPlaying = isPlaying(state);

                if (wasPlaying && nowPlaying) {
                    // Still playing: commit elapsed wall time since last tick.
                    long elapsed = now - lastTickWallMs;
                    if (elapsed > 0 && elapsed < 60_000) { // guard against clock jumps
                        accumulatedListenedMs += elapsed;
                        ListeningDatabase.getInstance()
                                .accumulateSession(currentSessionId, elapsed);
                    }
                } else if (wasPlaying && !nowPlaying) {
                    // Leaving playback (pause/stop): commit final slice.
                    long elapsed = now - lastTickWallMs;
                    if (elapsed > 0 && elapsed < 60_000) {
                        accumulatedListenedMs += elapsed;
                        ListeningDatabase.getInstance()
                                .accumulateSession(currentSessionId, elapsed);
                    }
                    if (state == STATE_STOPPED) {
                        endCurrentSessionLocked();
                    }
                } else if (!wasPlaying && nowPlaying) {
                    // Resuming playback: just re-anchor the clock.
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

    /** Close the open session and roll it up into the track. Caller holds lock. */
    private static void endCurrentSessionLocked() {
        if (currentSessionId >= 0 && currentTrackId >= 0) {
            ListeningDatabase.getInstance().endSession(currentSessionId, currentTrackId,
                    System.currentTimeMillis(), accumulatedListenedMs);
        }
        currentSessionId = -1;
        currentTrackId = -1;
        accumulatedListenedMs = 0;
    }
}
