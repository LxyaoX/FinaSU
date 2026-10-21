package me.weishu.kernelsu.ui.screen.kernelmodule

import org.junit.Assert.assertEquals
import org.junit.Test

class KernelModuleStagingTest {
    @Test
    fun keepsDifferentOriginalModuleNames() {
        assertEquals("foo.ko", sanitizeStagedModuleName("foo.ko", null))
        assertEquals("bar.ko", sanitizeStagedModuleName(null, "downloads/bar.ko"))
    }

    @Test
    fun stripsPathComponentsAndRejectsDotNames() {
        assertEquals("demo.ko", sanitizeStagedModuleName("../../demo.ko", null))
        assertEquals("demo.ko", sanitizeStagedModuleName("C:\\downloads\\demo.ko", null))
        assertEquals("kernel-module.ko", sanitizeStagedModuleName("..", null))
    }
}
