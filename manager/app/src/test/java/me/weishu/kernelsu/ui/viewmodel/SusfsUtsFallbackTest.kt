package me.weishu.kernelsu.ui.viewmodel

import kotlinx.coroutines.test.runTest
import me.weishu.kernelsu.data.susfs.SusfsDataSource
import me.weishu.kernelsu.data.susfs.SusfsStatus
import me.weishu.kernelsu.data.uts.UtsDataSource
import me.weishu.kernelsu.data.uts.UtsFields
import me.weishu.kernelsu.data.uts.UtsTargetApp
import me.weishu.kernelsu.data.uts.UtsViewMode
import me.weishu.kernelsu.data.uts.UtsViewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SusfsUtsFallbackTest {
    @Test
    fun `restored default uname does not require a provider switch`() {
        val restored = """{"settings":{"uname":{"release":"default","version":"default"}}}"""
        val spoofed = """{"settings":{"uname":{"release":"6.6.118","version":"#1"}}}"""

        assertFalse(susfsUnameSpoofs(restored))
        assertTrue(susfsUnameSpoofs(spoofed))
    }

    @Test
    fun `confirmed uname keeps UTS disabled without clearing its template`() = runTest {
        val uts = FakeUts()
        val susfs = FakeSusfs(
            applyResult = """{"status":"applied","applied":["uname"],"failures":{},"pending_reboot":[]}""",
            statusAfterApply = status(applied = true),
        )

        applySusfsWithUtsFallback(susfs, uts)

        assertEquals(1, uts.disableCalls)
        assertEquals(0, uts.enableCalls)
        assertEquals(0, uts.clearCalls)
        assertFalse(uts.current.active)
        assertEquals(4, uts.current.fieldMask)
    }

    @Test
    fun `already applied uname is confirmed from authoritative status`() = runTest {
        val uts = FakeUts()
        val susfs = FakeSusfs(
            applyResult = """{"status":"applied","applied":[],"failures":{},"pending_reboot":[]}""",
            statusAfterApply = status(applied = true),
        )

        applySusfsWithUtsFallback(susfs, uts)

        assertEquals(0, uts.enableCalls)
        assertEquals(0, uts.clearCalls)
        assertFalse(uts.current.active)
    }

    @Test
    fun `failed uname restores UTS and preserves its configuration`() = runTest {
        val uts = FakeUts()
        val susfs = FakeSusfs(
            applyResult = """{"status":"failed","applied":[],"failures":{"uname":"kernel rejected uname"},"pending_reboot":[]}""",
            statusAfterApply = status(applied = false),
        )

        val error = runCatching { applySusfsWithUtsFallback(susfs, uts) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error?.message.orEmpty().contains("UTS View was restored"))
        assertEquals(1, uts.disableCalls)
        assertEquals(1, uts.enableCalls)
        assertEquals(0, uts.clearCalls)
        assertTrue(uts.current.active)
    }

    @Test
    fun `apply exception also restores UTS`() = runTest {
        val uts = FakeUts()
        val susfs = FakeSusfs(applyError = IllegalStateException("apply timed out"))

        val error = runCatching { applySusfsWithUtsFallback(susfs, uts) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error?.message.orEmpty().contains("apply timed out"))
        assertEquals(1, uts.enableCalls)
        assertEquals(0, uts.clearCalls)
        assertTrue(uts.current.active)
    }

    @Test
    fun `apply exception keeps UTS disabled when authoritative status is applied`() = runTest {
        val uts = FakeUts()
        val susfs = FakeSusfs(
            applyError = IllegalStateException("response lost"),
            statusAfterApply = status(applied = true),
        )

        val error = runCatching { applySusfsWithUtsFallback(susfs, uts) }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("UTS View remains disabled"))
        assertEquals(0, uts.enableCalls)
        assertEquals(0, uts.clearCalls)
        assertFalse(uts.current.active)
    }

    @Test
    fun `unknown uname outcome never restores UTS`() = runTest {
        val uts = FakeUts()
        val susfs = FakeSusfs(
            applyResult = """{"status":"failed","applied":[],"failures":{},"pending_reboot":[]}""",
            statusAfterApply = status(applied = false, uncertain = true),
        )

        val error = runCatching { applySusfsWithUtsFallback(susfs, uts) }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("result is unknown"))
        assertEquals(0, uts.enableCalls)
        assertFalse(uts.current.active)
    }

    @Test
    fun `status failure after apply is unknown and keeps UTS disabled`() = runTest {
        val uts = FakeUts()
        val susfs = FakeSusfs(
            applyResult = """{"status":"saved","applied":[],"failures":{},"pending_reboot":[]}""",
            statusError = IllegalStateException("status unavailable"),
        )

        val error = runCatching { applySusfsWithUtsFallback(susfs, uts) }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("result is unknown"))
        assertEquals(0, uts.enableCalls)
        assertFalse(uts.current.active)
    }

    @Test
    fun `known failure still restores UTS`() = runTest {
        val uts = FakeUts()
        val susfs = FakeSusfs(
            applyResult = """{"status":"failed","applied":[],"failures":{"uname":"rejected"},"pending_reboot":[]}""",
            statusError = IllegalStateException("status unavailable"),
        )

        val error = runCatching { applySusfsWithUtsFallback(susfs, uts) }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("UTS View was restored"))
        assertEquals(1, uts.enableCalls)
        assertTrue(uts.current.active)
    }

    private fun status(applied: Boolean, uncertain: Boolean = false) = SusfsStatus(
        protocol = "compatible",
        unameApplied = applied,
        unameUncertain = uncertain,
    )

    private class FakeUts : UtsDataSource {
        var current = UtsViewStatus(
            supported = true,
            enabled = true,
            active = true,
            fieldMask = 4,
            fields = UtsFields(release = "spoofed", mask = 4),
            source = "uts",
        )
        var disableCalls = 0
        var enableCalls = 0
        var clearCalls = 0

        override suspend fun status() = current
        override suspend fun loadApps(): List<UtsTargetApp> = emptyList()
        override suspend fun save(
            fields: UtsFields,
            enabled: Boolean,
            mode: UtsViewMode,
            targets: List<me.weishu.kernelsu.data.uts.UtsTarget>,
        ) = Unit

        override suspend fun enable() {
            enableCalls++
            current = current.copy(enabled = true, active = true, source = "uts")
        }

        override suspend fun disable() {
            disableCalls++
            current = current.copy(enabled = false, active = false, source = "none")
        }

        override suspend fun clear() {
            clearCalls++
            current = UtsViewStatus()
        }

        override suspend fun takeOver(
            fields: UtsFields,
            mode: UtsViewMode,
            targets: List<me.weishu.kernelsu.data.uts.UtsTarget>,
        ) = Unit
    }

    private class FakeSusfs(
        private val applyResult: String = """{"status":"saved","applied":[],"failures":{},"pending_reboot":[]}""",
        private val statusAfterApply: SusfsStatus = SusfsStatus(protocol = "compatible"),
        private val statusError: Throwable? = null,
        private val applyError: Throwable? = null,
    ) : SusfsDataSource {
        override suspend fun status(): SusfsStatus {
            statusError?.let { throw it }
            return statusAfterApply
        }
        override suspend fun export() = "{}"
        override suspend fun save(text: String) = Unit
        override suspend fun apply(): String {
            applyError?.let { throw it }
            return applyResult
        }
    }
}
