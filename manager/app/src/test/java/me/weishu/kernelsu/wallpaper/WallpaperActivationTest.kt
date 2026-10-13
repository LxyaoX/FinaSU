package me.weishu.kernelsu.wallpaper

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WallpaperActivationTest {
    @Before fun setUp() = WallpaperConfig.reset()
    @After fun tearDown() = WallpaperConfig.reset()

    @Test fun masterOffKeepsRememberedPageImagesInactive() {
        WallpaperConfig.updateModuleBackgroundUri("file:///module.png")
        WallpaperConfig.updatePluginBackgroundUri("file:///plugin.png")
        WallpaperConfig.updateMultiBackgroundEnabled(true)
        assertFalse(WallpaperConfig.isPageActive(WallpaperConfig.PAGE_MODULE))
        assertFalse(WallpaperConfig.isPageActive(WallpaperConfig.PAGE_PLUGIN))
    }

    @Test fun anotherPagesImageDoesNotMakeModuleOrPluginTransparent() {
        WallpaperConfig.updateEnabled(true)
        WallpaperConfig.updateMultiBackgroundEnabled(true)
        WallpaperConfig.updateHomeBackgroundUri("file:///home.png")
        assertTrue(WallpaperConfig.isPageActive(WallpaperConfig.PAGE_HOME))
        assertFalse(WallpaperConfig.isPageActive(WallpaperConfig.PAGE_MODULE))
        assertFalse(WallpaperConfig.isPageActive(WallpaperConfig.PAGE_PLUGIN))
        WallpaperConfig.updatePluginBackgroundUri("file:///plugin.png")
        assertTrue(WallpaperConfig.isPageActive(WallpaperConfig.PAGE_PLUGIN))
        WallpaperConfig.updatePluginBackgroundUri(null)
        assertFalse(WallpaperConfig.isPageActive(WallpaperConfig.PAGE_PLUGIN))
    }

    @Test fun singleModeIgnoresRememberedPageImages() {
        WallpaperConfig.updateEnabled(true)
        WallpaperConfig.updateModuleBackgroundUri("file:///module.png")
        assertFalse(WallpaperConfig.isPageActive(WallpaperConfig.PAGE_MODULE))
        WallpaperConfig.updateUri("file:///global.png")
        assertTrue(WallpaperConfig.isPageActive(WallpaperConfig.PAGE_MODULE))
        assertTrue(WallpaperConfig.isPageActive(WallpaperConfig.PAGE_PLUGIN))
    }

    @Test fun completedPickDoesNotUndoMasterOff() {
        WallpaperConfig.updateEnabled(true)
        val revision = WallpaperConfig.switchRevision
        WallpaperConfig.updateEnabled(false)
        WallpaperConfig.activatePickedImage(revision, multi = true)
        assertFalse(WallpaperConfig.enabled)
        assertFalse(WallpaperConfig.multiBackgroundEnabled)
    }

    @Test fun completedPickDoesNotUndoModeChangeOrReset() {
        val revision = WallpaperConfig.switchRevision
        WallpaperConfig.updateMultiBackgroundEnabled(false)
        WallpaperConfig.activatePickedImage(revision, multi = true)
        assertFalse(WallpaperConfig.multiBackgroundEnabled)
        val nextRevision = WallpaperConfig.switchRevision
        WallpaperConfig.reset()
        WallpaperConfig.activatePickedImage(nextRevision)
        assertFalse(WallpaperConfig.enabled)
    }

    @Test fun freshPickActivatesAndVideoRemainsIndependent() {
        WallpaperConfig.activatePickedImage(WallpaperConfig.switchRevision, multi = true)
        assertTrue(WallpaperConfig.enabled)
        assertTrue(WallpaperConfig.multiBackgroundEnabled)
        WallpaperConfig.updateEnabled(false)
        assertTrue(WallpaperConfig.isPageActive(WallpaperConfig.PAGE_MODULE, videoActive = true))
        assertFalse(WallpaperConfig.isPageActive(WallpaperConfig.PAGE_MODULE, videoActive = false))
    }
}
