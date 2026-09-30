package com.nuvio.tv.features.livetv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.ui.theme.NuvioTheme

// ─── Channel Card ─────────────────────────────────────────────────────────────

@Composable
internal fun TvChannelCard(
    channel: LiveTvChannel,
    isFavorite: Boolean,
    isLaunching: Boolean = false,
    focusRequester: FocusRequester? = null,
    isFirstRow: Boolean = false,
    isLastRow: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestNavigateToCategory: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        onLongClick = onToggleFavorite,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.25f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                val code = keyEvent.nativeKeyEvent.keyCode
                val action = keyEvent.nativeKeyEvent.action
                when {
                    action == AndroidKeyEvent.ACTION_DOWN &&
                    code == AndroidKeyEvent.KEYCODE_DPAD_UP && isFirstRow -> {
                        onRequestNavigateToCategory(); true
                    }
                    action == AndroidKeyEvent.ACTION_DOWN &&
                    code == AndroidKeyEvent.KEYCODE_DPAD_DOWN && isLastRow -> {
                        true
                    }
                    action == AndroidKeyEvent.ACTION_UP && code in listOf(
                        AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_STAR,
                        AndroidKeyEvent.KEYCODE_BUTTON_Y, AndroidKeyEvent.KEYCODE_BOOKMARK,
                        AndroidKeyEvent.KEYCODE_PROG_YELLOW
                    ) -> { onToggleFavorite(); true }
                    else -> false
                }
            },
        colors = CardDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundElevated,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                shape = RoundedCornerShape(12.dp)
            ),
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xs),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.0f)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier.fillMaxSize().padding(12.dp).padding(bottom = 28.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(Modifier.fillMaxSize().padding(bottom = 28.dp), Alignment.Center) {
                    Icon(Icons.Default.LiveTv, null, Modifier.size(36.dp),
                        tint = NuvioTheme.colors.TextMuted)
                }
            }
            // Favorite badge
            if (isFavorite) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).padding(4.dp)) {
                    Icon(Icons.Default.Star, "Favorite", Modifier.size(16.dp), tint = Color(0xFFFFD700))
                }
            } else if (isFocused) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)).padding(4.dp)) {
                    Icon(Icons.Default.Star, "Add to favorite", Modifier.size(16.dp),
                        tint = Color.White.copy(alpha = 0.6f))
                }
            }
            // Channel name bar
            Box(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(channel.name, color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }

            // Launching loading overlay for instant feedback
            if (isLaunching) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = NuvioTheme.colors.Primary,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}
