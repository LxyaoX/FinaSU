package me.weishu.kernelsu.modulebanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The switch contract: the master switch and the custom-banner switch both silence every source, and
 * API mode is the only way to reach the API or a local directory.
 */
class BannerPolicyTest {
    @Test fun eitherTopSwitchSilencesEverySource() {
        for (folk in listOf(false, true)) {
            for (api in listOf(false, true)) {
                val masterOff = BannerPolicy(enabled = false, folk, api, "https://example.com/banner")
                assertFalse(masterOff.active)
                assertFalse(masterOff.localActive)
                assertFalse(masterOff.apiActive)
                assertFalse(masterOff.apiConfigured)

                val folkOff = BannerPolicy(enabled = true, folkEnabled = false, api, "https://example.com/banner")
                assertFalse(folkOff.active)
                assertFalse(folkOff.localActive)
                assertFalse(folkOff.apiActive)
            }
        }
    }

    @Test fun apiModeIsTheOnlyWayToTheApiSource() {
        val apiOff = BannerPolicy(enabled = true, folkEnabled = true, apiModeEnabled = false, "https://example.com/b")
        assertTrue(apiOff.active)
        assertTrue(apiOff.localActive)
        assertFalse(apiOff.apiActive)

        val apiOn = apiOff.copy(apiModeEnabled = true)
        assertTrue(apiOn.apiActive)
        assertTrue(apiOn.apiConfigured)

        assertFalse(apiOn.copy(apiSource = "").apiConfigured)
    }

    @Test fun turningAParentBackOnRestoresTheStoredChoices() {
        val policy = BannerPolicy(enabled = true, folkEnabled = true, apiModeEnabled = true, "https://example.com/b")
        val off = policy.copy(enabled = false)
        assertFalse(off.active)
        assertTrue(off.apiModeEnabled)
        assertTrue(off.folkEnabled)
        assertEquals(policy, off.copy(enabled = true))
        assertEquals(policy, policy.copy(folkEnabled = false).copy(folkEnabled = true))
    }

    @Test fun everyFieldParticipatesInEqualitySoProducersRestart() {
        val base = BannerPolicy(enabled = true, folkEnabled = true, apiModeEnabled = false, "https://example.com/b")
        assertEquals(base, base.copy())
        assertTrue(
            setOf(
                base.copy(enabled = false),
                base.copy(folkEnabled = false),
                base.copy(apiModeEnabled = true),
                base.copy(apiSource = "https://example.com/c"),
            ).all { it != base },
        )
    }
}
