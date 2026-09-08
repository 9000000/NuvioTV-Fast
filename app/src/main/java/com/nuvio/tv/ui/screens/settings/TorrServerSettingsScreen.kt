@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun TorrServerSettingsScreen(
    onBackPress: () -> Unit,
    viewModel: TorrServerSettingsViewModel = hiltViewModel()
) {
    BackHandler { onBackPress() }

    SettingsStandaloneScaffold(
        title = stringResource(R.string.settings_torrserver_title),
        subtitle = stringResource(R.string.settings_torrserver_subtitle)
    ) {
        TorrServerSettingsContent(viewModel = viewModel)
    }
}

@Composable
fun TorrServerSettingsContent(
    viewModel: TorrServerSettingsViewModel = hiltViewModel(),
    initialFocusRequester: FocusRequester? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var showServerUrlDialog by remember { mutableStateOf(false) }
    var showSelectAddonDialog by remember { mutableStateOf(false) }
    var showAddonUrlDialog by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.settings_torrserver_title),
            subtitle = stringResource(R.string.settings_torrserver_subtitle)
        )

        SettingsGroupCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = NuvioTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Enable Toggle
                    item(key = "torrserver_enable") {
                        SettingsToggleRow(
                            title = stringResource(R.string.torrserver_enable_title),
                            subtitle = stringResource(R.string.torrserver_enable_subtitle),
                            checked = uiState.enabled,
                            onToggle = { viewModel.onEvent(TorrServerSettingsEvent.ToggleEnabled(!uiState.enabled)) },
                            modifier = Modifier
                                .padding(top = NuvioTheme.spacing.xxs)
                                .then(
                                    if (initialFocusRequester != null) {
                                        Modifier.focusRequester(initialFocusRequester)
                                    } else Modifier
                                )
                        )
                    }

                    // Server URL
                    item(key = "torrserver_server_url") {
                        SettingsActionRow(
                            title = stringResource(R.string.torrserver_server_url_title),
                            subtitle = uiState.serverUrl.ifBlank { stringResource(R.string.torrserver_server_url_hint) },
                            value = uiState.serverStatusMessage?.let { msg ->
                                if (uiState.serverStatusSuccess == true) "✅ $msg" else "❌ $msg"
                            },
                            valueColor = if (uiState.serverStatusSuccess == true) NuvioTheme.colors.Success else NuvioTheme.colors.Error,
                            onClick = { showServerUrlDialog = true }
                        )
                    }

                    // Test Server Connection Button
                    item(key = "torrserver_test_server_btn") {
                        SettingsActionRow(
                            title = stringResource(R.string.torrserver_test_connection),
                            subtitle = if (uiState.isTestingServer) "Đang kiểm tra..." else "Kiểm tra kết nối GET /echo tới máy chủ",
                            value = if (uiState.isTestingServer) "..." else null,
                            leadingIcon = Icons.Default.Refresh,
                            onClick = { viewModel.onEvent(TorrServerSettingsEvent.TestServerConnection) }
                        )
                    }

                    // Select from Installed Addons
                    item(key = "torrserver_select_addon") {
                        val selectedAddonName = uiState.installedAddons.firstOrNull { addon ->
                            val manifest = if (addon.baseUrl.endsWith("/manifest.json")) addon.baseUrl else "${addon.baseUrl}/manifest.json"
                            manifest.equals(uiState.addonUrl, ignoreCase = true) || addon.baseUrl.equals(uiState.addonUrl, ignoreCase = true)
                        }?.displayName

                        val currentDisplayValue = when {
                            uiState.addonUrl.isBlank() -> stringResource(R.string.torrserver_addon_url_hint)
                            selectedAddonName != null -> selectedAddonName
                            else -> uiState.addonUrl
                        }

                        SettingsActionRow(
                            title = stringResource(R.string.torrserver_select_installed_addon),
                            subtitle = stringResource(R.string.torrserver_select_installed_addon_subtitle),
                            value = currentDisplayValue,
                            onClick = { showSelectAddonDialog = true }
                        )
                    }

                    // Addon URL (Custom Scraper URL like Torrentio)
                    item(key = "torrserver_addon_url") {
                        SettingsActionRow(
                            title = stringResource(R.string.torrserver_addon_url_title),
                            subtitle = uiState.addonUrl.ifBlank { stringResource(R.string.torrserver_addon_url_hint) },
                            value = uiState.addonStatusMessage?.let { msg ->
                                if (uiState.addonStatusSuccess == true) "✅ $msg" else "❌ $msg"
                            },
                            valueColor = if (uiState.addonStatusSuccess == true) NuvioTheme.colors.Success else NuvioTheme.colors.Error,
                            onClick = { showAddonUrlDialog = true }
                        )
                    }

                    // Test Addon Button
                    item(key = "torrserver_test_addon_btn") {
                        SettingsActionRow(
                            title = stringResource(R.string.torrserver_test_addon),
                            subtitle = if (uiState.isTestingAddon) "Đang kiểm tra..." else "Kiểm tra manifest của Addon scraper",
                            value = if (uiState.isTestingAddon) "..." else null,
                            leadingIcon = Icons.Default.Refresh,
                            onClick = { viewModel.onEvent(TorrServerSettingsEvent.TestAddon) }
                        )
                    }

                    // Basic Auth
                    item(key = "torrserver_auth") {
                        val authSubtitle = if (uiState.authUsername.isNotBlank()) {
                            "Người dùng: ${uiState.authUsername}"
                        } else {
                            "Không sử dụng mật khẩu"
                        }
                        SettingsActionRow(
                            title = stringResource(R.string.torrserver_auth_title),
                            subtitle = authSubtitle,
                            onClick = { showAuthDialog = true }
                        )
                    }

                    // Preload Toggle
                    item(key = "torrserver_preload") {
                        SettingsToggleRow(
                            title = stringResource(R.string.torrserver_preload_title),
                            subtitle = stringResource(R.string.torrserver_preload_subtitle),
                            checked = uiState.preload,
                            onToggle = { viewModel.onEvent(TorrServerSettingsEvent.TogglePreload(!uiState.preload)) }
                        )
                    }

                    // Save to DB Toggle
                    item(key = "torrserver_save") {
                        SettingsToggleRow(
                            title = stringResource(R.string.torrserver_save_title),
                            subtitle = stringResource(R.string.torrserver_save_subtitle),
                            checked = uiState.saveToDb,
                            onToggle = { viewModel.onEvent(TorrServerSettingsEvent.ToggleSaveToDb(!uiState.saveToDb)) }
                        )
                    }

                    // GStreamer (GST) Toggle
                    item(key = "torrserver_gst") {
                        val gstSubtitle = when {
                            uiState.isCheckingGst -> "Đang kiểm tra hỗ trợ GStreamer trên máy chủ..."
                            uiState.gstStatusMessage != null -> {
                                val prefix = if (uiState.gstStatusSuccess == true) "✅ " else "⚠️ "
                                prefix + uiState.gstStatusMessage
                            }
                            else -> stringResource(R.string.torrserver_gst_subtitle)
                        }
                        SettingsToggleRow(
                            title = stringResource(R.string.torrserver_gst_title),
                            subtitle = gstSubtitle,
                            checked = uiState.gst,
                            onToggle = { viewModel.onEvent(TorrServerSettingsEvent.ToggleGst(!uiState.gst)) }
                        )
                    }
                }

                SettingsVerticalScrollIndicators(state = listState)
            }
        }
    }

    if (showServerUrlDialog) {
        TorrServerTextInputDialog(
            title = stringResource(R.string.torrserver_server_url_title),
            subtitle = "Nhập địa chỉ máy chủ TorrServer (vd: http://192.168.1.100:8090 hoặc http://127.0.0.1:8090)",
            initialValue = uiState.serverUrl,
            placeholder = "http://192.168.1.100:8090",
            onSave = { url ->
                viewModel.onEvent(TorrServerSettingsEvent.UpdateServerUrl(url))
                showServerUrlDialog = false
            },
            onDismiss = { showServerUrlDialog = false }
        )
    }

    if (showSelectAddonDialog) {
        val options = buildList {
            add(
                SettingsPickerOption(
                    value = "",
                    title = stringResource(R.string.torrserver_addon_url_hint),
                    description = stringResource(R.string.torrserver_auto_detect_addons_desc)
                )
            )
            uiState.installedAddons.forEach { addon ->
                val manifest = if (addon.baseUrl.endsWith("/manifest.json")) addon.baseUrl else "${addon.baseUrl}/manifest.json"
                add(
                    SettingsPickerOption(
                        value = manifest,
                        title = addon.displayName,
                        description = manifest
                    )
                )
            }
        }

        SettingsSingleChoiceDialog(
            title = stringResource(R.string.torrserver_select_installed_addon),
            options = options,
            selectedValue = uiState.addonUrl,
            onOptionSelected = { selectedUrl ->
                viewModel.onEvent(TorrServerSettingsEvent.SelectInstalledAddon(selectedUrl))
                showSelectAddonDialog = false
            },
            onDismiss = { showSelectAddonDialog = false },
            width = 620.dp
        )
    }

    if (showAddonUrlDialog) {
        TorrServerTextInputDialog(
            title = stringResource(R.string.torrserver_addon_url_title),
            subtitle = "Nhập URL manifest của addon tìm torrent (vd: Torrentio https://torrentio.strem.fun/manifest.json)",
            initialValue = uiState.addonUrl,
            placeholder = "https://torrentio.strem.fun/manifest.json",
            onSave = { url ->
                viewModel.onEvent(TorrServerSettingsEvent.UpdateAddonUrl(url))
                showAddonUrlDialog = false
            },
            onDismiss = { showAddonUrlDialog = false }
        )
    }

    if (showAuthDialog) {
        TorrServerAuthDialog(
            currentUsername = uiState.authUsername,
            currentPassword = uiState.authPassword,
            onSave = { user, pass ->
                viewModel.onEvent(TorrServerSettingsEvent.UpdateCredentials(user, pass))
                showAuthDialog = false
            },
            onDismiss = { showAuthDialog = false }
        )
    }
}

