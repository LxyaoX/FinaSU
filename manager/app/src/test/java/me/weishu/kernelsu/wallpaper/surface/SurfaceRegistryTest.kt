package me.weishu.kernelsu.wallpaper.surface

import org.junit.Assert.assertEquals
import org.junit.Test

class SurfaceRegistryTest {
    @Test fun theStatusCardSurfaceIsTheOnlyOneOffered() {
        // The home page draws one layout, so the surfaces that skinned the deleted ones are gone and
        // the work card - which paints the status card still on the page - is what remains.
        assertEquals(listOf(SurfaceRegistry.GRID_WORK_CARD), SurfaceRegistry.forLayout().map { it.id })
    }

    @Test fun theWorkCardSurfaceResolves() {
        assertEquals(SurfaceRegistry.GRID_WORK_CARD, SurfaceRegistry.descriptor(SurfaceRegistry.GRID_WORK_CARD)?.id)
    }
}
