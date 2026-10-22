package me.weishu.kernelsu.data.netisolate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetIsolateStatusTest {
    @Test
    fun `parses the ksud status contract`() {
        val status = NetIsolateStatus.parse(
            """{"supported":true,"enabled":true,"compat_supported":true,"strict_socket_supported":true,"strict_socket":false,"allow_loopback_supported":true,"allow_loopback":true,"uids":[10123,10200]}""",
        )

        assertTrue(status.supported)
        assertTrue(status.enabled)
        assertTrue(status.compatSupported)
        assertTrue(status.strictSocketSupported)
        assertFalse(status.strictSocket)
        assertTrue(status.allowLoopbackSupported)
        assertTrue(status.allowLoopback)
        assertEquals(listOf(10123, 10200), status.uids)
    }

    @Test
    fun `missing optional fields degrade safely`() {
        val status = NetIsolateStatus.parse("""{"supported":false}""")

        assertFalse(status.supported)
        assertFalse(status.enabled)
        assertFalse(status.compatSupported)
        assertFalse(status.strictSocketSupported)
        assertFalse(status.strictSocket)
        assertFalse(status.allowLoopbackSupported)
        assertFalse(status.allowLoopback)
        assertTrue(status.uids.isEmpty())
    }
}
