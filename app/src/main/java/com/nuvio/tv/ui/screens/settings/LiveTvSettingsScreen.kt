package com.nuvio.tv.ui.screens.settings

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nuvio.tv.ui.screens.addon.QrCodeOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.features.livetv.LiveTvPlaylist
import com.nuvio.tv.features.livetv.LiveTvPlaylistType
import com.nuvio.tv.features.livetv.LiveTvRepository
import com.nuvio.tv.features.livetv.LiveTvStalkerSettings
import com.nuvio.tv.features.livetv.LiveTvXtreamSettings
import com.nuvio.tv.features.livetv.STALKER_PLAYLIST_ID
import com.nuvio.tv.features.livetv.XTREAM_PLAYLIST_ID
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun LiveTvSettingsScreen(
    onBackPress: () -> Unit
) {
    BackHandler { onBackPress() }

    SettingsStandaloneScaffold(
        title = stringResource(R.string.settings_livetv_title),
        subtitle = stringResource(R.string.settings_livetv_subtitle)
    ) {
        LiveTvSettingsContent()
    }
}

@Composable
fun LiveTvSettingsContent(
    initialFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by LiveTvRepository.uiState.collectAsState()
    val listState = rememberLazyListState()

    val m3uFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                    } ?: "local_playlist.m3u"

                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (!content.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            LiveTvRepository.addLocalPlaylist(
                                name = fileName.substringBeforeLast('.'),
                                fileName = fileName,
                                content = content
                            )
                            Toast.makeText(
                                context,
                                context.getString(R.string.livetv_file_import_success, fileName),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.livetv_file_import_empty),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.livetv_file_import_failed, e.message ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            LiveTvRepository.stopQrMode()
        }
    }

    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<LiveTvPlaylist?>(null) }
    var showXtreamDialog by remember { mutableStateOf(false) }
    var showStalkerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.settings_livetv_title),
            subtitle = stringResource(R.string.settings_livetv_subtitle)
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
                    contentPadding = PaddingValues(bottom = NuvioTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Manage from phone (QR Code setup)
                    item(key = "livetv_manage_from_phone") {
                        SettingsActionRow(
                            title = stringResource(R.string.livetv_manage_from_phone_title),
                            subtitle = stringResource(R.string.livetv_manage_from_phone_subtitle),
                            leadingIcon = Icons.Default.QrCode2,
                            onClick = { LiveTvRepository.startQrMode(context) },
                            modifier = Modifier
                                .padding(top = NuvioTheme.spacing.xxs)
                                .then(
                                    if (initialFocusRequester != null) {
                                        Modifier.focusRequester(initialFocusRequester)
                                    } else Modifier
                                )
                        )
                    }

                    // Navigation toggle
                    item(key = "livetv_nav_toggle") {
                        SettingsToggleRow(
                            title = stringResource(R.string.livetv_nav_show_title),
                            subtitle = stringResource(R.string.livetv_nav_show_desc),
                            checked = uiState.isNavigationEnabled,
                            onToggle = { LiveTvRepository.setNavigationEnabled(!uiState.isNavigationEnabled) }
                        )
                    }

                    // --- SECTION 1: PLAYLISTS ---
                    item(key = "section_playlists_header") {
                        Text(
                            text = stringResource(R.string.livetv_section_playlists),
                            style = TextStyle(
                                color = NuvioTheme.colors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 8.dp)
                        )
                    }

                    item(key = "btn_add_playlist") {
                        SettingsActionRow(
                            title = stringResource(R.string.livetv_add_playlist),
                            subtitle = stringResource(R.string.livetv_add_playlist_desc),
                            leadingIcon = Icons.Default.Add,
                            onClick = { showAddPlaylistDialog = true }
                        )
                    }

                    item(key = "btn_import_m3u_file") {
                        SettingsActionRow(
                            title = stringResource(R.string.livetv_import_file),
                            subtitle = stringResource(R.string.livetv_import_file_desc),
                            leadingIcon = Icons.Default.FolderOpen,
                            onClick = {
                                m3uFilePicker.launch(arrayOf("*/*"))
                            }
                        )
                    }

                    items(uiState.playlists, key = { "pl_${it.id}" }) { playlist ->
                        PlaylistRowItem(
                            playlist = playlist,
                            onToggleEnabled = { isEnabled ->
                                LiveTvRepository.setPlaylistEnabled(playlist.id, isEnabled)
                            },
                            onEdit = { editingPlaylist = playlist },
                            onDelete = { LiveTvRepository.removePlaylist(playlist.id) }
                        )
                    }

                    // --- SECTION 2: XTREAM CODES ---
                    item(key = "section_xtream_header") {
                        Text(
                            text = stringResource(R.string.livetv_section_xtream),
                            style = TextStyle(
                                color = NuvioTheme.colors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 8.dp)
                        )
                    }

                    item(key = "xtream_account_row") {
                        val isConfigured = uiState.xtreamSettings.isConfigured
                        val channelCount = uiState.channels.count { it.playlistId == XTREAM_PLAYLIST_ID }
                        SettingsActionRow(
                            title = if (isConfigured) {
                                "Xtream: ${uiState.xtreamSettings.username} @ ${uiState.xtreamSettings.serverUrl}"
                            } else {
                                stringResource(R.string.livetv_section_xtream)
                            },
                            subtitle = stringResource(R.string.livetv_xtream_desc),
                            value = if (isConfigured) {
                                if (channelCount > 0) "✅ Đã kết nối ($channelCount kênh)"
                                else "✅ " + stringResource(R.string.livetv_connected)
                            } else {
                                stringResource(R.string.livetv_not_connected)
                            },
                            valueColor = if (isConfigured) NuvioTheme.colors.Success else NuvioTheme.colors.TextMuted,
                            leadingIcon = Icons.Default.Router,
                            onClick = { showXtreamDialog = true }
                        )
                    }

                    // --- SECTION 3: STALKER PORTAL ---
                    item(key = "section_stalker_header") {
                        Text(
                            text = stringResource(R.string.livetv_section_stalker),
                            style = TextStyle(
                                color = NuvioTheme.colors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 8.dp)
                        )
                    }

                    item(key = "stalker_account_row") {
                        val isConfigured = uiState.stalkerSettings.isConfigured
                        val channelCount = uiState.channels.count { it.playlistId == STALKER_PLAYLIST_ID }
                        SettingsActionRow(
                            title = if (isConfigured) {
                                "Stalker: ${uiState.stalkerSettings.macAddress} @ ${uiState.stalkerSettings.portalUrl}"
                            } else {
                                stringResource(R.string.livetv_section_stalker)
                            },
                            subtitle = stringResource(R.string.livetv_stalker_desc),
                            value = if (isConfigured) {
                                if (channelCount > 0) "✅ Đã kết nối ($channelCount kênh)"
                                else "✅ " + stringResource(R.string.livetv_connected)
                            } else {
                                stringResource(R.string.livetv_not_connected)
                            },
                            valueColor = if (isConfigured) NuvioTheme.colors.Success else NuvioTheme.colors.TextMuted,
                            leadingIcon = Icons.Default.Tv,
                            onClick = { showStalkerDialog = true }
                        )
                    }
                }

                SettingsVerticalScrollIndicators(state = listState)
            }
        }
    }

    // Dialogs
    if (showAddPlaylistDialog) {
        LiveTvPlaylistDialog(
            title = stringResource(R.string.livetv_add_playlist),
            initialName = "",
            initialUrl = "",
            onSave = { name, url ->
                LiveTvRepository.addPlaylistUrl(name = name.ifBlank { null }, url = url)
                showAddPlaylistDialog = false
            },
            onDismiss = { showAddPlaylistDialog = false }
        )
    }

    editingPlaylist?.let { pl ->
        LiveTvPlaylistDialog(
            title = "Edit Playlist",
            initialName = pl.name,
            initialUrl = pl.source,
            onSave = { name, url ->
                LiveTvRepository.updatePlaylist(pl.id, name.ifBlank { "Playlist" }, url)
                editingPlaylist = null
            },
            onDismiss = { editingPlaylist = null }
        )
    }

    if (showXtreamDialog) {
        LiveTvXtreamDialog(
            settings = uiState.xtreamSettings,
            onConnect = { newSettings ->
                LiveTvRepository.testAndSaveXtreamSettings(newSettings)
            },
            onDisconnect = {
                LiveTvRepository.removeXtream()
                showXtreamDialog = false
            },
            onDismiss = { showXtreamDialog = false }
        )
    }

    if (showStalkerDialog) {
        LiveTvStalkerDialog(
            settings = uiState.stalkerSettings,
            onConnect = { newSettings ->
                LiveTvRepository.testAndSaveStalkerSettings(newSettings)
            },
            onDisconnect = {
                LiveTvRepository.removeStalker()
                showStalkerDialog = false
            },
            onDismiss = { showStalkerDialog = false }
        )
    }

    if (uiState.isQrModeActive) {
        Popup(properties = PopupProperties(focusable = true)) {
            QrCodeOverlay(
                qrBitmap = uiState.qrCodeBitmap,
                serverUrl = uiState.serverUrl,
                instruction = stringResource(R.string.livetv_qr_scan_instruction),
                onClose = { LiveTvRepository.stopQrMode() }
            )
        }
    }
}

