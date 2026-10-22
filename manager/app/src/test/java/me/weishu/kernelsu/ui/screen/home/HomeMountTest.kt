package me.weishu.kernelsu.ui.screen.home

import me.weishu.kernelsu.data.model.FolkMountMode
import me.weishu.kernelsu.data.model.FolkMountProvider
import me.weishu.kernelsu.data.model.FolkMountStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeMountTest {

    @Test
    fun `boot provider wins over next provider`() {
        val status = status(
            nextProvider = FolkMountProvider.NOMOUNT,
            bootProvider = FolkMountProvider.BUILTIN,
        )

        assertEquals(FolkMountProvider.BUILTIN, resolveHomeMountProvider(status))
    }

    @Test
    fun `missing boot provider falls back to next provider`() {
        val status = status(
            nextProvider = FolkMountProvider.METAMODULE,
            bootProvider = null,
        )

        assertEquals(FolkMountProvider.METAMODULE, resolveHomeMountProvider(status))
    }

    @Test
    fun `skipped boot reports mounting off`() {
        val status = status(
            nextProvider = FolkMountProvider.BUILTIN,
            bootProvider = null,
            bootResult = "skipped",
        )

        assertEquals(FolkMountProvider.NONE, resolveHomeMountProvider(status))
    }

    @Test
    fun `missing status hides the row`() {
        assertNull(resolveHomeMountProvider(null))
    }

    private fun status(
        nextProvider: FolkMountProvider?,
        bootProvider: FolkMountProvider?,
        bootResult: String = "success",
    ) = FolkMountStatus(
        configuredMode = FolkMountMode.NOMOUNT,
        nextProvider = nextProvider,
        metamoduleId = null,
        metamoduleEnabled = false,
        bootId = "boot",
        bootProvider = bootProvider,
        bootResult = bootResult,
        targetCount = null,
        bootWarning = null,
        error = null,
    )
}
