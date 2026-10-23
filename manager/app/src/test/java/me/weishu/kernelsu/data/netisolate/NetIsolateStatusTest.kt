package me.weishu.kernelsu.data.netisolate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetIsolateStatusTest {
    @Test
    fun `parses the ksud status contract`() {
        val status = NetIsolateStatus.parse(
            """{"supported":true,"enabled":true,"compat_supported":true,"policy_supported":true,"strict_socket_supported":true,"strict_socket":false,"allow_loopback_supported":true,"allow_loopback":false,"uids":[10123,10200],"policies":[{"uid":10123,"strict_socket":false,"allow_loopback":true},{"uid":10200,"strict_socket":true,"allow_loopback":false}]}""",
        )

        assertTrue(status.supported)
        assertTrue(status.enabled)
        assertTrue(status.compatSupported)
        assertTrue(status.policySupported)
        assertTrue(status.strictSocketSupported)
        assertFalse(status.strictSocket)
        assertTrue(status.allowLoopbackSupported)
        assertFalse(status.allowLoopback)
        assertEquals(listOf(10123, 10200), status.uids)
        assertEquals(
            listOf(
                NetIsolatePolicy(10123, strictSocket = false, allowLoopback = true),
                NetIsolatePolicy(10200, strictSocket = true, allowLoopback = false),
            ),
            status.policies,
        )
    }

    @Test
    fun `missing optional fields degrade safely`() {
        val status = NetIsolateStatus.parse("""{"supported":false}""")

        assertFalse(status.supported)
        assertFalse(status.enabled)
        assertFalse(status.compatSupported)
        assertFalse(status.policySupported)
        assertFalse(status.strictSocketSupported)
        assertFalse(status.strictSocket)
        assertFalse(status.allowLoopbackSupported)
        assertFalse(status.allowLoopback)
        assertTrue(status.uids.isEmpty())
        assertTrue(status.policies.isEmpty())
    }

    @Test
    fun `legacy status expands shared flags`() {
        val status = NetIsolateStatus.parse(
            """{"supported":true,"enabled":true,"strict_socket":true,"uids":[10123]}""",
        )

        assertEquals(
            listOf(NetIsolatePolicy(10123, strictSocket = true, allowLoopback = false)),
            status.policies,
        )
    }
}
