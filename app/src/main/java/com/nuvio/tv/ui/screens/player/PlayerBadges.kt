package com.nuvio.tv.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.nuvio.tv.core.streams.StreamBadgeMatcher
import com.nuvio.tv.domain.model.StreamBadge
import com.nuvio.tv.ui.components.StreamImportedBadgeChip
import com.nuvio.tv.ui.util.languageCodeToName

@Composable
internal fun rememberPlayerBadges(viewModel: PlayerViewModel, uiState: PlayerUiState): List<StreamBadge> {
    val filters = uiState.playerBadgeFilters
    var mediaInfo by remember { mutableStateOf(PlayerMediaInfo()) }
    var streamFilename by remember { mutableStateOf<String?>(null) }
    val selectedAudio = uiState.audioTracks.firstOrNull { it.isSelected }
    LaunchedEffect(
        filters,
        uiState.showControls,
        uiState.isBuffering,
        uiState.currentStreamUrl,
        uiState.internalPlayerEngine,
        selectedAudio?.index
    ) {
        if (filters.isNotEmpty() && uiState.showControls && !uiState.isBuffering) {
            mediaInfo = viewModel.playerMediaInfo()
            streamFilename = viewModel.playerStreamFilename()
        }
    }
    if (filters.isEmpty()) return emptyList()
    val language = selectedAudio?.language
    val text = playbackMatchText(mediaInfo, listOfNotNull(language, language?.let(::languageCodeToName)))
    // Keep candidates separate so regex lookaheads in one don't bleed into another.
    // Collapse whitespace so \n in stream names doesn't break single-line regex patterns.
    val playbackCandidate = text.takeIf { it.isNotBlank() }
    val metaCandidates = listOfNotNull(
        streamFilename?.collapseWhitespace(),
        uiState.currentStreamName?.collapseWhitespace()
    )
    return remember(playbackCandidate, metaCandidates, filters) {
        val matched = linkedMapOf<String, StreamBadge>()
        fun collect(badge: StreamBadge) {
            val key = badge.imageURL.takeIf { it.isNotBlank() } ?: badge.name
            if (key !in matched) matched[key] = badge
        }
        // Phase 1: match all filters against playback candidate (decoder truth)
        // and track which groups got a hit.
        val groupsMatchedByPlayback = mutableSetOf<String>()
        if (playbackCandidate != null) {
            for (filter in filters) {
                if (filter.pattern.matcher(playbackCandidate).find()) {
                    collect(filter.badge)
                    if (filter.groupId.isNotBlank()) groupsMatchedByPlayback += filter.groupId
                }
            }
        }
        // Phase 2: match remaining filters against filename/streamName, but skip groups
        // that already matched from the decoder.
        if (metaCandidates.isNotEmpty()) {
            val metaFilters = if (groupsMatchedByPlayback.isEmpty()) {
                filters
            } else {
                filters.filter { it.groupId.isBlank() || it.groupId !in groupsMatchedByPlayback }
            }
            if (metaFilters.isNotEmpty()) {
                for (badge in StreamBadgeMatcher.matchedBadges(metaCandidates, metaFilters)) {
                    collect(badge)
                }
            }
        }
        matched.values.filter { it.imageURL.isNotBlank() }
    }
}

private fun String.collapseWhitespace(): String? =
    replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }

@Composable
internal fun PlayerBadges(
    badges: List<StreamBadge>,
    modifier: Modifier = Modifier
) {
    Layout(
        content = { badges.forEach { StreamImportedBadgeChip(badge = it) } },
        modifier = modifier
    ) { measurables, constraints ->
        val gap = 4.dp.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        var width = 0
        var visibleCount = 0
        for (placeable in placeables) {
            val next = if (visibleCount == 0) placeable.width else width + gap + placeable.width
            if (next > constraints.maxWidth) break
            width = next
            visibleCount++
        }
        val visible = placeables.take(visibleCount)
        val height = visible.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            var x = 0
            visible.forEach { placeable ->
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width + gap
            }
        }
    }
}
