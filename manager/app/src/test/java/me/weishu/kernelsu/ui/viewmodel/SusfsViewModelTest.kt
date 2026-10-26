package me.weishu.kernelsu.ui.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.weishu.kernelsu.data.susfs.SusfsDataSource
import me.weishu.kernelsu.data.susfs.SusfsStatus
import me.weishu.kernelsu.ui.util.RootShellStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SusfsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSusfs()
    private var generation = 1L

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun model() = SusfsViewModel(
        repository = repository,
        rootStatus = flowOf(RootShellStatus.Ready),
        rootGeneration = flowOf(1L),
        shellGeneration = { generation },
    )

    @Test
    fun `late stale refresh cannot overwrite newer status`() = runTest(dispatcher) {
        val stale = CompletableDeferred<Unit>()
        repository.blockNextStatus = stale
        repository.statusValue = repository.statusValue.copy(management = "saved")
        val model = model()
        runCurrent()

        repository.statusValue = repository.statusValue.copy(management = "applied")
        model.refresh()
        advanceUntilIdle()
        stale.complete(Unit)
        advanceUntilIdle()

        assertEquals("applied", model.state.value.status.management)
        assertFalse(model.state.value.busy)
    }

    private class FakeSusfs : SusfsDataSource {
        var statusValue = SusfsStatus(protocol = "compatible")
        var blockNextStatus: CompletableDeferred<Unit>? = null

        override suspend fun status(): SusfsStatus {
            val snapshot = statusValue
            blockNextStatus?.let {
                blockNextStatus = null
                it.await()
            }
            return snapshot
        }

        override suspend fun export() = """{"automatic":false,"settings":{},"rules":[],"lkm":{}}"""
        override suspend fun save(text: String) = Unit

        override suspend fun apply() =
            """{"status":"applied","applied":[],"failures":{},"pending_reboot":[]}"""
    }

}
