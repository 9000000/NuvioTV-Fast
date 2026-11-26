package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernHomeFocusRingAnchorTest {

    @Test
    fun `normal card navigation always stays anchored at keyline even when grid reports idle`() {
        assertFalse(
            shouldUseLiveFocusRingTargetX(
                isExpandedCard = false,
                isAnimatingTargetSize = false,
                isRowAnimatingWidth = false,
                explicitUseLivePosition = false,
                isMorphingSize = false,
                isHoldingHorizontalKey = false,
                hasPendingHorizontalNavigation = false,
                isCollapseTransitionActive = false
            )
        )
    }

    @Test
    fun `expanded or in-place width-animating card tracks live bounds while settled`() {
        assertTrue(
            shouldUseLiveFocusRingTargetX(
                isExpandedCard = true,
                isAnimatingTargetSize = false,
                isRowAnimatingWidth = false,
                explicitUseLivePosition = false,
                isMorphingSize = false,
                isHoldingHorizontalKey = false,
                hasPendingHorizontalNavigation = false,
                isCollapseTransitionActive = false
            )
        )
        assertTrue(
            shouldUseLiveFocusRingTargetX(
                isExpandedCard = false,
                isAnimatingTargetSize = true,
                isRowAnimatingWidth = false,
                explicitUseLivePosition = false,
                isMorphingSize = false,
                isHoldingHorizontalKey = false,
                hasPendingHorizontalNavigation = false,
                isCollapseTransitionActive = false
            )
        )
        assertTrue(
            shouldUseLiveFocusRingTargetX(
                isExpandedCard = false,
                isAnimatingTargetSize = false,
                isRowAnimatingWidth = true,
                explicitUseLivePosition = false,
                isMorphingSize = false,
                isHoldingHorizontalKey = false,
                hasPendingHorizontalNavigation = false,
                isCollapseTransitionActive = false
            )
        )
    }

    @Test
    fun `in-flight ring morph, horizontal hold, pending navigation, or collapse transition forces anchored keyline`() {
        assertFalse(
            shouldUseLiveFocusRingTargetX(
                isExpandedCard = true,
                isAnimatingTargetSize = true,
                isRowAnimatingWidth = true,
                explicitUseLivePosition = true,
                isMorphingSize = true,
                isHoldingHorizontalKey = false,
                hasPendingHorizontalNavigation = false,
                isCollapseTransitionActive = false
            )
        )
        assertFalse(
            shouldUseLiveFocusRingTargetX(
                isExpandedCard = true,
                isAnimatingTargetSize = false,
                isRowAnimatingWidth = false,
                explicitUseLivePosition = true,
                isMorphingSize = false,
                isHoldingHorizontalKey = true,
                hasPendingHorizontalNavigation = false,
                isCollapseTransitionActive = false
            )
        )
        assertFalse(
            shouldUseLiveFocusRingTargetX(
                isExpandedCard = true,
                isAnimatingTargetSize = false,
                isRowAnimatingWidth = false,
                explicitUseLivePosition = true,
                isMorphingSize = false,
                isHoldingHorizontalKey = false,
                hasPendingHorizontalNavigation = true,
                isCollapseTransitionActive = false
            )
        )
        assertFalse(
            shouldUseLiveFocusRingTargetX(
                isExpandedCard = true,
                isAnimatingTargetSize = false,
                isRowAnimatingWidth = false,
                explicitUseLivePosition = true,
                isMorphingSize = false,
                isHoldingHorizontalKey = false,
                hasPendingHorizontalNavigation = false,
                isCollapseTransitionActive = true
            )
        )
    }
}
