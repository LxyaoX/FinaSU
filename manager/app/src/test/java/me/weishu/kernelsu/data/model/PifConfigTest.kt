package me.weishu.kernelsu.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PifConfigTest {
    private val upstream = """
        FINGERPRINT=google/bluejay_beta/bluejay:CANARY/ZP11.260918.007/16484274:user/release-keys
        MANUFACTURER=Google
        MODEL=Pixel 6a
        SECURITY_PATCH=2026-10-05
        DEVICE_INITIAL_SDK_INT=32
        spoofBuild=true
        spoofProps=true
        spoofProvider=false
        spoofSignature=false
        spoofVendingBuild=true
        spoofVendingSdk=false
        DEBUG=false
    """.trimIndent()

    @Test
    fun parsesTheIdentityAndTheSwitches() {
        val config = PifConfig.parse(upstream)
        assertEquals(
            "google/bluejay_beta/bluejay:CANARY/ZP11.260918.007/16484274:user/release-keys",
            config.fingerprint,
        )
        assertEquals("Google", config.manufacturer)
        assertEquals("Pixel 6a", config.model)
        assertEquals("2026-10-05", config.securityPatch)
        assertTrue(config.isOn(PifOption.SPOOF_BUILD))
        assertFalse(config.isOn(PifOption.SPOOF_PROVIDER))
        assertFalse(config.isOn(PifOption.SPOOF_VENDING_SDK))
    }

    @Test
    fun rewritesOnlyTheLineItOwns() {
        val updated = PifProp.withOption(upstream, PifOption.SPOOF_PROVIDER, true)
        assertTrue(PifConfig.parse(updated).isOn(PifOption.SPOOF_PROVIDER))
        // Keys this manager does not model have to survive the rewrite: the module reads them.
        assertTrue(updated.contains("DEVICE_INITIAL_SDK_INT=32"))
        assertTrue(updated.contains("DEBUG=false"))
        assertEquals(upstream.lines().size, updated.trimEnd('\n').lines().size)
    }

    @Test
    fun appendsASwitchTheFileDoesNotCarryYet() {
        val withoutProvider = upstream.lines()
            .filterNot { it.startsWith("spoofProvider=") }
            .joinToString("\n")
        val updated = PifProp.withOption(withoutProvider, PifOption.SPOOF_PROVIDER, true)
        assertTrue(updated.contains("spoofProvider=true"))
        assertTrue(PifConfig.parse(updated).isOn(PifOption.SPOOF_PROVIDER))
    }

    @Test
    fun probeReportsInstalledDisabledAndVersion() {
        val installed = PifStatus.fromProbe("installed=1\ndisabled=0\nversion=v4.9-inject-s", upstream)
        assertTrue(installed.installed)
        assertTrue(installed.enabled)
        assertEquals("v4.9-inject-s", installed.version)
        assertEquals("Pixel 6a", installed.config.model)

        val disabled = PifStatus.fromProbe("installed=1\ndisabled=1", upstream)
        assertTrue(disabled.installed)
        assertFalse(disabled.enabled)

        val missing = PifStatus.fromProbe("installed=0", upstream)
        assertFalse(missing.installed)
        // A stale pif.prop next to a removed module must not be presented as the identity.
        assertEquals("", missing.config.fingerprint)
    }
}
