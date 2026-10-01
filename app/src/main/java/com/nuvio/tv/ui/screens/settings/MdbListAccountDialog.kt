package com.nuvio.tv.ui.screens.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.mdblist.MdbListLibraryListOption
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioMotion
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

@Composable
internal fun MdbListAccountDialog(
    state: MdbListTrackerUiState,
    libraryLists: MdbListLibraryListsUiState,
    onStartConnection: () -> Unit,
    onRetryPolling: () -> Unit,
    onSync: () -> Unit,
    onOpenLibraryLists: () -> Unit,
    onToggleLibraryList: (MdbListLibraryListOption) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val logo = rememberRawSvgPainter(R.raw.mdblist_logo, 150.dp)
    var showLibraryLists by rememberSaveable { mutableStateOf(false) }
    if (state.isConnected) {
        ConnectedTrackingAccountDialog(
            brand = TrackingDialogBrand.MDBLIST,
            glyph = logo,
            onDismiss = { if (showLibraryLists) showLibraryLists = false else onDismiss() }
        ) {
            Crossfade(
                targetState = showLibraryLists,
                animationSpec = tween(NuvioMotion.tokens.durations.fast),
                label = "MdbListLibraryLists"
            ) { listsVisible ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (listsVisible) {
                        MdbListLibraryListsContent(
                            logo = logo,
                            state = libraryLists,
                            onToggle = onToggleLibraryList,
                            onBack = { showLibraryLists = false }
                        )
                    } else {
                        ConnectedTrackingAccountContent(
                            brand = TrackingDialogBrand.MDBLIST,
                            logo = logo,
                            logoContentDescription = stringResource(R.string.mdblist_name),
                            connectedLabel = stringResource(R.string.mdblist_connected_as, state.username ?: stringResource(R.string.mdblist_account_fallback)),
                            connectedDescription = stringResource(R.string.mdblist_tracking_description),
                            statusMessage = state.statusMessage,
                            errorMessage = state.errorMessage,
                            isLoading = state.isLoading || state.isSyncing,
                            onDisconnect = onDisconnect,
                            onSync = onSync,
                            onInfo = {
                                onOpenLibraryLists()
                                showLibraryLists = true
                            },
                            infoLabel = stringResource(R.string.mdblist_library_lists)
                        )
                    }
                }
            }
        }
    } else {
        NuvioDialog(onDismiss = onDismiss, title = "", width = 720.dp, titleTextAlign = TextAlign.Center, suppressFirstKeyUp = false) {
            TrackingDeviceAuthContent(
                providerName = stringResource(R.string.mdblist_name),
                logo = logo,
                logoContentDescription = stringResource(R.string.mdblist_name),
                logoLabel = stringResource(R.string.mdblist_name),
                qrContentDescription = stringResource(R.string.mdblist_qr_description),
                instruction = stringResource(R.string.mdblist_awaiting_instruction),
                userCode = state.session?.userCode,
                displayUrl = state.session?.verificationUri,
                qrUrl = state.session?.verificationUriComplete,
                expiresAtEpochMs = state.session?.expiresAtEpochMs,
                isLoading = state.isLoading,
                isPolling = state.isPolling,
                credentialsConfigured = state.credentialsConfigured,
                statusMessage = state.statusMessage,
                errorMessage = state.errorMessage,
                missingCredentialsMessage = stringResource(R.string.mdblist_missing_client),
                onStartConnection = onStartConnection,
                onRetryPolling = onRetryPolling,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun ColumnScope.MdbListLibraryListsContent(
    logo: Painter,
    state: MdbListLibraryListsUiState,
    onToggle: (MdbListLibraryListOption) -> Unit,
    onBack: () -> Unit
) {
    val firstListFocusRequester = remember { FocusRequester() }
    val backFocusRequester = remember { FocusRequester() }
    val hasLists = state.lists.isNotEmpty()
    LaunchedEffect(hasLists) {
        delay(80L)
        runCatching { (if (hasLists) firstListFocusRequester else backFocusRequester).requestFocus() }
    }

    TrackingConnectedWordmark(
        brand = TrackingDialogBrand.MDBLIST,
        logo = logo,
        contentDescription = stringResource(R.string.mdblist_name)
    )
    Text(
        text = stringResource(R.string.mdblist_library_lists),
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = stringResource(R.string.mdblist_library_lists_description),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.78f)
    )
    when {
        hasLists -> LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(state.lists, key = { _, option -> option.key }) { index, option ->
                MdbListLibraryListRow(
                    option = option,
                    pending = state.pendingKey == option.key,
                    onClick = { onToggle(option) },
                    modifier = if (index == 0) Modifier.focusRequester(firstListFocusRequester) else Modifier
                )
            }
        }
        state.isLoading -> LoadingIndicator(
            modifier = Modifier
                .size(28.dp)
                .align(Alignment.CenterHorizontally),
            color = Color.White
        )
        else -> TrackingBrandMessage(
            text = stringResource(R.string.mdblist_library_lists_empty),
            isError = false
        )
    }
    state.errorMessage?.let { TrackingBrandMessage(text = it, isError = true) }
    TrackingBrandFooterButton(
        text = stringResource(R.string.action_back),
        onClick = onBack,
        modifier = Modifier.focusRequester(backFocusRequester)
    )
}

@Composable
private fun MdbListLibraryListRow(
    option: MdbListLibraryListOption,
    pending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            contentColor = Color.White.copy(alpha = 0.84f),
            focusedContentColor = Color.White
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.01f),
        shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pending) {
                LoadingIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            } else {
                Icon(
                    imageVector = if (option.visible) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = option.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