@Composable
private fun TorrServerTextInputDialog(
    title: String,
    subtitle: String,
    initialValue: String,
    placeholder: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = subtitle,
        width = 680.dp
    ) {
        Card(
            onClick = { focusRequester.requestFocus() },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                focusedContainerColor = NuvioTheme.colors.BackgroundElevated
            ),
            border = CardDefaults.border(
                border = Border(
                    border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                    shape = RoundedCornerShape(10.dp)
                ),
                focusedBorder = Border(
                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                    shape = RoundedCornerShape(10.dp)
                )
            ),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
            scale = CardDefaults.scale(focusedScale = 1f)
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        color = NuvioTheme.colors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    singleLine = true
                )
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = NuvioTheme.colors.TextMuted,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Text(stringResource(R.string.action_cancel))
            }

            Button(
                onClick = { onSave(text) },
                modifier = Modifier.focusRequester(saveFocusRequester),
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.Primary,
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun TorrServerAuthDialog(
    currentUsername: String,
    currentPassword: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var username by remember(currentUsername) { mutableStateOf(currentUsername) }
    var password by remember(currentPassword) { mutableStateOf(currentPassword) }
    val userFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        userFocusRequester.requestFocus()
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.torrserver_auth_title),
        subtitle = "Tài khoản Basic Auth trên máy chủ TorrServer (nếu có)",
        width = 600.dp
    ) {
        Text("Tên đăng nhập (Username)", style = MaterialTheme.typography.labelMedium, color = NuvioTheme.colors.TextSecondary)
        Card(
            onClick = { userFocusRequester.requestFocus() },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                focusedContainerColor = NuvioTheme.colors.BackgroundElevated
            ),
            shape = CardDefaults.shape(RoundedCornerShape(8.dp))
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                BasicTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(userFocusRequester),
                    textStyle = TextStyle(color = NuvioTheme.colors.TextPrimary, fontSize = 14.sp),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Mật khẩu (Password)", style = MaterialTheme.typography.labelMedium, color = NuvioTheme.colors.TextSecondary)
        Card(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                focusedContainerColor = NuvioTheme.colors.BackgroundElevated
            ),
            shape = CardDefaults.shape(RoundedCornerShape(8.dp))
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                BasicTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = NuvioTheme.colors.TextPrimary, fontSize = 14.sp),
                    singleLine = true
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Text(stringResource(R.string.action_cancel))
            }

            Button(
                onClick = { onSave(username, password) },
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.Primary,
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
