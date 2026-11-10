package com.nuvio.tv.updater.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import com.nuvio.tv.updater.UpdateUiState

@Composable
fun UpdateBannerHost(
    state: UpdateUiState,
    actionFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
    onDismissBanner: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismissUnknownSources: () -> Unit,
    onOpenUnknownSources: () -> Unit,
    onFeedbackShown: () -> Unit,
    content: @Composable () -> Unit
) {
    content()
}
