package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.StartupTab

@Composable
internal fun StartupTabRow(
    selectedTab: StartupTab,
    onTabSelected: (StartupTab) -> Unit,
    modifier: Modifier = Modifier
) {
    var dialogOpen by remember { mutableStateOf(false) }

    val currentLabel = when (selectedTab) {
        StartupTab.HOME -> stringResource(R.string.nav_home)
        StartupTab.LIVE_TV -> stringResource(R.string.nav_livetv)
        StartupTab.DISCOVER -> stringResource(R.string.nav_discover)
        StartupTab.SEARCH -> stringResource(R.string.nav_search)
        StartupTab.LIBRARY -> stringResource(R.string.nav_library)
    }

    SettingsActionRow(
        title = stringResource(R.string.layout_startup_tab),
        subtitle = stringResource(R.string.layout_startup_tab_sub),
        value = currentLabel,
        onClick = { dialogOpen = true },
        modifier = modifier
    )

    if (dialogOpen) {
        StartupTabDialog(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                onTabSelected(tab)
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false }
        )
    }
}

@Composable
internal fun StartupTabDialog(
    selectedTab: StartupTab,
    onTabSelected: (StartupTab) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        SettingsPickerOption(
            StartupTab.HOME,
            stringResource(R.string.nav_home),
            stringResource(R.string.layout_startup_tab_home_desc)
        ),
        SettingsPickerOption(
            StartupTab.LIVE_TV,
            stringResource(R.string.nav_livetv),
            stringResource(R.string.layout_startup_tab_livetv_desc)
        ),
        SettingsPickerOption(
            StartupTab.DISCOVER,
            stringResource(R.string.nav_discover),
            stringResource(R.string.layout_startup_tab_discover_desc)
        ),
        SettingsPickerOption(
            StartupTab.SEARCH,
            stringResource(R.string.nav_search),
            stringResource(R.string.layout_startup_tab_search_desc)
        ),
        SettingsPickerOption(
            StartupTab.LIBRARY,
            stringResource(R.string.nav_library),
            stringResource(R.string.layout_startup_tab_library_desc)
        )
    )

    SettingsSingleChoiceDialog(
        title = stringResource(R.string.layout_startup_tab_dialog_title),
        options = options,
        selectedValue = selectedTab,
        onOptionSelected = onTabSelected,
        onDismiss = onDismiss,
        width = 460.dp,
        maxHeight = 420.dp
    )
}

