package me.weishu.kernelsu.ui.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.weishu.kernelsu.data.model.SuiApp
import me.weishu.kernelsu.data.model.SuiMode
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.data.sui.SuiAdbRootMode
import me.weishu.kernelsu.data.sui.SuiBridge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuiPermissionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val apps = listOf(
        app(uid = 10001, packageName = "com.example.alpha", label = "Alpha", mode = SuiApp.MODE_ROOT),
        app(
            uid = 10002,
            packageName = "com.example.beta",
            label = "Beta",
            mode = SuiApp.MODE_DEFAULT,
            requestsShizuku = true,
        ),
        app(uid = 10003, packageName = "com.example.gamma", label = "Gamma", mode = SuiApp.MODE_DENY),
    )

    private fun model() = SuiPermissionsViewModel(repo)

    @Test fun storedWordsMapToModesAndUnknownWordsReadAsUnconfigured() {
        assertEquals(SuiMode.DEFAULT, SuiMode.of(SuiApp.MODE_DEFAULT))
        assertEquals(SuiMode.ROOT, SuiMode.of(SuiApp.MODE_ROOT))
        assertEquals(SuiMode.SHELL, SuiMode.of(SuiApp.MODE_SHELL))
        assertEquals(SuiMode.DENY, SuiMode.of(SuiApp.MODE_DENY))
        assertEquals(SuiMode.HIDDEN, SuiMode.of(SuiApp.MODE_HIDE))
        assertEquals(SuiMode.DEFAULT, SuiMode.of(SuiApp.MODE_ROOT or SuiApp.MODE_SHELL))
    }

    @Test fun adbRootModeReadsTheTwoBits() {
        assertEquals(SuiAdbRootMode.OFF, SuiAdbRootMode.of(0))
        assertEquals(SuiAdbRootMode.ONCE, SuiAdbRootMode.of(SuiBridge.FLAG_ADB_ROOT_ONCE))
        assertEquals(SuiAdbRootMode.ALWAYS, SuiAdbRootMode.of(SuiBridge.FLAG_ADB_ROOT_ALWAYS))
    }

    @Test fun shizukuFilterKeepsTheAppsSuiItselfLists() {
        // Alpha is allowed, Beta only asks for Shizuku, Gamma has a denying rule.
        assertEquals(listOf(10001, 10002), visibleSuiApps(apps, onlyShizukuApps = true).map { it.uid })
        assertEquals(listOf(10001, 10002, 10003), visibleSuiApps(apps, onlyShizukuApps = false).map { it.uid })
    }

    @Test fun searchMatchesLabelAndPackageIgnoringCase() {
        assertEquals(listOf(10001), searchSuiApps(apps, "alph").map { it.uid })
        assertEquals(listOf(10003), searchSuiApps(apps, "EXAMPLE.GAMMA").map { it.uid })
        assertEquals(3, searchSuiApps(apps, "  ").size)
        assertTrue(searchSuiApps(apps, "nothing-matches").isEmpty())
    }

    @Test fun loadReadsAppsDefaultPermissionSettingsAndHiddenCount() = runTest(dispatcher) {
        repo.apps = apps
        repo.defaultMode = SuiApp.MODE_SHELL
        repo.hiddenUids = listOf(10009, 10010)
        repo.settingsFlags = SuiBridge.FLAG_KSU_NO_ESCAPE
        val model = model()
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(listOf(10001, 10002, 10003), state.visible.map { it.uid })
        assertEquals(SuiMode.SHELL, state.defaultMode)
        assertEquals(2, state.hiddenCount)
        assertTrue(state.ksuNoEscape)
        assertFalse(state.legacyBinderCompat)
        assertEquals(SuiAdbRootMode.OFF, state.adbRootMode)
        assertEquals(false, state.loading)
        assertNull(state.error)
    }

    @Test fun filterAndSearchNarrowTheSameListTheScreenDraws() = runTest(dispatcher) {
        repo.apps = apps
        val model = model()
        advanceUntilIdle()

        model.setShowOnlyShizukuApps(true)
        advanceUntilIdle()
        assertEquals(listOf(10001, 10002), model.uiState.value.visible.map { it.uid })

        model.setSearch("beta")
        assertEquals(listOf(10002), model.uiState.value.searchResults.map { it.uid })
        assertEquals(listOf(10001, 10002), model.uiState.value.visible.map { it.uid })
    }

    @Test fun togglingASettingKeepsTheUnrelatedBits() = runTest(dispatcher) {
        repo.apps = apps
        repo.settingsFlags = SuiBridge.FLAG_LEGACY_SHIZUKU_BINDER_COMPAT
        val model = model()
        advanceUntilIdle()

        model.setKsuNoEscape(true)
        advanceUntilIdle()
        assertEquals(
            SuiBridge.FLAG_LEGACY_SHIZUKU_BINDER_COMPAT or SuiBridge.FLAG_KSU_NO_ESCAPE,
            repo.settingsWrites.single(),
        )
        assertEquals(SuiNotice.REBOOT_REQUIRED, model.uiState.value.notice)

        model.consumeNotice()
        model.setLegacyBinderCompat(false)
        advanceUntilIdle()
        assertEquals(SuiBridge.FLAG_KSU_NO_ESCAPE, repo.settingsWrites.last())
    }

    @Test fun adbRootModeReplacesBothBits() = runTest(dispatcher) {
        repo.apps = apps
        repo.settingsFlags = SuiBridge.FLAG_ADB_ROOT_ONCE or SuiBridge.FLAG_SHOW_ONLY_SHIZUKU_APPS
        val model = model()
        advanceUntilIdle()
        assertEquals(SuiAdbRootMode.ONCE, model.uiState.value.adbRootMode)

        model.setAdbRootMode(SuiAdbRootMode.ALWAYS)
        advanceUntilIdle()
        assertEquals(SuiBridge.FLAG_ADB_ROOT_ALWAYS or SuiBridge.FLAG_SHOW_ONLY_SHIZUKU_APPS, repo.settingsWrites.single())
        assertEquals(SuiAdbRootMode.ALWAYS, model.uiState.value.adbRootMode)

        model.setAdbRootMode(SuiAdbRootMode.OFF)
        advanceUntilIdle()
        assertEquals(SuiBridge.FLAG_SHOW_ONLY_SHIZUKU_APPS, repo.settingsWrites.last())
    }

    @Test fun failedSettingWriteRollsBackAndReportsTheError() = runTest(dispatcher) {
        repo.apps = apps
        repo.settingsFlags = SuiBridge.FLAG_LEGACY_SHIZUKU_BINDER_COMPAT
        val model = model()
        advanceUntilIdle()

        repo.settingsWriteResult = Result.failure(IllegalStateException("Sui said no"))
        model.setKsuNoEscape(true)
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.ksuNoEscape)
        assertEquals("Sui said no", state.error)
        assertNull(state.notice)
    }

    @Test fun changingOneAppWritesTheModeAndUpdatesEveryList() = runTest(dispatcher) {
        repo.apps = apps
        val model = model()
        advanceUntilIdle()

        model.setMode(apps[1], SuiMode.HIDDEN)
        advanceUntilIdle()

        assertEquals(10002 to SuiApp.MODE_HIDE, repo.writes.single())
        assertEquals(SuiMode.HIDDEN, model.uiState.value.visible.first { it.uid == 10002 }.suiMode)
        assertEquals(false, model.uiState.value.busy)
    }

    @Test fun failedWriteKeepsTheStoredModeAndReportsTheError() = runTest(dispatcher) {
        repo.apps = apps
        val model = model()
        advanceUntilIdle()

        repo.writeResult = Result.failure(IllegalStateException("Sui said no"))
        model.setMode(apps[0], SuiMode.DENY)
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(SuiMode.ROOT, state.visible.first { it.uid == 10001 }.suiMode)
        assertEquals("Sui said no", state.error)
        assertEquals(false, state.busy)
    }

    @Test fun defaultPermissionIsWrittenAsItsModeWord() = runTest(dispatcher) {
        repo.apps = apps
        val model = model()
        advanceUntilIdle()

        model.setDefaultMode(SuiMode.DENY)
        advanceUntilIdle()
        assertEquals(SuiApp.MODE_DENY, repo.defaultWrites.single())
        assertEquals(SuiMode.DENY, model.uiState.value.defaultMode)

        repo.defaultWriteResult = Result.failure(IllegalStateException("rejected"))
        model.setDefaultMode(SuiMode.ROOT)
        advanceUntilIdle()
        assertEquals(SuiMode.DENY, model.uiState.value.defaultMode)
        assertEquals("rejected", model.uiState.value.error)
    }

    private fun app(
        uid: Int,
        packageName: String,
        label: String,
        mode: Int,
        requestsShizuku: Boolean = false,
    ) = SuiApp(
        uid = uid,
        packageName = packageName,
        label = label,
        mode = mode,
        requestsShizuku = requestsShizuku,
    )

    /** Records the management calls the screen makes; everything else needs a device. */
    private class FakeRepository : SettingsRepository by SettingsRepositoryImpl() {
        var apps: List<SuiApp> = emptyList()
        var defaultMode: Int = SuiApp.MODE_DEFAULT
        var settingsFlags: Int = 0
        var hiddenUids: List<Int> = emptyList()
        var writeResult: Result<Unit> = Result.success(Unit)
        var defaultWriteResult: Result<Unit> = Result.success(Unit)
        var settingsWriteResult: Result<Unit> = Result.success(Unit)
        val writes = mutableListOf<Pair<Int, Int>>()
        val defaultWrites = mutableListOf<Int>()
        val settingsWrites = mutableListOf<Int>()

        override suspend fun suiAppPermissions(): Result<List<SuiApp>> = Result.success(apps)

        override suspend fun suiDefaultMode(): Result<Int> = Result.success(defaultMode)

        override suspend fun suiSettingsFlags(): Result<Int> = Result.success(settingsFlags)

        override suspend fun suiHiddenUids(): Result<List<Int>> = Result.success(hiddenUids)

        override suspend fun setSuiAppMode(uid: Int, mode: Int): Result<Unit> {
            writes += uid to mode
            return writeResult
        }

        override suspend fun setSuiDefaultMode(mode: Int): Result<Unit> {
            defaultWrites += mode
            return defaultWriteResult
        }

        override suspend fun setSuiSettingsFlags(flags: Int): Result<Unit> {
            settingsWrites += flags
            if (settingsWriteResult.isSuccess) settingsFlags = flags
            return settingsWriteResult
        }
    }
}
