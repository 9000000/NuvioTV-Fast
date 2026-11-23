package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.R
import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.domain.model.AmbientDisplayMode
import com.nuvio.tv.domain.model.AmbientModeSettings

@Composable
internal fun AmbientModeSettingsCard(
    viewModel: AmbientModeSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showCatalogsDialog by remember { mutableStateOf(false) }
    var showDisplayModeDialog by remember { mutableStateOf(false) }
    val supportsTrailerMode = AppFeaturePolicy.inAppTrailerPlaybackEnabled
    val effectiveDisplayMode = if (supportsTrailerMode) settings.displayMode else AmbientDisplayMode.BACKDROP
    val selectedCatalogs = settings.catalogKeys.filter { key -> uiState.catalogs.any { it.key == key } }

    SettingsGroupCard(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.ambient_mode_title),
        subtitle = stringResource(R.string.ambient_mode_subtitle)
    ) {
        SettingsToggleRow(
            title = stringResource(R.string.ambient_mode_title),
            subtitle = stringResource(R.string.ambient_mode_enabled_subtitle),
            checked = settings.enabled,
            onToggle = { viewModel.setEnabled(!settings.enabled) }
        )
        if (settings.enabled) {
            if (supportsTrailerMode) {
                SettingsActionRow(
                    title = stringResource(R.string.ambient_mode_display_mode),
                    subtitle = stringResource(R.string.ambient_mode_display_mode_subtitle),
                    value = displayModeLabel(effectiveDisplayMode),
                    onClick = { showDisplayModeDialog = true }
                )
                if (effectiveDisplayMode == AmbientDisplayMode.TRAILER) {
                    SettingsToggleRow(
                        title = stringResource(R.string.ambient_mode_trailer_audio),
                        subtitle = stringResource(R.string.ambient_mode_trailer_audio_subtitle),
                        checked = settings.trailerAudioEnabled,
                        onToggle = { viewModel.setTrailerAudioEnabled(!settings.trailerAudioEnabled) }
                    )
                }
            }
            SettingsActionRow(
                title = stringResource(R.string.ambient_mode_timeout),
                subtitle = stringResource(R.string.ambient_mode_timeout_subtitle),
                value = timeoutLabel(settings.timeoutMinutes),
                onClick = { showTimeoutDialog = true }
            )
            SettingsActionRow(
                title = stringResource(R.string.ambient_mode_catalogs),
                subtitle = stringResource(R.string.ambient_mode_catalogs_subtitle),
                value = if (selectedCatalogs.isEmpty()) {
                    stringResource(R.string.ambient_mode_catalogs_default)
                } else {
                    stringResource(
                        R.string.ambient_mode_catalogs_count,
                        selectedCatalogs.size,
                        AmbientModeSettings.MAX_CATALOGS
                    )
                },
                enabled = uiState.catalogs.isNotEmpty(),
                onClick = { showCatalogsDialog = true }
            )
        }
    }

    if (showDisplayModeDialog && supportsTrailerMode) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.ambient_mode_display_mode),
            subtitle = stringResource(R.string.ambient_mode_display_mode_subtitle),
            options = listOf(
                SettingsPickerOption(
                    value = AmbientDisplayMode.BACKDROP,
                    title = stringResource(R.string.ambient_mode_display_mode_backdrop),
                    description = stringResource(R.string.ambient_mode_display_mode_backdrop_desc)
                ),
                SettingsPickerOption(
                    value = AmbientDisplayMode.TRAILER,
                    title = stringResource(R.string.ambient_mode_display_mode_trailer),
                    description = stringResource(R.string.ambient_mode_display_mode_trailer_desc)
                )
            ),
            selectedValue = effectiveDisplayMode,
            onOptionSelected = { mode ->
                viewModel.setDisplayMode(mode)
                showDisplayModeDialog = false
            },
            onDismiss = { showDisplayModeDialog = false },
            maxHeight = 320.dp
        )
    }

    if (showTimeoutDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.ambient_mode_timeout),
            subtitle = stringResource(R.string.ambient_mode_timeout_subtitle),
            options = AmbientModeSettings.TIMEOUT_OPTIONS_MINUTES.map { minutes ->
                SettingsPickerOption(minutes, timeoutLabel(minutes))
            },
            selectedValue = settings.timeoutMinutes,
            onOptionSelected = { minutes ->
                viewModel.setTimeoutMinutes(minutes)
                showTimeoutDialog = false
            },
            onDismiss = { showTimeoutDialog = false },
            maxHeight = 360.dp
        )
    }

    if (showCatalogsDialog) {
        SettingsMultiChoiceDialog(
            title = stringResource(R.string.ambient_mode_catalogs),
            subtitle = stringResource(R.string.ambient_mode_catalogs_dialog_subtitle),
            options = uiState.catalogs.map { catalog ->
                SettingsPickerOption(catalog.key, catalog.title, description = catalog.source)
            },
            selectedValues = selectedCatalogs,
            onValuesSelected = { keys ->
                viewModel.setCatalogKeys(keys)
                showCatalogsDialog = false
            },
            onDismiss = { showCatalogsDialog = false },
            width = 620.dp,
            maxHeight = 460.dp,
            maxSelections = AmbientModeSettings.MAX_CATALOGS
        )
    }
}

@Composable
private fun displayModeLabel(mode: AmbientDisplayMode): String = when (mode) {
    AmbientDisplayMode.BACKDROP -> stringResource(R.string.ambient_mode_display_mode_backdrop)
    AmbientDisplayMode.TRAILER -> stringResource(R.string.ambient_mode_display_mode_trailer)
}

@Composable
private fun timeoutLabel(minutes: Int): String =
    pluralStringResource(R.plurals.ambient_mode_timeout_minutes, minutes, minutes)
