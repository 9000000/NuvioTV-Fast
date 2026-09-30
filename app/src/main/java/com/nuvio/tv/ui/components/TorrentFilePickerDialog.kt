@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.torrent.TorrServerRemoteFile
import com.nuvio.tv.ui.theme.NuvioTheme
import java.util.Locale

private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "webm", "ts", "m4v", "mov", "wmv", "flv")

private data class TorrentFileItemUiModel(
    val id: Int,
    val cleanFileName: String,
    val folderPath: String,
    val formattedSize: String,
    val isVideo: Boolean,
    val isMatched: Boolean
)

@Composable
fun TorrentFilePickerDialog(
    title: String,
    isLoading: Boolean,
    error: String? = null,
    files: List<TorrServerRemoteFile>,
    targetSeason: Int? = null,
    targetEpisode: Int? = null,
    contentType: String? = null,
    onFileSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val closeFocusRequester = remember { FocusRequester() }

    // Pre-calculate sorted list and UI models once per input change
    val uiFiles = remember(files, targetSeason, targetEpisode, contentType) {
        val isMovie = contentType?.equals("movie", ignoreCase = true) == true ||
            (targetSeason == null && targetEpisode == null &&
             contentType?.equals("series", ignoreCase = true) != true &&
             contentType?.equals("tv", ignoreCase = true) != true)
        val matcher = buildEpisodeMatcher(targetSeason, targetEpisode)
        val sorted = if (isMovie) {
            files.sortedWith(
                compareByDescending<TorrServerRemoteFile> { file ->
                    val ext = file.path.substringAfterLast('.', "").lowercase()
                    ext in VIDEO_EXTENSIONS
                }.thenByDescending { it.length }
                .thenBy { it.path }
            )
        } else {
            files.sortedWith(
                compareByDescending<TorrServerRemoteFile> { file ->
                    val ext = file.path.substringAfterLast('.', "").lowercase()
                    ext in VIDEO_EXTENSIONS
                }.thenBy { it.path }
            )
        }
        sorted.map { file ->
            val ext = file.path.substringAfterLast('.', "").lowercase()
            val isVideo = ext in VIDEO_EXTENSIONS
            val isMatched = matcher(file.path)
            TorrentFileItemUiModel(
                id = file.id,
                cleanFileName = file.path.substringAfterLast('/'),
                folderPath = file.path.substringBeforeLast('/', ""),
                formattedSize = formatFileSize(file.length),
                isVideo = isVideo,
                isMatched = isMatched
            )
        }
    }

    val matchedIndex = remember(uiFiles) {
        uiFiles.indexOfFirst { it.isMatched }
    }

    val initialIndex = remember(matchedIndex) {
        if (matchedIndex >= 0) matchedIndex else 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    val fileFocusRequesters = remember(uiFiles.size) {
        List(uiFiles.size) { FocusRequester() }
    }

    LaunchedEffect(uiFiles, matchedIndex, isLoading) {
        if (!isLoading && uiFiles.isNotEmpty()) {
            val targetIdx = if (matchedIndex >= 0) matchedIndex else 0
            if (targetIdx in fileFocusRequesters.indices) {
                listState.scrollToItem(targetIdx)
                fileFocusRequesters[targetIdx].requestFocus()
            }
        } else if (!isLoading && uiFiles.isEmpty()) {
            closeFocusRequester.requestFocus()
        }
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.torrent_file_picker_title),
        subtitle = title.ifBlank { null },
        width = 660.dp
    ) {
        when {
            isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LoadingIndicator(modifier = Modifier.size(48.dp))
                    Text(
                        text = stringResource(R.string.torrent_file_picker_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NuvioTheme.colors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(closeFocusRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioTheme.colors.BackgroundCard,
                            contentColor = NuvioTheme.colors.TextPrimary
                        )
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }

            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NuvioTheme.colors.Error
                    )
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(closeFocusRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioTheme.colors.BackgroundCard,
                            contentColor = NuvioTheme.colors.TextPrimary
                        )
                    ) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }

            uiFiles.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.torrent_file_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NuvioTheme.colors.TextSecondary
                    )
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(closeFocusRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioTheme.colors.BackgroundCard,
                            contentColor = NuvioTheme.colors.TextPrimary
                        )
                    ) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }

            else -> {
                Text(
                    text = stringResource(R.string.torrent_file_picker_hint, uiFiles.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioTheme.colors.TextMuted
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiFiles, key = { _, item -> item.id }) { index, item ->
                        val isMatched = item.isMatched
                        val isVideo = item.isVideo
                        var isFocused by remember { mutableStateOf(false) }

                        Card(
                            onClick = { onFileSelected(item.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(fileFocusRequesters[index])
                                .onFocusChanged { isFocused = it.isFocused },
                            colors = CardDefaults.colors(
                                containerColor = if (isMatched) {
                                    NuvioTheme.colors.Primary.copy(alpha = 0.15f)
                                } else {
                                    NuvioTheme.colors.BackgroundCard
                                },
                                focusedContainerColor = NuvioTheme.colors.Primary
                            ),
                            border = CardDefaults.border(
                                border = if (isMatched) {
                                    Border(BorderStroke(1.dp, NuvioTheme.colors.Primary))
                                } else {
                                    Border(BorderStroke(1.dp, NuvioTheme.colors.Border))
                                },
                                focusedBorder = Border(BorderStroke(2.dp, NuvioTheme.colors.PrimaryLight))
                            ),
                            shape = CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isFocused) Color.White else if (isVideo) NuvioTheme.colors.Primary else NuvioTheme.colors.TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = item.cleanFileName,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isMatched) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isFocused) Color.White else NuvioTheme.colors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )

                                        if (isMatched) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isFocused) Color.White.copy(alpha = 0.25f) else NuvioTheme.colors.Primary)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.torrent_file_matched_badge),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }

                                    if (item.folderPath.isNotBlank()) {
                                        Text(
                                            text = item.folderPath,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isFocused) Color.White.copy(alpha = 0.8f) else NuvioTheme.colors.TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (item.formattedSize.isNotBlank()) {
                                    Text(
                                        text = item.formattedSize,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isFocused) Color.White else NuvioTheme.colors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

internal fun buildEpisodeMatcher(season: Int?, episode: Int?): (String) -> Boolean {
    if (episode == null) return { false }
    val epPattern = String.format(Locale.US, "%02d", episode)
    val epSingle = episode.toString()

    val explicitSeasonEpPatterns = if (season != null) {
        val sPattern = String.format(Locale.US, "%02d", season)
        val sSingle = season.toString()
        listOf(
            "s${sPattern}e${epPattern}",
            "s${sSingle}e${epPattern}",
            "s${sPattern}e${epSingle}",
            "s${sSingle}e${epSingle}",
            "${sSingle}x${epPattern}",
            "${sSingle}x${epSingle}"
        )
    } else emptyList()

    val epPrefixedPatterns = listOf(
        "e${epPattern}",
        "ep${epPattern}",
        "ep.${epPattern}",
        "episode ${epSingle}",
        "episode ${epPattern}"
    )

    // Regex for anime filenames (e.g. "One Piece - 1050", "[SubsPlease] One Piece - 1050 (1080p)", "One Piece 0500")
    val animeEpRegex = Regex("""(?i)(?<!\d)(?:e|ep|ep\.|episode[\s._-]*)?0*${episode}(?:v\d+)?(?![pPkK\d])(?=[\s._\-\])]|$)""")
    val otherSeasonRegex = if (season != null) {
        Regex("""(?i)\b[sS]0*(\d+)[eE]""")
    } else null

    return { path ->
        val clean = path.substringAfterLast('/').substringBeforeLast('.')
        // 1. Explicit SxxExx match takes highest priority
        if (explicitSeasonEpPatterns.any { clean.contains(it, ignoreCase = true) }) {
            true
        } else {
            // Check if file specifies a different season (e.g. S02E... when targeting season 1)
            val hasDifferentSeason = otherSeasonRegex?.find(clean)?.let { matchResult ->
                val fileSeason = matchResult.groupValues[1].toIntOrNull()
                fileSeason != null && fileSeason != season
            } ?: false

            if (hasDifferentSeason) {
                false
            } else if (epPrefixedPatterns.any { clean.contains(it, ignoreCase = true) }) {
                true
            } else {
                animeEpRegex.containsMatchIn(clean)
            }
        }
    }
}
