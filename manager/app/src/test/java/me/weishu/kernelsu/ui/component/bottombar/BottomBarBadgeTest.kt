package me.weishu.kernelsu.ui.component.bottombar

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomBarBadgeTest {
    @Test
    fun `module badge ignores the module count when an update is available`() {
        val state = NavigationBadgeState(
            moduleEnabledCount = 4,
            moduleUpdatableCount = 1,
        )

        assertEquals(
            NavBadge(1, BadgeTone.Alert),
            badgeFor(BottomBarDestination.Module, state),
        )
    }
}
