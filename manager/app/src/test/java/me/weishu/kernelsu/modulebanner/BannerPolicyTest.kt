package me.weishu.kernelsu.modulebanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerPolicyTest {
    @Test fun parentSwitchesGateEveryCombination() {
        for (enabled in listOf(false, true)) {
            for (folk in listOf(false, true)) {
                for (api in listOf(false, true)) {
                    val policy = BannerPolicy(enabled, folk, api, "https://example.com/banner")
                    assertEquals(enabled && folk, policy.active)
                    assertEquals(enabled && folk && api, policy.apiActive)
                }
            }
        }
    }

    @Test fun disablingApiKeepsLocalBannersAvailable() {
        val policy = BannerPolicy(true, true, false, "https://example.com/banner")
        assertTrue(policy.active)
        assertFalse(policy.apiActive)
    }

    @Test fun parentToggleRetainsChildChoiceWithoutBypassingTheParent() {
        val policy = BannerPolicy(true, true, true, "https://example.com/banner")
        val off = policy.copy(enabled = false)
        assertFalse(off.active)
        assertFalse(off.apiActive)
        assertTrue(off.apiModeEnabled)
        assertEquals(policy, off.copy(enabled = true))
        assertFalse(policy.copy(folkEnabled = false).apiActive)
    }
}
