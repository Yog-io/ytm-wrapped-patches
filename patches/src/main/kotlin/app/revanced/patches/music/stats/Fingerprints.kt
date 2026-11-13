package app.revanced.patches.music.stats

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Locates the MediaSession impl methods (class `ie` in 8.40.54, obfuscated name
 * varies per version). Discriminated by the stable framework method they invoke:
 * `MediaSession.setMetadata` / `MediaSession.setPlaybackState` — framework method
 * names are never obfuscated. `im.j`/`im.k` (the wrappers) have the same parameter
 * types but delegate via obfuscated `m`/`n`, so they do NOT match.
 */
internal val BytecodePatchContext.mediaSessionSetMetadataMethodMatch by composingFirstMethod {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returnType("V")
    parameterTypes("Landroid/support/v4/media/MediaMetadataCompat;")
    instructions(method("setMetadata"))
}

internal val BytecodePatchContext.mediaSessionSetPlaybackStateMethodMatch by composingFirstMethod {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returnType("V")
    parameterTypes("Landroid/support/v4/media/session/PlaybackStateCompat;")
    instructions(method("setPlaybackState"))
}

/**
 * `ie.q(List)`: hands the play queue to the platform media session. Queue items
 * carry the real artwork bitmaps, the most reliable source of covers.
 */
internal val BytecodePatchContext.mediaSessionSetQueueMethodMatch by composingFirstMethod {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returnType("V")
    parameterTypes("Ljava/util/List;")
    instructions(method("setQueue"))
}
