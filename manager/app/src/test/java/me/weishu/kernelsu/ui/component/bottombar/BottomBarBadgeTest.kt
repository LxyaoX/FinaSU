package me.weishu.kernelsu.ui.component.bottombar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomBarBadgeTest {
    @Test
    fun `plugin destination shows the enabled plugin count`() {
        val state = NavigationBadgeState(pluginEnabledCount = 3)

        val badge = badgeFor(BottomBarDestination.Plugin, state)

        assertEquals(NavBadge(3, BadgeTone.Accent), badge)
    }

    @Test
    fun `plugin destination has no badge when every plugin is disabled`() {
        val state = NavigationBadgeState(pluginEnabledCount = 0, moduleEnabledCount = 4)

        assertNull(badgeFor(BottomBarDestination.Plugin, state))
    }

    @Test
    fun `plugin count does not change the module badge`() {
        val state = NavigationBadgeState(
            pluginEnabledCount = 3,
            moduleEnabledCount = 4,
            moduleUpdatableCount = 1,
        )

        assertEquals(
            NavBadge(1, BadgeTone.Alert),
            badgeFor(BottomBarDestination.Module, state),
        )
    }
}
