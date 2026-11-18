package com.nuvio.tv.ui.leanback.adapter

private const val MAX_MYERS_DIFF_SIZE = 300

/**
 * Pure update strategy planner shared between [LeanbackUniversalAdapter] and Modern Home adapters.
 * Prevents full dataset resets (`notifyDataSetChanged`) on 300+ item grids when item keys stay in place
 * (e.g., marking a movie as watched or updating playback progress).
 */
internal sealed interface LeanbackUpdatePlan {
    data object NoOp : LeanbackUpdatePlan

    data class AppendAndChange(
        val insertStart: Int,
        val insertCount: Int,
        val changedPrefixPositions: List<Int>
    ) : LeanbackUpdatePlan

    data class InPlaceChanges(
        val changedPositions: List<Int>
    ) : LeanbackUpdatePlan

    data object MyersDiff : LeanbackUpdatePlan

    data object FullReset : LeanbackUpdatePlan
}

internal inline fun <T> computeLeanbackUpdatePlan(
    oldItems: List<T>,
    newItems: List<T>,
    keyOf: (T) -> String,
    visualsMatch: (T, T) -> Boolean,
    maxDiffSize: Int = MAX_MYERS_DIFF_SIZE
): LeanbackUpdatePlan {
    val oldSize = oldItems.size
    val newSize = newItems.size

    if (oldSize == 0 && newSize == 0) return LeanbackUpdatePlan.NoOp
    if (oldSize == 0 || newSize == 0) return LeanbackUpdatePlan.FullReset

    // Fast O(N) check when keys at all existing positions are identical
    if (newSize >= oldSize) {
        var prefixKeysMatch = true
        for (i in 0 until oldSize) {
            if (keyOf(oldItems[i]) != keyOf(newItems[i])) {
                prefixKeysMatch = false
                break
            }
        }
        if (prefixKeysMatch) {
            var changed: MutableList<Int>? = null
            for (i in 0 until oldSize) {
                if (!visualsMatch(oldItems[i], newItems[i])) {
                    if (changed == null) changed = ArrayList(4)
                    changed.add(i)
                }
            }
            val changedList = changed ?: emptyList()
            return if (newSize == oldSize) {
                if (changedList.isEmpty()) LeanbackUpdatePlan.NoOp
                else LeanbackUpdatePlan.InPlaceChanges(changedList)
            } else {
                LeanbackUpdatePlan.AppendAndChange(
                    insertStart = oldSize,
                    insertCount = newSize - oldSize,
                    changedPrefixPositions = changedList
                )
            }
        }
    }

    return if (oldSize <= maxDiffSize && newSize <= maxDiffSize) {
        LeanbackUpdatePlan.MyersDiff
    } else {
        LeanbackUpdatePlan.FullReset
    }
}
