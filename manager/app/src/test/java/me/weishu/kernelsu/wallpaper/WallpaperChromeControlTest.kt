package me.weishu.kernelsu.wallpaper

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the fill rule for a control sitting on the chrome platform, such as the top bar's back
 * button: it must stay translucent while the wallpaper is visible, and it must keep the tone the
 * caller asked for.
 */
class WallpaperChromeControlTest {
    private val scheme = lightColorScheme()
    private val tint = Color(0xFFE6E0E9)

    @Test
    fun controlFillTracksTheWallpaperOpacity() {
        val clear = resolveFolkWallpaperTokens(scheme, 0f).chromeControlFill(tint)
        val half = resolveFolkWallpaperTokens(scheme, 0.5f).chromeControlFill(tint)
        val solid = resolveFolkWallpaperTokens(scheme, 1f).chromeControlFill(tint)

        assertEquals(0.10f, clear.alpha, 0.005f)
        assertEquals(0.55f, half.alpha, 0.005f)
        assertEquals(1f, solid.alpha, 0.005f)
        assertTrue(clear.alpha < half.alpha)
        assertTrue(half.alpha < solid.alpha)
    }

    @Test
    fun controlFillKeepsTheRequestedTone() {
        val fill = resolveFolkWallpaperTokens(scheme, 0.4f).chromeControlFill(tint)

        assertEquals(tint, fill.copy(alpha = 1f))
    }
}
