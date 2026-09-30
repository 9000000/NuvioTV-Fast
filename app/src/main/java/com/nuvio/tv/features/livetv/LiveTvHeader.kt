package com.nuvio.tv.features.livetv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Header component for Live TV screen.
 * Displays title, channel counts, search input bar, hint badge, refresh, and settings buttons.
 */
@Composable
internal fun LiveTvHeader(
    channelCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onRequestNavigateToFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchInputFocusRequester = remember { FocusRequester() }
    var isSearchInputFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.nav_livetv),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = NuvioTheme.colors.TextPrimary
            )
            Text(
                text = if (searchQuery.isNotBlank()) {
                    "Tìm thấy $channelCount kênh"
                } else {
                    stringResource(R.string.livetv_channels_count, channelCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (searchQuery.isNotBlank()) NuvioTheme.colors.Primary else NuvioTheme.colors.TextSecondary
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Search Bar (Task 3.3)
            AnimatedVisibility(
                visible = isSearchOpen,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .width(260.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(NuvioTheme.colors.BackgroundElevated)
                        .then(
                            if (isSearchInputFocused) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = NuvioTheme.colors.Primary,
                                    shape = RoundedCornerShape(20.dp)
                                )
                            } else Modifier
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = NuvioTheme.colors.TextSecondary
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .focusRequester(searchInputFocusRequester)
                            .onFocusChanged { isSearchInputFocused = it.isFocused }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                            onRequestNavigateToFilters()
                                            true
                                        }
                                        AndroidKeyEvent.KEYCODE_BACK -> {
                                            if (searchQuery.isNotBlank()) {
                                                onSearchQueryChange("")
                                                true
                                            } else {
                                                onToggleSearch()
                                                true
                                            }
                                        }
                                        else -> false
                                    }
                                } else false
                            },
                        textStyle = TextStyle(
                            color = NuvioTheme.colors.TextPrimary,
                            fontSize = 14.sp
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(NuvioTheme.colors.Primary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Tìm tên, nhóm kênh...",
                                        color = NuvioTheme.colors.TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Card(
                            onClick = { onSearchQueryChange("") },
                            colors = CardDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = NuvioTheme.colors.FocusBackground
                            ),
                            shape = CardDefaults.shape(CircleShape),
                            scale = CardDefaults.scale(focusedScale = 1.0f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(16.dp),
                                tint = NuvioTheme.colors.TextSecondary
                            )
                        }
                    }
                }
            }

            // Search Toggle Button
            Card(
                onClick = onToggleSearch,
                colors = CardDefaults.colors(
                    containerColor = if (isSearchOpen || searchQuery.isNotBlank()) NuvioTheme.colors.Primary
                    else NuvioTheme.colors.BackgroundElevated,
                    focusedContainerColor = NuvioTheme.colors.FocusBackground
                ),
                shape = CardDefaults.shape(CircleShape),
                scale = CardDefaults.scale(focusedScale = 1.0f),
                modifier = Modifier.onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                        keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                    ) {
                        onRequestNavigateToFilters()
                        true
                    } else false
                }
            ) {
                Box(Modifier.padding(10.dp)) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search channels",
                        modifier = Modifier.size(20.dp),
                        tint = if (isSearchOpen || searchQuery.isNotBlank()) NuvioTheme.colors.OnPrimary
                        else NuvioTheme.colors.TextPrimary
                    )
                }
            }

            // Hint badge: Giữ OK/Menu: Yêu thích
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NuvioTheme.colors.BackgroundElevated)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = Color(0xFFFFD700))
                Text(
                    text = "Giữ [OK] / [Menu]: Yêu thích",
                    color = NuvioTheme.colors.TextSecondary,
                    fontSize = 12.sp
                )
            }

            // Refresh Button
            Card(
                onClick = onRefresh,
                colors = CardDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundElevated,
                    focusedContainerColor = NuvioTheme.colors.FocusBackground
                ),
                shape = CardDefaults.shape(CircleShape),
                scale = CardDefaults.scale(focusedScale = 1.0f),
                modifier = Modifier.onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                        keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                    ) {
                        onRequestNavigateToFilters()
                        true
                    } else false
                }
            ) {
                Box(Modifier.padding(10.dp)) {
                    Icon(Icons.Default.Refresh, "Refresh", Modifier.size(20.dp), tint = NuvioTheme.colors.TextPrimary)
                }
            }

            // Settings Button
            Card(
                onClick = onNavigateToSettings,
                colors = CardDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundElevated,
                    focusedContainerColor = NuvioTheme.colors.FocusBackground
                ),
                shape = CardDefaults.shape(CircleShape),
                scale = CardDefaults.scale(focusedScale = 1.0f),
                modifier = Modifier.onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                        keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                    ) {
                        onRequestNavigateToFilters()
                        true
                    } else false
                }
            ) {
                Box(Modifier.padding(10.dp)) {
                    Icon(Icons.Default.Settings, "Settings", Modifier.size(20.dp), tint = NuvioTheme.colors.TextPrimary)
                }
            }
        }
    }
}
