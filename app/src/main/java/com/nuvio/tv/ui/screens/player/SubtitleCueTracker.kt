package com.nuvio.tv.ui.screens.player

import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.text.TextOutput
import com.nuvio.tv.core.tts.SubtitleCuePlaybackContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SubtitleCueState(
    val version: Long = 0L,
    val visible: Boolean = false,
    val text: String = "",
    val hasBitmap: Boolean = false,
    val timingContext: SubtitleCuePlaybackContext? = null
)

internal class SubtitleCueTracker {
    private val mutableState = MutableStateFlow(SubtitleCueState())
    val state: StateFlow<SubtitleCueState> = mutableState.asStateFlow()
    private val mutablePrescannedTexts = MutableStateFlow<List<String>>(emptyList())
    val prescannedTexts: StateFlow<List<String>> = mutablePrescannedTexts.asStateFlow()

    @Volatile
    private var lastKey: String? = null

    fun update(cues: List<Cue>) {
        updateWithTiming(cues, null)
    }

    fun updateWithTiming(cues: List<Cue>, timingContext: SubtitleCuePlaybackContext? = null) {
        val hasBitmap = cues.any { it.bitmap != null }
        val text = cues.mapNotNull { it.text?.toString()?.trim()?.takeIf(String::isNotBlank) }.joinToString("\n")
        val key = buildString {
            append(text)
            if (hasBitmap) {
                for (cue in cues) {
                    cue.bitmap?.let {
                        append('\u0001')
                        append(System.identityHashCode(it))
                    }
                }
            }
        }
        publish(
            key = key,
            visible = text.isNotBlank() || hasBitmap,
            text = text,
            hasBitmap = hasBitmap,
            timingContext = timingContext
        )
    }

    fun updateText(text: String?) {
        updateTextWithTiming(text, null)
    }

    fun updateTextWithTiming(text: String?, timingContext: SubtitleCuePlaybackContext? = null) {
        val normalized = text?.trim().orEmpty()
        publish(
            key = normalized,
            visible = normalized.isNotBlank(),
            text = normalized,
            hasBitmap = false,
            timingContext = timingContext
        )
    }

    fun setPrescannedTexts(texts: List<String>) {
        mutablePrescannedTexts.value = texts
    }

    @Synchronized
    fun reset() {
        lastKey = null
        val current = mutableState.value
        if (current.visible || current.text.isNotEmpty() || current.hasBitmap || current.timingContext != null) {
            mutableState.value = SubtitleCueState(version = current.version + 1)
        }
    }

    @Synchronized
    private fun publish(
        key: String,
        visible: Boolean,
        text: String,
        hasBitmap: Boolean,
        timingContext: SubtitleCuePlaybackContext? = null
    ) {
        if (key == lastKey) return
        lastKey = key
        mutableState.value = SubtitleCueState(
            version = mutableState.value.version + 1,
            visible = visible,
            text = text,
            hasBitmap = hasBitmap,
            timingContext = timingContext
        )
    }
}

internal class CueTrackingTextOutput(
    private val delegate: TextOutput,
    private val onCuesShown: (List<Cue>) -> Unit
) : TextOutput {
    override fun onCues(cueGroup: CueGroup) {
        delegate.onCues(cueGroup)
        onCuesShown(cueGroup.cues)
    }

    @Deprecated("Uses the deprecated Media3 callback for text outputs.")
    override fun onCues(cues: List<Cue>) {
        // Media3 TextRenderer invokes both deprecated onCues(List) and onCues(CueGroup) for every
        // cue update; forward to delegate for compatibility without firing onCuesShown twice.
        @Suppress("DEPRECATION")
        delegate.onCues(cues)
    }
}
