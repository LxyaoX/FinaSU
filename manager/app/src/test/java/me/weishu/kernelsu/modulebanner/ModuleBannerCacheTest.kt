package me.weishu.kernelsu.modulebanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cache is what keeps a switch flip from emptying the list, and the origin recorded on an entry
 * is what keeps a cached image from surviving the switch that owns it.
 */
class ModuleBannerCacheTest {

    private val bytes = byteArrayOf(1, 2, 3)

    @Test fun anEntryIsOnlyShownWhileItsOwnSwitchIsOn() {
        val full = BannerPolicy(enabled = true, folkEnabled = true, apiModeEnabled = true, apiSource = "s")
        val api = ModuleBannerCache.Entry(ModuleBannerCache.Origin.Api, bytes = bytes)
        val picked = ModuleBannerCache.Entry(ModuleBannerCache.Origin.UserPick, bytes = bytes)
        val shipped = ModuleBannerCache.Entry(ModuleBannerCache.Origin.Module, bytes = bytes)

        assertTrue(shipped.allowedBy(full))
        assertTrue(api.allowedBy(full))
        assertTrue(picked.allowedBy(full))

        val apiModeOff = full.copy(apiModeEnabled = false)
        assertFalse(api.allowedBy(apiModeOff))
        assertTrue(picked.allowedBy(apiModeOff))
        assertTrue(shipped.allowedBy(apiModeOff))

        // Turning custom banners off silences every source, the module's own artwork included.
        val customBannerOff = full.copy(folkEnabled = false)
        assertFalse(picked.allowedBy(customBannerOff))
        assertFalse(api.allowedBy(customBannerOff))
        assertFalse(shipped.allowedBy(customBannerOff))

        val masterOff = full.copy(enabled = false)
        assertFalse(shipped.allowedBy(masterOff))
        assertFalse(api.allowedBy(masterOff))
        assertFalse(picked.allowedBy(masterOff))
    }

    @Test fun anEntryWithoutAnImageIsNeverShown() {
        val policy = BannerPolicy(enabled = true, folkEnabled = true, apiModeEnabled = true, apiSource = "s")
        assertFalse(ModuleBannerCache.Entry(ModuleBannerCache.Origin.Module).allowedBy(policy))
        assertFalse(ModuleBannerCache.Entry(ModuleBannerCache.Origin.Module, url = "").allowedBy(policy))
        assertTrue(ModuleBannerCache.Entry(ModuleBannerCache.Origin.Module, url = "https://x/y").allowedBy(policy))
    }

    @Test fun onlyAModuleEntryReadUnderTheSameReloadKeyCountsAsCached() {
        val slot = ApmBannerSlot
        ModuleBannerCache.put(slot, "cache-test-module", ModuleBannerCache.Entry(ModuleBannerCache.Origin.Module, bytes = bytes, reloadKey = 1))
        assertEquals(bytes.toList(), ModuleBannerCache.moduleEntry(slot, "cache-test-module", reloadKey = 1)?.bytes?.toList())
        assertNull(ModuleBannerCache.moduleEntry(slot, "cache-test-module", reloadKey = 2))

        ModuleBannerCache.put(slot, "cache-test-pick", ModuleBannerCache.Entry(ModuleBannerCache.Origin.UserPick, bytes = bytes))
        assertNull(ModuleBannerCache.moduleEntry(slot, "cache-test-pick", reloadKey = 1))

        ModuleBannerCache.remove(slot, "cache-test-module")
        assertNull(ModuleBannerCache.entry(slot, "cache-test-module"))
    }

    @Test fun pruningDropsModulesThatAreGoneButKeepsTheRest() {
        val slot = ApmBannerSlot
        ModuleBannerCache.put(slot, "kept", ModuleBannerCache.Entry(ModuleBannerCache.Origin.Module, bytes = bytes))
        ModuleBannerCache.put(slot, "gone", ModuleBannerCache.Entry(ModuleBannerCache.Origin.Module, bytes = bytes))
        ModuleBannerCache.prune(slot, setOf("kept"))
        assertNull(ModuleBannerCache.entry(slot, "gone"))
        assertTrue(ModuleBannerCache.entry(slot, "kept")?.hasImage == true)
        ModuleBannerCache.remove(slot, "kept")
    }
}
