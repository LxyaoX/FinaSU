package me.weishu.kernelsu.wallpaper.surface

import me.weishu.kernelsu.data.model.HomeLayoutStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceRegistryTest {
    private fun ids(layout: String, statsTop: String) =
        SurfaceRegistry.forLayout(layout, statsTop).map { it.id }

    @Test fun statsGridHeaderExposesTheGridWorkCard() {
        assertTrue(SurfaceRegistry.GRID_WORK_CARD in ids(HomeLayoutStyle.STATS, "grid"))
    }

    @Test fun statsListHeaderHidesTheGridWorkCard() {
        assertFalse(SurfaceRegistry.GRID_WORK_CARD in ids(HomeLayoutStyle.STATS, "list"))
    }

    @Test fun gridLayoutKeepsItsWorkCard() {
        assertTrue(SurfaceRegistry.GRID_WORK_CARD in ids(HomeLayoutStyle.GRID, "list"))
    }
}
