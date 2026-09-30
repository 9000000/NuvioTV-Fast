package com.nuvio.tv.features.livetv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

// ─── Loading State ────────────────────────────────────────────────────────────

@Composable
internal fun LiveTvLoadingState() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        CircularProgressIndicator(color = NuvioTheme.colors.Primary)
    }
}

// ─── Empty State (no playlist configured) ────────────────────────────────────

@Composable
internal fun LiveTvEmptyState(onNavigateToSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Card(
            onClick = onNavigateToSettings,
            modifier = Modifier.width(480.dp).padding(24.dp),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                focusedContainerColor = NuvioTheme.colors.FocusBackground
            ),
            border = CardDefaults.border(
                border = Border(
                    border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                    shape = RoundedCornerShape(16.dp)
                ),
                focusedBorder = Border(
                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xs),
                    shape = RoundedCornerShape(16.dp)
                )
            ),
            shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
            scale = CardDefaults.scale(focusedScale = 1.0f)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.LiveTv, null, Modifier.size(64.dp), tint = NuvioTheme.colors.Primary)
                Text(stringResource(R.string.livetv_empty_title), style = MaterialTheme.typography.headlineSmall,
                    color = NuvioTheme.colors.TextPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(stringResource(R.string.livetv_empty_desc), style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(NuvioTheme.colors.Primary)
                    .padding(horizontal = 24.dp, vertical = 10.dp)) {
                    Text(stringResource(R.string.livetv_configure_now), color = NuvioTheme.colors.OnPrimary,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ─── Error State ──────────────────────────────────────────────────────────────

@Composable
internal fun LiveTvErrorState(message: String, onRetry: () -> Unit, onNavigateToSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(24.dp)) {
            Text("⚠️ $message", color = NuvioTheme.colors.Error,
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(onClick = onRetry,
                    colors = CardDefaults.colors(containerColor = NuvioTheme.colors.Primary,
                        focusedContainerColor = NuvioTheme.colors.FocusBackground),
                    shape = CardDefaults.shape(RoundedCornerShape(8.dp))) {
                    Text("Thử lại (Retry)", Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = NuvioTheme.colors.OnPrimary, fontWeight = FontWeight.Bold)
                }
                Card(onClick = onNavigateToSettings,
                    colors = CardDefaults.colors(containerColor = NuvioTheme.colors.BackgroundElevated,
                        focusedContainerColor = NuvioTheme.colors.FocusBackground),
                    shape = CardDefaults.shape(RoundedCornerShape(8.dp))) {
                    Text(stringResource(R.string.settings_livetv_title),
                        Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = NuvioTheme.colors.TextPrimary)
                }
            }
        }
    }
}

// ─── Empty Channel State (within a filter/playlist) ──────────────────────────

@Composable
internal fun LiveTvEmptyChannelState(
    activeFilter: FilterType,
    hasPlaylistSelected: Boolean,
    onSelectPlaylist: () -> Unit
) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        if (!hasPlaylistSelected) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                val emptyIcon = if (activeFilter == FilterType.FAVORITES) Icons.Default.Star else Icons.Default.LiveTv
                val emptyTitle = if (activeFilter == FilterType.FAVORITES) {
                    stringResource(R.string.livetv_empty_favorites_title)
                } else {
                    stringResource(R.string.livetv_empty_recent_title)
                }
                val emptyDesc = if (activeFilter == FilterType.FAVORITES) {
                    stringResource(R.string.livetv_empty_favorites_desc)
                } else {
                    stringResource(R.string.livetv_empty_recent_desc)
                }

                Icon(emptyIcon, null, Modifier.size(52.dp), tint = NuvioTheme.colors.Primary.copy(alpha = 0.8f))
                Text(emptyTitle, style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(emptyDesc, style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Card(
                    onClick = onSelectPlaylist,
                    colors = CardDefaults.colors(
                        containerColor = NuvioTheme.colors.Primary,
                        focusedContainerColor = NuvioTheme.colors.FocusBackground
                    ),
                    shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = CardDefaults.scale(focusedScale = 1.05f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PlaylistPlay, null, Modifier.size(20.dp), tint = NuvioTheme.colors.OnPrimary)
                        Text(stringResource(R.string.livetv_select_playlist), color = NuvioTheme.colors.OnPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        } else {
            Text("Không có kênh nào trong mục này",
                color = NuvioTheme.colors.TextSecondary, fontSize = 15.sp)
        }
    }
}
