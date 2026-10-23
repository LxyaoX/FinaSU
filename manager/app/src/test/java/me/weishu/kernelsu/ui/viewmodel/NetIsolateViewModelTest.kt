package me.weishu.kernelsu.ui.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.weishu.kernelsu.data.netisolate.NetIsolateDataSource
import me.weishu.kernelsu.data.netisolate.NetIsolatePolicy
import me.weishu.kernelsu.data.netisolate.NetIsolateStatus
import me.weishu.kernelsu.data.uts.UtsTarget
import me.weishu.kernelsu.data.uts.UtsTargetApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetIsolateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeNetIsolateDataSource()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun model() = NetIsolateViewModel(
        repository = repository,
        shellGeneration = { 1L },
        getString = { "message" },
    )

    @Test
    fun `refresh exposes kernel capability and selected uids`() = runTest(dispatcher) {
        repository.current = NetIsolateStatus(
            supported = true,
            enabled = true,
            compatSupported = true,
            strictSocketSupported = true,
            strictSocket = true,
            uids = listOf(10123),
        )

        val model = model()
        advanceUntilIdle()

        assertTrue(model.uiState.value.hasLoaded)
        assertTrue(model.uiState.value.status.enabled)
        assertTrue(model.uiState.value.status.compatSupported)
        assertTrue(model.uiState.value.status.strictSocketSupported)
        assertTrue(model.uiState.value.status.strictSocket)
        assertEquals(listOf(10123), model.uiState.value.status.uids)
    }

    @Test
    fun `enable is rejected when no uid is selected`() = runTest(dispatcher) {
        repository.current = NetIsolateStatus(supported = true, uids = emptyList())
        val model = model()
        advanceUntilIdle()

        model.setEnabled(true)
        advanceUntilIdle()

        assertEquals(0, repository.saves.size)
        assertEquals("message", model.uiState.value.message)
    }

    @Test
    fun `changing apps preserves enabled state and deduplicates uids`() = runTest(dispatcher) {
        repository.current = NetIsolateStatus(supported = true, enabled = true, uids = listOf(10123))
        val model = model()
        advanceUntilIdle()

        model.setTargets(
            listOf(
                UtsTarget("com.example.two", 10200),
                UtsTarget("com.example.one", 10100),
                UtsTarget("com.example.one.again", 10100),
            ),
        )
        advanceUntilIdle()

        assertEquals(
            listOf(
                PolicySaveCall(
                    true,
                    listOf(
                        NetIsolatePolicy(10100, false, false),
                        NetIsolatePolicy(10200, false, false),
                    ),
                ),
            ),
            repository.policySaves,
        )
        assertEquals(listOf(10100, 10200), model.uiState.value.status.uids)
    }

    @Test
    fun `changing apps preserves existing per-app policies`() = runTest(dispatcher) {
        repository.current = NetIsolateStatus(
            supported = true,
            enabled = true,
            policySupported = true,
            uids = listOf(10123),
            policies = listOf(NetIsolatePolicy(10123, false, true)),
        )
        val model = model()
        advanceUntilIdle()

        model.setTargets(listOf(UtsTarget("com.example.one", 10123), UtsTarget("com.example.two", 10200)))
        advanceUntilIdle()

        assertEquals(
            listOf(
                NetIsolatePolicy(10123, false, true),
                NetIsolatePolicy(10200, false, false),
            ),
            repository.policySaves.single().policies,
        )
    }

    @Test
    fun `single app policy update sends the complete policy set`() = runTest(dispatcher) {
        repository.current = NetIsolateStatus(
            supported = true,
            enabled = true,
            policySupported = true,
            strictSocketSupported = true,
            allowLoopbackSupported = true,
            uids = listOf(10123, 10200),
            policies = listOf(
                NetIsolatePolicy(10123, false, true),
                NetIsolatePolicy(10200, false, false),
            ),
        )
        val model = model()
        advanceUntilIdle()

        model.setPolicy(10200, strictSocket = true, allowLoopback = false)
        advanceUntilIdle()

        assertEquals(
            listOf(
                PolicySaveCall(
                    true,
                    listOf(
                        NetIsolatePolicy(10123, false, true),
                        NetIsolatePolicy(10200, true, false),
                    ),
                ),
            ),
            repository.policySaves,
        )
    }

    @Test
    fun `single app policy is rejected when per-app policies are unsupported`() = runTest(dispatcher) {
        repository.current = NetIsolateStatus(
            supported = true,
            enabled = true,
            uids = listOf(10123),
            policies = listOf(NetIsolatePolicy(10123, false, false)),
        )
        val model = model()
        advanceUntilIdle()

        model.setPolicy(10123, strictSocket = false, allowLoopback = true)
        advanceUntilIdle()

        assertTrue(repository.policySaves.isEmpty())
        assertEquals("message", model.uiState.value.message)
    }

    @Test
    fun `strict socket toggle preserves the isolation state`() = runTest(dispatcher) {
        repository.current = NetIsolateStatus(
            supported = true,
            enabled = true,
            strictSocketSupported = true,
            uids = listOf(10123),
        )
        val model = model()
        advanceUntilIdle()

        model.setStrictSocket(true)
        advanceUntilIdle()

        assertEquals(listOf(SaveCall(true, listOf(10123), true, false)), repository.saves)
        assertTrue(model.uiState.value.status.strictSocket)
        assertFalse(model.uiState.value.status.allowLoopback)
    }

    @Test
    fun `strict socket is rejected when the kernel cannot support it`() = runTest(dispatcher) {
        repository.current = NetIsolateStatus(supported = true, uids = listOf(10123))
        val model = model()
        advanceUntilIdle()

        model.setStrictSocket(true)
        advanceUntilIdle()

        assertTrue(repository.saves.isEmpty())
        assertEquals("message", model.uiState.value.message)
        assertFalse(model.uiState.value.status.strictSocket)
    }

    @Test
    fun `enabling strict socket clears loopback exemption`() = runTest(dispatcher) {
        repository.current = NetIsolateStatus(
            supported = true,
            enabled = true,
            strictSocketSupported = true,
            allowLoopbackSupported = true,
            allowLoopback = true,
            uids = listOf(10123),
        )
        val model = model()
        advanceUntilIdle()

        model.setStrictSocket(true)
        advanceUntilIdle()

        assertEquals(listOf(SaveCall(true, listOf(10123), true, false)), repository.saves)
        assertTrue(model.uiState.value.status.strictSocket)
        assertFalse(model.uiState.value.status.allowLoopback)
    }

    @Test
    fun `enabling loopback clears strict socket`() = runTest(dispatcher) {
        repository.current = NetIsolateStatus(
            supported = true,
            enabled = true,
            strictSocketSupported = true,
            strictSocket = true,
            allowLoopbackSupported = true,
            uids = listOf(10123),
        )
        val model = model()
        advanceUntilIdle()

        model.setAllowLoopback(true)
        advanceUntilIdle()

        assertEquals(listOf(SaveCall(true, listOf(10123), false, true)), repository.saves)
        assertFalse(model.uiState.value.status.strictSocket)
        assertTrue(model.uiState.value.status.allowLoopback)
    }

    @Test
    fun `loopback is rejected when the kernel cannot support it`() = runTest(dispatcher) {
        repository.current = NetIsolateStatus(supported = true, uids = listOf(10123))
        val model = model()
        advanceUntilIdle()

        model.setAllowLoopback(true)
        advanceUntilIdle()

        assertTrue(repository.saves.isEmpty())
        assertEquals("message", model.uiState.value.message)
        assertFalse(model.uiState.value.status.allowLoopback)
    }

    @Test
    fun `disabling uses the dedicated command`() = runTest(dispatcher) {
        repository.current = NetIsolateStatus(supported = true, enabled = true, uids = listOf(10123))
        val model = model()
        advanceUntilIdle()

        model.setEnabled(false)
        advanceUntilIdle()

        assertEquals(1, repository.disableCalls)
        assertTrue(repository.saves.isEmpty())
        assertFalse(model.uiState.value.status.enabled)
    }

    @Test
    fun `operation failure releases busy and shows the failure`() = runTest(dispatcher) {
        repository.current = NetIsolateStatus(supported = true, enabled = true, uids = listOf(10123))
        val model = model()
        advanceUntilIdle()

        repository.operationError = IllegalStateException("disable failed")
        model.setEnabled(false)
        advanceUntilIdle()

        assertFalse(model.uiState.value.busy)
        assertEquals("disable failed", model.uiState.value.message)
    }

    @Test
    fun `clear removes persisted selection`() = runTest(dispatcher) {
        repository.current = NetIsolateStatus(supported = true, enabled = true, uids = listOf(10123))
        val model = model()
        advanceUntilIdle()

        model.clear()
        advanceUntilIdle()

        assertEquals(1, repository.clearCalls)
        assertFalse(model.uiState.value.status.enabled)
        assertTrue(model.uiState.value.status.uids.isEmpty())
    }

    private class FakeNetIsolateDataSource : NetIsolateDataSource {
        var current = NetIsolateStatus()
        val saves = mutableListOf<SaveCall>()
        val policySaves = mutableListOf<PolicySaveCall>()
        var operationError: Throwable? = null
        var disableCalls = 0
        var clearCalls = 0

        override suspend fun status(): NetIsolateStatus = current

        override suspend fun loadApps(): List<UtsTargetApp> = emptyList()

        override suspend fun save(
            enabled: Boolean,
            uids: List<Int>,
            strictSocket: Boolean,
            allowLoopback: Boolean,
        ) {
            val normalized = uids.distinct().sorted()
            saves += SaveCall(enabled, normalized, strictSocket, allowLoopback)
            savePolicies(
                enabled,
                normalized.map { uid ->
                    NetIsolatePolicy(uid, strictSocket, allowLoopback)
                },
            )
        }

        override suspend fun savePolicies(enabled: Boolean, policies: List<NetIsolatePolicy>) {
            val normalized = policies.sortedBy(NetIsolatePolicy::uid)
            policySaves += PolicySaveCall(enabled, normalized)
            current = current.copy(
                enabled = enabled,
                uids = normalized.map(NetIsolatePolicy::uid),
                strictSocket = normalized.isNotEmpty() && normalized.all(NetIsolatePolicy::strictSocket),
                allowLoopback = normalized.isNotEmpty() && normalized.all(NetIsolatePolicy::allowLoopback),
                policies = normalized,
            )
        }

        override suspend fun disable() {
            disableCalls++
            operationError?.let { throw it }
            current = current.copy(enabled = false)
        }

        override suspend fun clear() {
            clearCalls++
            current = NetIsolateStatus(supported = true)
        }
    }

    private data class SaveCall(
        val enabled: Boolean,
        val uids: List<Int>,
        val strictSocket: Boolean,
        val allowLoopback: Boolean,
    )

    private data class PolicySaveCall(
        val enabled: Boolean,
        val policies: List<NetIsolatePolicy>,
    )
}
