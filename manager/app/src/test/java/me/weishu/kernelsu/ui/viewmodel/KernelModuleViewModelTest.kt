package me.weishu.kernelsu.ui.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.weishu.kernelsu.data.model.KernelModule
import me.weishu.kernelsu.data.model.KernelModuleCollection
import me.weishu.kernelsu.data.model.KernelModuleLastResult
import me.weishu.kernelsu.data.model.LoadState
import me.weishu.kernelsu.data.repository.KernelModuleRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KernelModuleViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun lateLogResponseCannotReplaceTheSelectedModule() = runTest(dispatcher) {
        val repo = Repository()
        val model = KernelModuleViewModel(repo)
        val alpha = module("alpha")
        val beta = module("beta")
        val alphaResult = CompletableDeferred<KernelModuleLastResult>()
        val betaResult = CompletableDeferred<KernelModuleLastResult>()
        repo.logResults["alpha"] = alphaResult
        repo.logResults["beta"] = betaResult

        model.openLog(alpha)
        runCurrent()
        model.openLog(beta)
        runCurrent()
        betaResult.complete(lastResult("beta"))
        advanceUntilIdle()
        assertEquals("beta", model.uiState.value.logModule?.id)
        assertEquals("beta", model.uiState.value.logResult?.operation)

        alphaResult.complete(lastResult("alpha"))
        advanceUntilIdle()
        assertEquals("beta", model.uiState.value.logModule?.id)
        assertEquals("beta", model.uiState.value.logResult?.operation)
    }

    private fun module(id: String) = KernelModule(
        id = id,
        internalName = id,
        displayName = id,
        version = "1",
        authors = emptyList(),
        description = "",
        license = "GPL",
        vermagic = "",
        architecture = "aarch64",
        depends = emptyList(),
        parameters = emptyList(),
        diagnostics = emptyList(),
        fileSize = 1,
        sha256 = "hash",
        autoload = false,
        disabled = false,
        effectiveAutoload = false,
        loaded = LoadState.NOT_LOADED,
        stateError = null,
        params = "",
        loadable = true,
        protected = false,
        error = null,
    )

    private fun lastResult(operation: String) = KernelModuleLastResult(
        bootId = "boot",
        operation = operation,
        outcome = "success",
        message = "",
    )

    private class Repository : KernelModuleRepository {
        val logResults = mutableMapOf<String, CompletableDeferred<KernelModuleLastResult>>()

        override suspend fun getModules() = Result.success(
            KernelModuleCollection(emptyList(), safeMode = false),
        )

        override suspend fun install(path: String) = Result.success(Unit)
        override suspend fun remove(id: String) = Result.success(Unit)
        override suspend fun enable(id: String) = Result.success(Unit)
        override suspend fun disable(id: String) = Result.success(Unit)
        override suspend fun setAutoload(id: String, enabled: Boolean) = Result.success(Unit)
        override suspend fun setParams(id: String, params: String) = Result.success(Unit)
        override suspend fun load(id: String) = Result.success(Unit)
        override suspend fun getLog(id: String) = Result.success(logResults.getValue(id).await())
    }
}
