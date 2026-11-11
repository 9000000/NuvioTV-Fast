package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.MetaCastMember
import com.nuvio.tv.ui.leanback.LeanbackNativeRow
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun CastSection(
    cast: List<MetaCastMember>,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    title: String = "Cast",
    leadingCast: List<MetaCastMember> = emptyList(),
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    sectionFocusRequester: FocusRequester? = null,
    restorePersonId: Int? = null,
    restoreFocusToken: Int = 0,
    blockDefaultRestore: Boolean = false,
    lastFocusedPersonKey: String? = null,
    onLastFocusedPersonKeyChange: (String) -> Unit = {},
    onRestoreFocusHandled: () -> Unit = {},
    onCastMemberFocused: (MetaCastMember) -> Unit = {},
    onCastMemberClick: (MetaCastMember) -> Unit = {},
    windowResetKey: String? = null
) {
    if (cast.isEmpty() && leadingCast.isEmpty()) return

    val allMembers = remember(leadingCast, cast) {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<MetaCastMember>()
        (leadingCast + cast).forEach { member ->
            val key = "${member.tmdbId ?: member.name}:${member.character.orEmpty()}"
            if (seen.add(key)) {
                result.add(member)
            }
        }
        result
    }

    val leanbackItems = remember(allMembers) {
        allMembers.map { member ->
            val key = "cast_${member.tmdbId ?: member.name}_${member.character.orEmpty()}"
            LeanbackCardItem.Avatar(
                key = key,
                title = member.name,
                imageUrl = member.photo,
                subtitle = member.character,
                payload = member
            )
        }
    }

    val hasTitle = title.isNotBlank()
    val initialIndex = remember(leanbackItems, restorePersonId, lastFocusedPersonKey) {
        when {
            restorePersonId != null -> allMembers.indexOfFirst { it.tmdbId == restorePersonId }.coerceAtLeast(0)
            !lastFocusedPersonKey.isNullOrBlank() -> leanbackItems.indexOfFirst { it.key == lastFocusedPersonKey }.coerceAtLeast(0)
            else -> 0
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (blockDefaultRestore) {
                    Modifier.focusProperties { canFocus = false }
                } else {
                    Modifier
                }
            )
            .padding(top = if (hasTitle) 20.dp else NuvioTheme.spacing.sm, bottom = NuvioTheme.spacing.sm)
    ) {
        if (hasTitle) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = NuvioTheme.colors.TextPrimary,
                modifier = Modifier.padding(horizontal = NuvioTheme.spacing.xxxl)
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
        }

        LeanbackNativeRow(
            rowKey = "cast_${windowResetKey ?: "default"}",
            items = leanbackItems,
            cardWidth = 120.dp,
            cardHeight = 120.dp,
            cornerRadius = 60.dp,
            showLabels = true,
            animationsEnabled = NuvioTheme.animationsEnabled,
            startPadding = NuvioTheme.spacing.xxxl,
            spacing = 18.dp,
            isFocusEnabled = !blockDefaultRestore,
            onItemFocused = { _, cardItem ->
                val member = cardItem.payload as? MetaCastMember ?: return@LeanbackNativeRow
                onLastFocusedPersonKeyChange(cardItem.key)
                onCastMemberFocused(member)
                if (restorePersonId != null && member.tmdbId == restorePersonId) {
                    onRestoreFocusHandled()
                }
            },
            onItemClick = { cardItem ->
                val member = cardItem.payload as? MetaCastMember ?: return@LeanbackNativeRow
                onCastMemberClick(member)
            },
            onNavigateUp = {
                upFocusRequester?.requestFocus()
                upFocusRequester != null
            },
            onNavigateDown = {
                downFocusRequester?.requestFocus()
                downFocusRequester != null
            },
            initialFocusPosition = initialIndex,
            isPendingFocus = restorePersonId != null && restoreFocusToken > 0,
            pendingFocusIndex = initialIndex,
            pendingFocusNonce = restoreFocusToken,
            onClearPendingFocus = onRestoreFocusHandled,
            entryFocusRequester = sectionFocusRequester
        )
    }
}
