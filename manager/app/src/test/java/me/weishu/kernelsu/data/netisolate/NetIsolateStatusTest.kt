package me.weishu.kernelsu.data.netisolate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetIsolateStatusTest {
    @Test
    fun `mode derives from the shared socket flags`() {
        assertEquals(NetIsolateMode.Standard, NetIsolateStatus().mode())
        assertEquals(NetIsolateMode.Loopback, NetIsolateStatus(allowLoopback = true).mode())
        assertEquals(NetIsolateMode.Strict, NetIsolateStatus(strictSocket = true).mode())
        assertEquals(
            NetIsolateMode.Strict,
            NetIsolateStatus(strictSocket = true, allowLoopback = true).mode(),
        )
    }

    @Test
    fun `mode support requires the matching kernel capability`() {
        val unsupported = NetIsolateStatus()
        assertFalse(unsupported.supportsMode(NetIsolateMode.Standard))
        assertFalse(unsupported.supportsMode(NetIsolateMode.Loopback))
        assertFalse(unsupported.supportsMode(NetIsolateMode.Strict))

        val standard = NetIsolateStatus(supported = true)
        assertTrue(standard.supportsMode(NetIsolateMode.Standard))
        assertFalse(standard.supportsMode(NetIsolateMode.Loopback))
        assertFalse(standard.supportsMode(NetIsolateMode.Strict))

        assertEquals(
            setOf(NetIsolateMode.Standard, NetIsolateMode.Loopback),
            NetIsolateStatus(supported = true, allowLoopbackSupported = true)
                .let { status -> NetIsolateMode.entries.filter(status::supportsMode).toSet() },
        )
        assertEquals(
            setOf(NetIsolateMode.Standard, NetIsolateMode.Strict),
            NetIsolateStatus(supported = true, strictSocketSupported = true)
                .let { status -> NetIsolateMode.entries.filter(status::supportsMode).toSet() },
        )
    }

    @Test
    fun `parses the ksud status contract`() {
        val status = NetIsolateStatus.parse(
            """{"supported":true,"enabled":true,"compat_supported":true,"policy_supported":true,"strict_socket_supported":true,"strict_socket":false,"allow_loopback_supported":true,"allow_loopback":false,"allow_private_network_supported":true,"allow_private_network":true,"allow_local_discovery_supported":true,"allow_local_discovery":true,"uids":[10123,10200],"policies":[{"uid":10123,"strict_socket":false,"allow_loopback":true},{"uid":10200,"strict_socket":true,"allow_loopback":false}]}""",
        )

        assertTrue(status.supported)
        assertTrue(status.enabled)
        assertTrue(status.compatSupported)
        assertTrue(status.policySupported)
        assertTrue(status.strictSocketSupported)
        assertFalse(status.strictSocket)
        assertTrue(status.allowLoopbackSupported)
        assertFalse(status.allowLoopback)
        assertTrue(status.allowPrivateNetworkSupported)
        assertTrue(status.allowPrivateNetwork)
        assertTrue(status.allowLocalDiscoverySupported)
        assertTrue(status.allowLocalDiscovery)
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
        assertFalse(status.allowPrivateNetworkSupported)
        assertFalse(status.allowPrivateNetwork)
        assertFalse(status.allowLocalDiscoverySupported)
        assertFalse(status.allowLocalDiscovery)
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

    @Test
    fun `parses blocked event diagnostics`() {
        val events = NetIsolateEvents.parse(
            """{"supported":true,"capacity":64,"dropped":2,"events":[{"timestamp_ns":123456789,"uid":10410,"operation":"connect","family":"inet","address":"127.0.0.1","port":8080},{"timestamp_ns":223456789,"uid":10200,"operation":"socket","family":"inet6","address":"","port":0}]}""",
        )

        assertTrue(events.supported)
        assertEquals(64, events.capacity)
        assertEquals(2L, events.dropped)
        assertEquals(
            listOf(
                NetIsolateEvent(123456789, 10410, "connect", "inet", "127.0.0.1", 8080),
                NetIsolateEvent(223456789, 10200, "socket", "inet6", "", 0),
            ),
            events.events,
        )
    }

    @Test
    fun `missing event diagnostics degrade safely`() {
        val events = NetIsolateEvents.parse("""{"supported":false}""")

        assertFalse(events.supported)
        assertEquals(0, events.capacity)
        assertEquals(0L, events.dropped)
        assertTrue(events.events.isEmpty())
    }
}
