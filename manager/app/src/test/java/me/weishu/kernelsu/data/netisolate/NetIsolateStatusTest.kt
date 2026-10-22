package me.weishu.kernelsu.data.netisolate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetIsolateStatusTest {
    @Test
    fun `parses the ksud status contract`() {
        val status = NetIsolateStatus.parse(
            """{"supported":true,"enabled":true,"compat_supported":true,"uids":[10123,10200]}""",
        )

        assertTrue(status.supported)
        assertTrue(status.enabled)
        assertTrue(status.compatSupported)
        assertEquals(listOf(10123, 10200), status.uids)
    }

    @Test
    fun `missing optional fields degrade safely`() {
        val status = NetIsolateStatus.parse("""{"supported":false}""")

        assertFalse(status.supported)
        assertFalse(status.enabled)
        assertFalse(status.compatSupported)
        assertTrue(status.uids.isEmpty())
    }
}
