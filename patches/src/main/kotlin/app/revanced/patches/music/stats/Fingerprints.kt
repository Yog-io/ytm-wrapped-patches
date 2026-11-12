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