@Composable
private fun PlaylistRowItem(
    playlist: LiveTvPlaylist,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val enabledStatus = if (playlist.isEnabled) " (Active)" else " (Disabled)"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            onClick = { onToggleEnabled(!playlist.isEnabled) },
            modifier = Modifier.weight(1f),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                focusedContainerColor = NuvioTheme.colors.FocusBackground
            ),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
            scale = CardDefaults.scale(focusedScale = 1.01f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name + enabledStatus,
                        style = TextStyle(
                            color = if (playlist.isEnabled) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextMuted,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )
                    Text(
                        text = playlist.source,
                        style = TextStyle(
                            color = NuvioTheme.colors.TextSecondary,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }
                Text(
                    text = if (playlist.isEnabled) "Enabled" else "Disabled",
                    color = if (playlist.isEnabled) NuvioTheme.colors.Success else NuvioTheme.colors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Edit button
        Card(
            onClick = onEdit,
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                focusedContainerColor = NuvioTheme.colors.FocusBackground
            ),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
            scale = CardDefaults.scale(focusedScale = 1.05f)
        ) {
            Box(modifier = Modifier.padding(10.dp)) {
                Text(text = "Edit", color = NuvioTheme.colors.Primary, fontSize = 13.sp)
            }
        }

        // Delete button
        Card(
            onClick = onDelete,
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                focusedContainerColor = NuvioTheme.colors.FocusBackground
            ),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
            scale = CardDefaults.scale(focusedScale = 1.05f)
        ) {
            Box(modifier = Modifier.padding(10.dp)) {
                Text(text = "Delete", color = NuvioTheme.colors.Error, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun LiveTvPlaylistDialog(
    title: String,
    initialName: String,
    initialUrl: String,
    onSave: (name: String, url: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = "Enter the playlist name and remote URL (e.g. .m3u or .m3u8 link)",
        width = 680.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Name field
            TVTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Playlist Name (Optional)",
                modifier = Modifier.focusRequester(focusRequester)
            )

            // URL field
            TVTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = "https://example.com/channels.m3u"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
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
                    onClick = {
                        if (url.trim().isNotBlank()) {
                            onSave(name.trim(), url.trim())
                        }
                    },
                    enabled = url.trim().isNotBlank(),
                    colors = ButtonDefaults.colors(
                        containerColor = NuvioTheme.colors.Primary,
                        contentColor = NuvioTheme.colors.OnPrimary
                    )
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun LiveTvXtreamDialog(
    settings: LiveTvXtreamSettings,
    onConnect: suspend (LiveTvXtreamSettings) -> Result<Int>,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    var server by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var user by remember(settings.username) { mutableStateOf(settings.username) }
    var pass by remember(settings.password) { mutableStateOf(settings.password) }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    NuvioDialog(
        onDismiss = { if (!isConnecting) onDismiss() },
        title = stringResource(R.string.livetv_section_xtream),
        subtitle = "Connect to Xtream Codes IPTV with your server credentials",
        width = 680.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = NuvioTheme.colors.Error.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = NuvioTheme.colors.Error,
                        fontSize = 13.sp
                    )
                }
            }

            TVTextField(
                value = server,
                onValueChange = { server = it; errorMessage = null },
                placeholder = "Server URL (e.g. http://server.com:8080)",
                modifier = Modifier.focusRequester(focusRequester),
                enabled = !isConnecting
            )

            TVTextField(
                value = user,
                onValueChange = { user = it; errorMessage = null },
                placeholder = "Username",
                enabled = !isConnecting
            )

            TVTextField(
                value = pass,
                onValueChange = { pass = it; errorMessage = null },
                placeholder = "Password",
                isPassword = true,
                enabled = !isConnecting
            )

            if (isConnecting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(20.dp).height(20.dp),
                        strokeWidth = 2.dp,
                        color = NuvioTheme.colors.Primary
                    )
                    Text(
                        text = "Đang kết nối và kiểm tra danh sách kênh Xtream...",
                        color = NuvioTheme.colors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                if (settings.isConfigured && !isConnecting) {
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioTheme.colors.Error.copy(alpha = 0.2f),
                            contentColor = NuvioTheme.colors.Error
                        )
                    ) {
                        Text(stringResource(R.string.livetv_disconnect))
                    }
                }

                Button(
                    onClick = onDismiss,
                    enabled = !isConnecting,
                    colors = ButtonDefaults.colors(
                        containerColor = NuvioTheme.colors.BackgroundCard,
                        contentColor = NuvioTheme.colors.TextPrimary
                    )
                ) {
                    Text(stringResource(R.string.action_cancel))
                }

                Button(
                    onClick = {
                        if (server.isNotBlank() && user.isNotBlank() && pass.isNotBlank() && !isConnecting) {
                            coroutineScope.launch {
                                isConnecting = true
                                errorMessage = null
                                val result = onConnect(
                                    LiveTvXtreamSettings(serverUrl = server.trim(), username = user.trim(), password = pass.trim())
                                )
                                isConnecting = false
                                result.onSuccess { count ->
                                    Toast.makeText(context, "Kết nối Xtream thành công! ($count kênh)", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }.onFailure { error ->
                                    errorMessage = error.localizedMessage ?: "Kết nối thất bại. Vui lòng kiểm tra lại thông tin."
                                }
                            }
                        }
                    },
                    enabled = server.isNotBlank() && user.isNotBlank() && pass.isNotBlank() && !isConnecting,
                    colors = ButtonDefaults.colors(
                        containerColor = NuvioTheme.colors.Primary,
                        contentColor = NuvioTheme.colors.OnPrimary
                    )
                ) {
                    Text(if (isConnecting) "Đang kết nối..." else stringResource(R.string.livetv_connect))
                }
            }
        }
    }
}

