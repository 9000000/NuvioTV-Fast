package com.nuvio.tv.features.livetv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlaylistPlay
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

// ─── Filter Chip ──────────────────────────────────────────────────────────────

@Composable
internal fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = if (isSelected) NuvioTheme.colors.Primary
                            else NuvioTheme.colors.BackgroundElevated,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
        scale = CardDefaults.scale(focusedScale = 1.0f),
        modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
            Text(
                text = label,
                color = if (isSelected) NuvioTheme.colors.OnPrimary else NuvioTheme.colors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

// ─── Playlist Dropdown Button ──────────────────────────────────────────────────

@Composable
internal fun PlaylistDropdownButton(
    selectedName: String?,
    isActive: Boolean,
    isOpen: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val displayName = selectedName ?: stringResource(R.string.livetv_select_playlist)
    
    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = if (isActive) NuvioTheme.colors.Primary else NuvioTheme.colors.BackgroundElevated,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
        scale = CardDefaults.scale(focusedScale = 1.0f),
        modifier = Modifier.focusRequester(focusRequester)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlaylistPlay,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (isActive) NuvioTheme.colors.OnPrimary else NuvioTheme.colors.TextSecondary
            )
            Text(
                text = displayName,
                color = if (isActive) NuvioTheme.colors.OnPrimary else NuvioTheme.colors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isActive) NuvioTheme.colors.OnPrimary else NuvioTheme.colors.TextSecondary
            )
        }
    }
}

// ─── Playlist Dropdown Panel ───────────────────────────────────────────────────

@Composable
internal fun PlaylistDropdownPanel(
    playlists: List<LiveTvPlaylist>,
    selectedPlaylistId: String?,
    onDefaultViewSelected: () -> Unit,
    onPlaylistSelected: (LiveTvPlaylist) -> Unit,
    onDismiss: () -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    val playlistFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    
    LaunchedEffect(Unit) {
        delay(80)
        firstItemFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(NuvioTheme.colors.BackgroundElevated)
            .widthIn(min = 230.dp, max = 340.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NuvioTheme.colors.Background.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.PlaylistPlay, null, Modifier.size(16.dp),
                    tint = NuvioTheme.colors.Primary)
                Text(stringResource(R.string.livetv_select_playlist),
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioTheme.colors.TextSecondary,
                    fontWeight = FontWeight.Medium)
            }

            // Divider
            Box(Modifier.fillMaxWidth().height(1.dp).background(NuvioTheme.colors.Border))

            // Playlist list
            LazyColumn(
                modifier = Modifier
                    .widthIn(min = 230.dp, max = 340.dp)
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                // DEFAULT VIEW item
                if (selectedPlaylistId != null) {
                    item(key = "default_view") {
                        PlaylistDropdownDefaultViewItem(
                            focusRequester = firstItemFocusRequester,
                            onClick = onDefaultViewSelected,
                            onKeyUp = onDismiss,
                            isFirstItem = true,
                            isLastItem = playlists.isEmpty()
                        )
                    }
                }
                
                // Playlist items
                itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                    val isSelected = playlist.id == selectedPlaylistId
                    val isFirst = selectedPlaylistId == null && index == 0
                    val itemFocusRequester = remember(playlist.id) { 
                        if (isFirst) firstItemFocusRequester else playlistFocusRequesters.getOrPut(playlist.id) { FocusRequester() }
                    }
                    val isLastItem = index == playlists.lastIndex

                    PlaylistDropdownItem(
                        playlist = playlist,
                        isSelected = isSelected,
                        focusRequester = itemFocusRequester,
                        onClick = { onPlaylistSelected(playlist) },
                        onKeyUp = onDismiss,
                        isFirstItem = isFirst,
                        isLastItem = isLastItem
                    )
                }
            }
        }
    }
}

// ─── Playlist Dropdown Default View Item ────────────────────────────────────────

@Composable
private fun PlaylistDropdownDefaultViewItem(
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onKeyUp: () -> Unit,
    isFirstItem: Boolean,
    isLastItem: Boolean
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        isFocused -> NuvioTheme.colors.FocusBackground
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                when {
                    isFirstItem &&
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                        onKeyUp(); true
                    }
                    isLastItem &&
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        true
                    }
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK -> {
                        onKeyUp(); true
                    }
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode in listOf(
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER
                    ) -> { onClick(); true }
                    else -> false
                }
            }
    ) {
        Card(
            onClick = onClick,
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale = CardDefaults.scale(focusedScale = 1.0f),
            border = CardDefaults.border(
                border = Border(BorderStroke(0.dp, Color.Transparent)),
                focusedBorder = Border(BorderStroke(0.dp, Color.Transparent))
            ),
            shape = CardDefaults.shape(RoundedCornerShape(0.dp)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isFocused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary
                )
                Text(
                    text = stringResource(R.string.livetv_default_view),
                    color = if (isFocused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─── Playlist Dropdown Item ────────────────────────────────────────────────────

@Composable
private fun PlaylistDropdownItem(
    playlist: LiveTvPlaylist,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onKeyUp: () -> Unit,
    isFirstItem: Boolean,
    isLastItem: Boolean
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        isSelected -> NuvioTheme.colors.Primary.copy(alpha = 0.18f)
        isFocused -> NuvioTheme.colors.FocusBackground
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                when {
                    // DOWN from last item → no-op
                    isLastItem &&
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        true
                    }
                    // BACK → close dropdown
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK -> {
                        onKeyUp(); true
                    }
                    // OK / CENTER → select
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode in listOf(
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER
                    ) -> { onClick(); true }
                    else -> false
                }
            }
    ) {
        Card(
            onClick = onClick,
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale = CardDefaults.scale(focusedScale = 1.0f),
            border = CardDefaults.border(
                border = Border(BorderStroke(0.dp, Color.Transparent)),
                focusedBorder = Border(BorderStroke(0.dp, Color.Transparent))
            ),
            shape = CardDefaults.shape(RoundedCornerShape(0.dp)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isSelected) NuvioTheme.colors.Primary
                           else if (isFocused) NuvioTheme.colors.TextPrimary
                           else NuvioTheme.colors.TextSecondary
                )
                Text(
                    text = playlist.name,
                    color = if (isSelected) NuvioTheme.colors.Primary
                            else if (isFocused) NuvioTheme.colors.TextPrimary
                            else NuvioTheme.colors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp),
                        tint = NuvioTheme.colors.Primary)
                }
            }
        }
    }
}
