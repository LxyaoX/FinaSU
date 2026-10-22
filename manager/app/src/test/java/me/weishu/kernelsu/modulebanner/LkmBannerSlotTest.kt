package me.weishu.kernelsu.modulebanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LkmBannerSlotTest {
    @Test
    fun usesAnIsolatedFamilyAndManagedRoot() {
        assertEquals("lkm", LkmBannerSlot.key)
        assertEquals("lkm_banners", LkmBannerSlot.bannerDirName)
        assertEquals("lkm_custom_info", LkmBannerSlot.customInfoDirName)
        assertEquals(listOf("/data/adb/ksu/lkm"), LkmBannerSlot.moduleRoots)
        assertFalse(LkmBannerSlot.supportsModulePropBanner)
    }
}
