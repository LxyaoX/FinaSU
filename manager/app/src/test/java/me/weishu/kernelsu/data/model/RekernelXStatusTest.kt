package me.weishu.kernelsu.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RekernelXStatusTest {

    private fun probe(vararg lines: String) = lines.joinToString("\n")

    @Test
    fun `a GKI release maps to the android tag and the core version`() {
        assertEquals("android14-6.1", RekernelXStatus.kmiOf("6.1.75-android14-11-gabcdef"))
        assertEquals("android15-6.6", RekernelXStatus.kmiOf("6.6.30-android15-8-g1234-ab"))
        assertEquals("android16-6.12", RekernelXStatus.kmiOf("6.12.23-android16-5-gdeadbeef"))
        assertEquals("android17-6.18", RekernelXStatus.kmiOf("6.18.3-android17-2-gfeedface"))
    }

    @Test
    fun `a release without an android tag falls back to the core version table`() {
        assertEquals("android14-6.1", RekernelXStatus.kmiOf("6.1.75"))
        assertEquals("android16-6.12", RekernelXStatus.kmiOf("6.12.30"))
    }

    @Test
    fun `a core version with no bundled build still reports its own kmi`() {
        // Not "unsupported" by itself: kmiSupported is decided by the assets, not by this parse.
        assertEquals("android12-5.10", RekernelXStatus.kmiOf("5.10.209-android12-9-gabc"))
    }

    @Test
    fun `releases that cannot name a kmi report an empty one`() {
        assertEquals("", RekernelXStatus.kmiOf("4.19.157-perf"))
        // No patch level means no core version to derive, and guessing would pick a driver at random.
        assertEquals("", RekernelXStatus.kmiOf("6.1"))
        assertEquals("", RekernelXStatus.kmiOf(""))
    }

    @Test
    fun `the probe carries the staged driver and the reason the last load failed`() {
        val status = RekernelXStatus.fromProbe(
            probe(
                "rkx:loaded=0",
                "rkx:autoload=1",
                "rkx:module=0",
                "rkx:release=6.1.75-android14-11-gabcdef",
                "rkx:driver=rekernel_x-android14-6.1-20260313.ko",
                "rkx:lasterror=insmod: unknown symbol kallsyms_lookup_name",
            )
        )

        assertFalse(status.loaded)
        // The boot script is still in place, so the switch reflects the persisted decision.
        assertTrue(status.enabled)
        assertFalse(status.externalModule)
        assertEquals("android14-6.1", status.kmi)
        assertEquals("rekernel_x-android14-6.1-20260313.ko", status.stagedDriver)
        assertEquals("insmod: unknown symbol kallsyms_lookup_name", status.lastLoadError)
    }

    @Test
    fun `a successful load reports no failure reason`() {
        val status = RekernelXStatus.fromProbe(probe("rkx:loaded=1", "rkx:autoload=1"))

        assertTrue(status.loaded)
        assertEquals("", status.lastLoadError)
        assertEquals("", status.stagedDriver)
    }

    @Test
    fun `a flashed module only owns the driver while it is enabled`() {
        assertTrue(RekernelXStatus.fromProbe(probe("rkx:module=1")).externalModule)
        // The directory outlives `disable`, which is why the probe has to read the marker.
        assertFalse(RekernelXStatus.fromProbe(probe("rkx:module=0")).externalModule)
    }
}
