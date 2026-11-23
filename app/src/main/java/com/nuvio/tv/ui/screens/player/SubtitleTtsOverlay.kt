@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.tts.GoogleTtsAvailability
import com.nuvio.tv.core.tts.GoogleTtsVoiceInfo
import com.nuvio.tv.core.tts.SubtitleTtsConfig
import com.nuvio.tv.core.tts.SubtitleTtsTimingCalculator
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun SubtitleTtsOverlay(
    visible: Boolean,
    config: SubtitleTtsConfig,
    availability: GoogleTtsAvailability,
    voices: List<GoogleTtsVoiceInfo>,
    onEnabledChange: (Boolean) -> Unit,
    onVoiceSelected: (String?) -> Unit,
    onAllowNetworkVoiceFallbackChange: (Boolean) -> Unit,
    onMovieVolumeChange: (Float) -> Unit,
    onVoiceVolumeChange: (Float) -> Unit,
    onBaseSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onAutoSpeedEnabledChange: (Boolean) -> Unit,
    onMaxAutoSpeedChange: (Float) -> Unit,
    onWaitForCueCompletionChange: (Boolean) -> Unit,
    onTransliterateEnglishNamesChange: (Boolean) -> Unit,
    onHideSubtitlesChange: (Boolean) -> Unit,
    onPreviewVoice: () -> Unit,
    onOpenVoiceInstaller: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enableToggleFocusRequester = remember { FocusRequester() }
    val voicesListState = rememberLazyListState()
    val controlsListState = rememberLazyListState()

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        val selectedVoiceIndex = if (config.voiceName == null) {
            0
        } else {
            val idx = voices.indexOfFirst { it.name == config.voiceName }
            if (idx >= 0) idx + 1 else 0
        }
        voicesListState.scrollToItem(selectedVoiceIndex)
        controlsListState.scrollToItem(0)
        delay(120)
        runCatching { enableToggleFocusRequester.requestFocus() }
    }

    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        captureKeys = false,
        contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 28.dp, bottom = 64.dp)
    ) {
        Column(
            modifier = Modifier
                .width(780.dp)
                .align(Alignment.BottomStart)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = stringResource(R.string.player_tts_dialog_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = NuvioTheme.spacing.sm)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(bottom = NuvioTheme.spacing.sm)
            ) {
                // Column 1: Voices list (Left, 412.dp)
                Column(modifier = Modifier.width(412.dp)) {
                    Text(
                        text = stringResource(R.string.tts_voice_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = NuvioTheme.spacing.xs)
                    )

                    if (availability == GoogleTtsAvailability.ENGINE_MISSING) {
                        Card(
                            onClick = onOpenVoiceInstaller,
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.colors(
                                containerColor = Color(0x28FFFFFF),
                                focusedContainerColor = Color(0x38FFFFFF)
                            ),
                            shape = CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                            border = CardDefaults.border(
                                border = Border(
                                    border = BorderStroke(NuvioTheme.spacing.xxs, Color.White.copy(alpha = 0.18f)),
                                    shape = RoundedCornerShape(NuvioTheme.radii.md)
                                ),
                                focusedBorder = Border(
                                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                                    shape = RoundedCornerShape(NuvioTheme.radii.md)
                                )
                            ),
                            scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
                        ) {
                            Column(modifier = Modifier.padding(NuvioTheme.spacing.md)) {
                                Text(
                                    text = stringResource(R.string.player_tts_not_installed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = stringResource(R.string.player_tts_install_button),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NuvioTheme.colors.Secondary,
                                    modifier = Modifier.padding(top = NuvioTheme.spacing.xs)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = voicesListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                            contentPadding = PaddingValues(vertical = NuvioTheme.spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item(key = "system_default") {
                                val isSelected = config.voiceName == null
                                VoiceOptionCard(
                                    title = stringResource(R.string.tts_voice_default),
                                    subtitle = null,
                                    isSelected = isSelected,
                                    onClick = { onVoiceSelected(null) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            items(items = voices, key = { it.name }) { voice ->
                                val isSelected = config.voiceName == voice.name
                                val tag = if (voice.isOffline) {
                                    stringResource(R.string.tts_voice_offline_tag)
                                } else {
                                    stringResource(R.string.tts_voice_online_tag)
                                }
                                VoiceOptionCard(
                                    title = voice.displayName,
                                    subtitle = tag,
                                    isSelected = isSelected,
                                    onClick = { onVoiceSelected(voice.name) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Column 2: Controls list (Right, 344.dp)
                Column(modifier = Modifier.width(344.dp)) {
                    Text(
                        text = stringResource(R.string.playback_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = NuvioTheme.spacing.xs)
                    )

                    val onText = stringResource(R.string.sentry_turn_on)
                    val offText = stringResource(R.string.playback_afr_off)

                    LazyColumn(
                        state = controlsListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        contentPadding = PaddingValues(vertical = NuvioTheme.spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item(key = "tts_enabled") {
                            TtsActionCard(
                                title = stringResource(R.string.tts_enable_title),
                                value = if (config.enabled) onText else offText,
                                isChecked = config.enabled,
                                onClick = { onEnabledChange(!config.enabled) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(enableToggleFocusRequester)
                            )
                        }

                        item(key = "tts_preview") {
                            TtsActionCard(
                                title = stringResource(R.string.tts_preview_button),
                                value = null,
                                trailingIcon = Icons.Default.PlayArrow,
                                onClick = onPreviewVoice,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item(key = "tts_movie_volume") {
                            val movieVolLabel = "${(config.movieVolume * 100).toInt()}%"
                            TtsActionCard(
                                title = stringResource(R.string.tts_movie_volume_title),
                                value = movieVolLabel,
                                onClick = {
                                    onMovieVolumeChange(
                                        SubtitleTtsTimingCalculator.nextSteppedOption(
                                            SubtitleTtsTimingCalculator.MOVIE_VOLUME_STEPS,
                                            config.movieVolume
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item(key = "tts_voice_volume") {
                            val voiceVolLabel = "${(config.voiceVolume * 100).toInt()}%"
                            TtsActionCard(
                                title = stringResource(R.string.tts_voice_volume_title),
                                value = voiceVolLabel,
                                onClick = {
                                    onVoiceVolumeChange(
                                        SubtitleTtsTimingCalculator.nextSteppedOption(
                                            SubtitleTtsTimingCalculator.VOICE_VOLUME_STEPS,
                                            config.voiceVolume
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item(key = "tts_base_speed") {
                            val speedLabel = String.format(Locale.US, "%.2fx", config.baseSpeed)
                            TtsActionCard(
                                title = stringResource(R.string.tts_base_speed_title),
                                value = speedLabel,
                                onClick = {
                                    onBaseSpeedChange(
                                        SubtitleTtsTimingCalculator.nextSteppedOption(
                                            SubtitleTtsTimingCalculator.BASE_SPEED_STEPS,
                                            config.baseSpeed
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item(key = "tts_pitch") {
                            val pitchLabel = String.format(Locale.US, "%.2f", config.pitch)
                            TtsActionCard(
                                title = stringResource(R.string.tts_pitch_title),
                                value = pitchLabel,
                                onClick = {
                                    onPitchChange(
                                        SubtitleTtsTimingCalculator.nextSteppedOption(
                                            SubtitleTtsTimingCalculator.PITCH_STEPS,
                                            config.pitch
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item(key = "tts_auto_speed") {
                            TtsActionCard(
                                title = stringResource(R.string.tts_auto_speed_title),
                                value = if (config.autoSpeedEnabled) onText else offText,
                                isChecked = config.autoSpeedEnabled,
                                onClick = { onAutoSpeedEnabledChange(!config.autoSpeedEnabled) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (config.autoSpeedEnabled) {
                            item(key = "tts_max_auto_speed") {
                                val maxSpeedLabel = String.format(Locale.US, "%.2fx", config.maxAutoSpeed)
                                TtsActionCard(
                                    title = stringResource(R.string.tts_max_auto_speed_title),
                                    value = maxSpeedLabel,
                                    onClick = {
                                        onMaxAutoSpeedChange(
                                            SubtitleTtsTimingCalculator.nextSteppedOption(
                                                SubtitleTtsTimingCalculator.MAX_AUTO_SPEED_STEPS,
                                                config.maxAutoSpeed
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        item(key = "tts_wait_for_cue") {
                            TtsActionCard(
                                title = stringResource(R.string.tts_wait_for_cue_title),
                                value = if (config.waitForCueCompletion) onText else offText,
                                isChecked = config.waitForCueCompletion,
                                onClick = { onWaitForCueCompletionChange(!config.waitForCueCompletion) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item(key = "tts_transliterate_names") {
                            TtsActionCard(
                                title = stringResource(R.string.tts_transliterate_names_title),
                                value = if (config.transliterateEnglishNames) onText else offText,
                                isChecked = config.transliterateEnglishNames,
                                onClick = { onTransliterateEnglishNamesChange(!config.transliterateEnglishNames) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item(key = "tts_hide_subtitles") {
                            TtsActionCard(
                                title = stringResource(R.string.tts_hide_subtitles_title),
                                value = if (config.hideSubtitlesWhenEnabled) onText else offText,
                                isChecked = config.hideSubtitlesWhenEnabled,
                                onClick = { onHideSubtitlesChange(!config.hideSubtitlesWhenEnabled) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item(key = "tts_network_fallback") {
                            TtsActionCard(
                                title = stringResource(R.string.tts_allow_network_fallback_title),
                                value = if (config.allowNetworkVoiceFallback) onText else offText,
                                isChecked = config.allowNetworkVoiceFallback,
                                onClick = { onAllowNetworkVoiceFallbackChange(!config.allowNetworkVoiceFallback) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item(key = "tts_install_voices") {
                            TtsActionCard(
                                title = stringResource(R.string.tts_install_voice_title),
                                value = null,
                                trailingIcon = Icons.Default.Download,
                                onClick = onOpenVoiceInstaller,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceOptionCard(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryTextColor = if (isSelected) NuvioTheme.colors.OnSecondary else Color.White
    val secondaryTextColor = if (isSelected) {
        NuvioTheme.colors.OnSecondary.copy(alpha = 0.82f)
    } else {
        Color.White.copy(alpha = 0.65f)
    }

    Card(
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.RadioButton
            selected = isSelected
        },
        colors = CardDefaults.colors(
            containerColor = if (isSelected) NuvioTheme.colors.Secondary else Color(0x22FFFFFF),
            focusedContainerColor = if (isSelected) NuvioTheme.colors.Secondary else Color(0x33FFFFFF)
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(NuvioTheme.spacing.xxs, Color.Transparent),
                shape = RoundedCornerShape(NuvioTheme.radii.md)
            ),
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = RoundedCornerShape(NuvioTheme.radii.md)
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NuvioTheme.spacing.md, vertical = NuvioTheme.spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryTextColor
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = NuvioTheme.colors.OnSecondary
                )
            }
        }
    }
}

@Composable
private fun TtsActionCard(
    title: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isChecked: Boolean? = null,
    trailingIcon: ImageVector? = null
) {
    val activeToggle = isChecked == true
    val primaryTextColor = if (activeToggle) NuvioTheme.colors.OnSecondary else Color.White
    val valueTextColor = if (activeToggle) NuvioTheme.colors.OnSecondary else Color.White.copy(alpha = 0.85f)

    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.colors(
            containerColor = if (activeToggle) NuvioTheme.colors.Secondary else Color(0x22FFFFFF),
            focusedContainerColor = if (activeToggle) NuvioTheme.colors.Secondary else Color(0x33FFFFFF)
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(NuvioTheme.spacing.xxs, Color.White.copy(alpha = 0.14f)),
                shape = RoundedCornerShape(NuvioTheme.radii.md)
            ),
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = RoundedCornerShape(NuvioTheme.radii.md)
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NuvioTheme.spacing.md, vertical = NuvioTheme.spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = primaryTextColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = NuvioTheme.spacing.sm)
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    color = valueTextColor
                )
            }
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = valueTextColor
                )
            }
        }
    }
}
