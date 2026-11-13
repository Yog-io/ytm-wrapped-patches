package app.revanced.patches.music.stats

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.extensions.instructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.music.misc.extension.sharedExtensionPatch
import app.revanced.patches.music.misc.settings.PreferenceScreen
import app.revanced.patches.music.misc.settings.settingsPatch
import app.revanced.patches.shared.misc.settings.preference.SwitchPreference
import app.revanced.patches.shared.misc.settings.preference.TextPreference
import app.revanced.util.asSequence
import app.revanced.util.getNode
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/music/stats/SessionTracker;"

private const val METADATA_BUNDLE_TYPE = "Landroid/os/Bundle;"

private const val WRAPPED_ACTIVITY_CLASS =
    "app.revanced.extension.music.stats.WrappedReportActivity"

/**
 * Registers the full-screen Wrapped report activity in the manifest.
 */
private val wrappedReportActivityPatch = resourcePatch(
    description = "Registers the Music Wrapped report screen.",
) {
    apply {
        document("AndroidManifest.xml").use { document ->
            val alreadyRegistered = document.getElementsByTagName("activity").asSequence()
                .any { it.attributes.getNamedItem("android:name")?.nodeValue == WRAPPED_ACTIVITY_CLASS }
            if (alreadyRegistered) return@apply

            with(document.getNode("application")) {
                document.createElement("activity").apply {
                    setAttribute("android:name", WRAPPED_ACTIVITY_CLASS)
                    setAttribute("android:exported", "false")
                    setAttribute("android:theme", "@android:style/Theme.Material")
                    setAttribute(
                        "android:configChanges",
                        "orientation|screenSize|screenLayout|keyboardHidden",
                    )
                }.let(this::appendChild)
            }
        }
    }
}

@Suppress("unused")
val listeningLoggerPatch = bytecodePatch(
    name = "Listening stats logger",
    description = "Records listening history (track, artist, duration, timestamps) to a local database for the Wrapped report.",
) {
    dependsOn(sharedExtensionPatch, settingsPatch, addResourcesPatch, wrappedReportActivityPatch)

    compatibleWith(
        "com.google.android.apps.youtube.music"(
            "7.29.52",
            "8.10.52",
            "8.37.56",
            "8.40.54",
        ),
    )

    apply {
        addResources("music", "stats.listeningLoggerPatch")

        PreferenceScreen.MISC.addPreferences(
            SwitchPreference("revanced_music_listening_stats"),
            TextPreference(
                key = null,
                titleKey = "revanced_music_wrapped_title",
                summaryKey = "revanced_music_wrapped_summary",
                tag = "app.revanced.extension.music.settings.preference.WrappedReportPreference",
            ),
        )

        // --- Hook setMetadata: track identity (TITLE/ARTIST/DURATION) ---
        mediaSessionSetMetadataMethodMatch.method.apply {
            // The support library obfuscates the Bundle field on MediaMetadataCompat.
            // Resolve it from this method's own iget (the only Bundle-typed field access).
            val bundleField = instructions
                .filterIsInstance<ReferenceInstruction>()
                .map { it.reference }
                .filterIsInstance<FieldReference>()
                .first { it.type == METADATA_BUNDLE_TYPE }

            addInstructions(
                0,
                """
                    if-eqz p1, :rv_stats_meta_skip
                    iget-object v0, p1, ${bundleField.definingClass}->${bundleField.name}:${bundleField.type}
                    if-eqz v0, :rv_stats_meta_skip
                    const-string v1, "android.media.metadata.TITLE"
                    invoke-virtual { v0, v1 }, Landroid/os/Bundle;->getCharSequence(Ljava/lang/String;)Ljava/lang/CharSequence;
                    move-result-object v1
                    const-string v2, "android.media.metadata.ARTIST"
                    invoke-virtual { v0, v2 }, Landroid/os/Bundle;->getCharSequence(Ljava/lang/String;)Ljava/lang/CharSequence;
                    move-result-object v2
                    const-string v3, "android.media.metadata.DURATION"
                    invoke-virtual { v0, v3 }, Landroid/os/Bundle;->getLong(Ljava/lang/String;)J
                    move-result-wide v3
                    invoke-static { v1, v2, v3, v4 }, $EXTENSION_CLASS_DESCRIPTOR->onTrackInfo(Ljava/lang/CharSequence;Ljava/lang/CharSequence;J)V
                    :rv_stats_meta_skip
                """,
            )
        }

        // --- Hook setPlaybackState: position + state, fires every ~1s while playing ---
        mediaSessionSetPlaybackStateMethodMatch.method.apply {
            // Locate PlaybackState$Builder.setState(IJFJ) and walk back to the iget /
            // iget-wide feeding its state (I) and position (J) operands, so the
            // obfuscated PlaybackStateCompat field names are resolved at patch time.
            val setStateIndex = indexOfFirstInstructionOrThrow {
                (opcode == Opcode.INVOKE_VIRTUAL || opcode == Opcode.INVOKE_VIRTUAL_RANGE) &&
                        getReference<MethodReference>()?.let { ref ->
                            ref.name == "setState" &&
                                    ref.parameterTypes == listOf("I", "J", "F", "J")
                        } == true
            }

            val setStateRange = getInstruction<RegisterRangeInstruction>(setStateIndex)
            val stateReg = setStateRange.startRegister + 1
            val positionReg = setStateRange.startRegister + 2

            var stateField: FieldReference? = null
            var positionField: FieldReference? = null
            for (i in setStateIndex - 1 downTo 0) {
                val instr = instructions[i]
                if (stateField == null && instr.opcode == Opcode.IGET &&
                    (instr as TwoRegisterInstruction).registerA == stateReg
                ) {
                    stateField = (instr as ReferenceInstruction).reference as FieldReference
                }
                if (positionField == null && instr.opcode == Opcode.IGET_WIDE &&
                    (instr as TwoRegisterInstruction).registerA == positionReg
                ) {
                    positionField = (instr as ReferenceInstruction).reference as FieldReference
                }
                if (stateField != null && positionField != null) break
            }

            val state = stateField ?: throw PatchException("Could not resolve state field")
            val position = positionField ?: throw PatchException("Could not resolve position field")

            addInstructions(
                0,
                """
                    if-eqz p1, :rv_stats_state_skip
                    iget v0, p1, ${state.definingClass}->${state.name}:${state.type}
                    iget-wide v1, p1, ${position.definingClass}->${position.name}:${position.type}
                    invoke-static { v1, v2, v0 }, $EXTENSION_CLASS_DESCRIPTOR->onPlaybackState(JI)V
                    :rv_stats_state_skip
                """,
            )
        }
    }
}
