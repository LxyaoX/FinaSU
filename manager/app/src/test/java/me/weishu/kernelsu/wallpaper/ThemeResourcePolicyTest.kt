package me.weishu.kernelsu.wallpaper

import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ThemeResourcePolicyTest {
    @Test fun resourcePriorityDoesNotDependOnZipEntryOrder() {
        val files = linkedMapOf("background.webp" to File("webp"), "background.png" to File("png"),
            "background.jpg" to File("jpg"), "video_background.mkv" to File("mkv"),
            "video_background.mp4" to File("mp4"), "font.otf" to File("otf"), "font.ttf" to File("ttf"))
        assertEquals(File("jpg"), ThemeResourcePolicy.select("background", files))
        assertEquals(File("mp4"), ThemeResourcePolicy.select("video_background", files))
        assertEquals(File("ttf"), ThemeResourcePolicy.select("font", files))
    }

    @Test fun theWorkCardUsesItsSwitchAndNeverRequiresAnImage() {
        val grid = SurfaceRegistry.descriptor(SurfaceRegistry.GRID_WORK_CARD)!!
        val json = JSONObject().put("isGridWorkingCardBackgroundEnabled", true)
        assertTrue(ThemeSurfaceMapping.wantsImage(grid, json, ThemeSurfaceMapping.read(grid, json), true))
        assertFalse(ThemeSurfaceMapping.wantsImage(grid, json, ThemeSurfaceMapping.read(grid, json), false))
    }

    @Test fun surfaceNumbersUseFpDefaultsRatherThanPreviousThemeValues() {
        val grid = SurfaceRegistry.descriptor(SurfaceRegistry.GRID_WORK_CARD)!!
        val parsed = ThemeSurfaceMapping.read(grid, JSONObject())
        assertFalse(parsed.enabled)
        assertEquals(1f, parsed.opacity, 0f)
        assertEquals(0.3f, parsed.dim, 0f)
        assertTrue(parsed.flags.isEmpty())
    }
}