@Composable
private fun LiveTvStalkerDialog(
    settings: LiveTvStalkerSettings,
    onConnect: suspend (LiveTvStalkerSettings) -> Result<Int>,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    var portal by remember(settings.portalUrl) { mutableStateOf(settings.portalUrl) }
    var mac by remember(settings.macAddress) { mutableStateOf(settings.macAddress) }
    var user by remember(settings.username) { mutableStateOf(settings.username) }
    var pass by remember(settings.password) { mutableStateOf(settings.password) }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    NuvioDialog(
        onDismiss = { if (!isConnecting) onDismiss() },
        title = stringResource(R.string.livetv_section_stalker),
        subtitle = "Connect to MAG Stalker Portal using your Portal URL and MAC Address",
        width = 680.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = NuvioTheme.colors.Error.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = NuvioTheme.colors.Error,
                        fontSize = 13.sp
                    )
                }
            }

            TVTextField(
                value = portal,
                onValueChange = { portal = it; errorMessage = null },
                placeholder = "Portal URL (e.g. http://portal.domain/c/)",
                modifier = Modifier.focusRequester(focusRequester),
                enabled = !isConnecting
            )

            TVTextField(
                value = mac,
                onValueChange = { mac = it.uppercase(); errorMessage = null },
                placeholder = "MAC Address (e.g. 00:1A:79:XX:XX:XX)",
                enabled = !isConnecting
            )

            TVTextField(
                value = user,
                onValueChange = { user = it; errorMessage = null },
                placeholder = "Username (Optional)",
                enabled = !isConnecting
            )

            TVTextField(
                value = pass,
                onValueChange = { pass = it; errorMessage = null },
                placeholder = "Password (Optional)",
                isPassword = true,
                enabled = !isConnecting
            )

            if (isConnecting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(20.dp).height(20.dp),
                        strokeWidth = 2.dp,
                        color = NuvioTheme.colors.Primary
                    )
                    Text(
                        text = "Đang kết nối tới Stalker Portal và tải danh sách kênh...",
                        color = NuvioTheme.colors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                if (settings.isConfigured && !isConnecting) {
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioTheme.colors.Error.copy(alpha = 0.2f),
                            contentColor = NuvioTheme.colors.Error
                        )
                    ) {
                        Text(stringResource(R.string.livetv_disconnect))
                    }
                }

                Button(
                    onClick = onDismiss,
                    enabled = !isConnecting,
                    colors = ButtonDefaults.colors(
                        containerColor = NuvioTheme.colors.BackgroundCard,
                        contentColor = NuvioTheme.colors.TextPrimary
                    )
                ) {
                    Text(stringResource(R.string.action_cancel))
                }

                Button(
                    onClick = {
                        if (portal.isNotBlank() && mac.isNotBlank() && !isConnecting) {
                            coroutineScope.launch {
                                isConnecting = true
                                errorMessage = null
                                val result = onConnect(
                                    LiveTvStalkerSettings(portalUrl = portal.trim(), macAddress = mac.trim(), username = user.trim(), password = pass.trim())
                                )
                                isConnecting = false
                                result.onSuccess { count ->
                                    Toast.makeText(context, "Kết nối Stalker Portal thành công! ($count kênh)", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }.onFailure { error ->
                                    errorMessage = error.localizedMessage ?: "Kết nối thất bại. Vui lòng kiểm tra lại thông tin."
                                }
                            }
                        }
                    },
                    enabled = portal.isNotBlank() && mac.isNotBlank() && !isConnecting,
                    colors = ButtonDefaults.colors(
                        containerColor = NuvioTheme.colors.Primary,
                        contentColor = NuvioTheme.colors.OnPrimary
                    )
                ) {
                    Text(if (isConnecting) "Đang kết nối..." else stringResource(R.string.livetv_connect))
                }
            }
        }
    }
}

@Composable
private fun TVTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    enabled: Boolean = true
) {
    Card(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.colors(
            containerColor = if (enabled) NuvioTheme.colors.BackgroundElevated else NuvioTheme.colors.BackgroundElevated.copy(alpha = 0.5f),
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
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = if (enabled) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                singleLine = true,
                cursorBrush = SolidColor(NuvioTheme.colors.Primary),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None
            )
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = NuvioTheme.colors.TextMuted,
                    fontSize = 15.sp
                )
            }
        }
    }
}
