package me.weishu.kernelsu.data.netisolate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetIsolateStatusTest {
    @Test
    fun `parses the ksud status contract`() {
        val status = NetIsolateStatus.parse(
            """{"supported":true,"enabled":true,"compat_supported":true,"strict_socket_supported":true,"strict_socket":true,"uids":[10123,10200]}""",
        )

        assertTrue(status.supported)
        assertTrue(status.enabled)
        assertTrue(status.compatSupported)
        assertTrue(status.strictSocketSupported)
        assertTrue(status.strictSocket)
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
        assertTrue(status.uids.isEmpty())
    }
}
