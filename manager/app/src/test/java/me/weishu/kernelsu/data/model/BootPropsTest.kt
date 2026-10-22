package me.weishu.kernelsu.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BootPropsTest {

    private fun probe(vararg lines: String) = lines.joinToString("\n")

    /** Every tracked property already reading what the scripts would write. */
    private fun satisfiedProbe(): String {
        val lines = mutableListOf(
            "bpm:ksud=1",
            "bpm:early=1",
            "bpm:late=1",
        )
        (BootProps.early + BootProps.late).forEach { lines += "bp:${it.name}=${it.value}" }
        // One match per globbed family, the way the probe enumerates them.
        lines += "bp:ro.build.tags=release-keys"
        lines += "bp:ro.vendor.build.tags=release-keys"
        lines += "bp:ro.system.build.type=user"
        return probe(*lines.toTypedArray())
    }

    @Test
    fun `a fully corrected device reports every property applied`() {
        val status = BootProps.statusFrom(satisfiedProbe())

        assertTrue(status.enabled)
        assertTrue(status.ksudAvailable)
        assertTrue(status.allApplied)
        assertTrue(status.pending.isEmpty())
        // Nineteen literals plus the three globbed names the probe found.
        assertEquals(BootProps.names().size + 3, status.tracked)
        assertEquals(status.tracked, status.applied)
    }

    @Test
    fun `the lock state an unlocked bootloader leaks is reported as pending`() {
        val status = BootProps.statusFrom(
            probe(
                "bpm:ksud=1",
                "bpm:early=1",
                "bpm:late=1",
                "bp:ro.boot.flash.locked=0",
                "bp:ro.boot.verifiedbootstate=orange",
                // Missing entirely: resetprop printed nothing, so the probe line is empty.
                "bp:ro.boot.vbmeta.device_state=",
                "bp:ro.boot.veritymode=enforcing",
                "bp:ro.build.tags=test-keys",
            )
        )

        assertEquals(
            listOf("ro.boot.flash.locked", "ro.boot.verifiedbootstate", "ro.boot.vbmeta.device_state", "ro.build.tags"),
            status.pending,
        )
        assertEquals(5, status.tracked)
        assertEquals(1, status.applied)
        assertFalse(status.allApplied)
    }

    @Test
    fun `a property the firmware never reported is not a deviation`() {
        val status = BootProps.statusFrom(
            probe(
                "bpm:ksud=1",
                "bpm:early=1",
                "bpm:late=1",
                // Only-if-present rows: the scripts leave these alone, so their absence is the
                // state the switch promised. The vbmeta state is the exception it creates.
                "bp:ro.boot.warranty_bit=",
                "bp:vendor.boot.vbmeta.device_state=",
                "bp:ro.boot.vbmeta.device_state=",
                "bp:ro.boot.flash.locked=1",
            )
        )

        assertEquals(listOf("ro.boot.vbmeta.device_state"), status.pending)
        assertEquals(4, status.tracked)
        assertEquals(3, status.applied)
    }

    @Test
    fun `scripts count as armed only when both stages are on disk`() {
        val onlyEarly = BootProps.statusFrom(probe("bpm:ksud=1", "bpm:early=1", "bpm:late=0"))
        assertFalse(onlyEarly.enabled)

        val both = BootProps.statusFrom(probe("bpm:ksud=1", "bpm:early=1", "bpm:late=1"))
        assertTrue(both.enabled)
    }

    @Test
    fun `a kernel without ksud reports that nothing can be written`() {
        val status = BootProps.statusFrom(probe("bpm:ksud=0", "bpm:early=0", "bpm:late=0"))

        assertFalse(status.ksudAvailable)
        assertFalse(status.enabled)
        assertEquals(0, status.tracked)
    }

    @Test
    fun `the early script corrects the signature facts and never invents a property`() {
        val script = BootProps.bootScript(BootPropsStage.EARLY)

        assertTrue(script.contains("set_if_diff ro.debuggable 0"))
        assertTrue(script.contains("set_if_diff ro.secure 1"))
        assertTrue(script.contains("set_if_diff ro.adb.secure 1"))
        assertTrue(script.contains("release-keys"))
        assertTrue(script.contains("ro\\..*build\\.tags"))
        // Nothing in the early stage may create a property.
        assertFalse(script.contains("set_always ro."))
        // The lock state belongs to the late stage.
        assertFalse(script.contains("ro.boot.flash.locked"))
    }

    @Test
    fun `the late script fixes the lock state and creates the missing vbmeta state`() {
        val script = BootProps.bootScript(BootPropsStage.LATE)

        assertTrue(script.contains("set_if_diff ro.boot.flash.locked 1"))
        assertTrue(script.contains("set_if_diff ro.boot.verifiedbootstate green"))
        assertTrue(script.contains("set_always ro.boot.vbmeta.device_state locked"))
        assertTrue(script.contains("set_if_diff ro.boot.veritymode enforcing"))
        assertTrue(script.contains("set_if_diff sys.oem_unlock_allowed 0"))
        assertFalse(script.contains("build.tags"))
    }

    @Test
    fun `both scripts bail out when ksud is gone`() {
        BootPropsStage.entries.forEach { stage ->
            val script = BootProps.bootScript(stage)
            assertTrue(script.startsWith("#!/system/bin/sh"))
            assertTrue(script.contains("[ -x \"\$KSUD\" ] || exit 0"))
        }
    }

    @Test
    fun `expected values cover the globbed build properties`() {
        assertEquals("release-keys", BootProps.expected("ro.build.tags"))
        assertEquals("release-keys", BootProps.expected("ro.vendor.build.tags"))
        assertEquals("user", BootProps.expected("ro.system.build.type"))
        assertEquals("1", BootProps.expected("ro.boot.flash.locked"))
        assertNull(BootProps.expected("ro.product.model"))
        // Not a build property: the glob must not match a prefix lookalike.
        assertNull(BootProps.expected("ro.build.tags.extra"))
    }
}
