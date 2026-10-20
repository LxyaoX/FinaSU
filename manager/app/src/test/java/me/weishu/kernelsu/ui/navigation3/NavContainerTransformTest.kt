package me.weishu.kernelsu.ui.navigation3

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavContainerTransformTest {
    @Test
    fun `committed transition starts from the last drawn fraction`() {
        val state = NavOriginState()
        val key = "screen"

        state.updatePredictive(active = true, key = key, fraction = 0.15f)
        state.markPredictiveCommit()
        state.updatePredictive(active = false, key = null, fraction = 1f)

        val startFraction = state.committedFraction(key)
        val animation = containerTransformAnimation(startFraction ?: 1f, 0f)

        assertEquals(0.15f, startFraction ?: -1f, 0.001f)
        assertEquals(0.15f, animation.startFraction, 0.001f)
        assertEquals(0f, animation.targetFraction, 0.001f)
    }

    @Test
    fun `cancelled transition returns from the last drawn fraction to full screen`() {
        val state = NavOriginState()
        val key = "screen"

        state.updatePredictive(active = true, key = key, fraction = 0.15f)
        state.updatePredictive(active = false, key = null, fraction = 1f)

        val startFraction = state.lastPredictiveFraction(key)
        val animation = containerTransformAnimation(startFraction ?: 1f, 1f)

        assertEquals(0.15f, startFraction ?: -1f, 0.001f)
        assertEquals(0.15f, animation.startFraction, 0.001f)
        assertEquals(1f, animation.targetFraction, 0.001f)
    }

    @Test
    fun `fast commit keeps a tiny start and never passes through full screen`() {
        val state = NavOriginState()
        val key = "screen"

        state.updatePredictive(active = true, key = key, fraction = 0.02f)
        state.markPredictiveCommit()
        state.updatePredictive(active = false, key = null, fraction = 1f)

        val animation = containerTransformAnimation(
            startFraction = state.committedFraction(key) ?: 1f,
            targetFraction = 0f,
        )

        assertEquals(0.02f, animation.startFraction, 0.001f)
        assertEquals(6, animation.durationMillis)
        assertEquals(0.02f, state.predictiveDrawFraction(key) ?: 1f, 0.001f)
        state.beginPredictiveSettle(key)
        assertNull(state.predictiveDrawFraction(key))
        assertTrue(animation.startFraction < 1f)
        assertTrue(animation.targetFraction < 1f)
    }

    @Test
    fun `matching theme keeps the captured fill and snapshot`() {
        val capturedColor = Color(0xFF243447)
        val liveSurface = Color(0xFFF0F4FF)

        val appearance = resolveContainerAppearance(
            originColor = capturedColor,
            originThemeSignature = 42,
            currentThemeSignature = 42,
            surfaceColor = liveSurface,
            popping = true,
            hasSnapshot = true,
            fraction = 0.28f,
        )

        assertEquals(capturedColor, appearance.fillColor)
        assertTrue(appearance.snapshotEnabled)
        assertTrue(appearance.snapshotAlpha > 0f)
    }

    @Test
    fun `changed theme uses the live surface fill`() {
        val liveSurface = Color(0xFF111318)

        val appearance = resolveContainerAppearance(
            originColor = Color.White,
            originThemeSignature = 42,
            currentThemeSignature = 43,
            surfaceColor = liveSurface,
            popping = true,
            hasSnapshot = false,
            fraction = 0.28f,
        )

        assertEquals(liveSurface, appearance.fillColor)
        assertFalse(appearance.snapshotEnabled)
        assertEquals(0f, appearance.snapshotAlpha, 0.001f)
    }

    @Test
    fun `changed theme discards a captured snapshot`() {
        val liveSurface = Color(0xFF111318)

        val appearance = resolveContainerAppearance(
            originColor = Color.White,
            originThemeSignature = 42,
            currentThemeSignature = 43,
            surfaceColor = liveSurface,
            popping = true,
            hasSnapshot = true,
            fraction = 0.28f,
        )

        assertEquals(liveSurface, appearance.fillColor)
        assertFalse(appearance.snapshotEnabled)
        assertEquals(0f, appearance.snapshotAlpha, 0.001f)
    }

    @Test
    fun `theme signature follows the surface palette roles`() {
        val scheme = lightColorScheme(
            background = Color.White,
            surface = Color(0xFFF9F9F9),
            surfaceContainer = Color(0xFFF0F0F0),
            surfaceBright = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFDDDDDD),
        )

        assertNotEquals(
            navThemeSignature(scheme),
            navThemeSignature(scheme.copy(surfaceBright = Color.Black)),
        )
    }

    @Test
    fun `container bounds use the real source and destination rectangles`() {
        val origin = Rect(24f, 180f, 264f, 420f)
        val destination = Rect(12f, 48f, 1044f, 2304f)

        assertEquals(origin, interpolateContainerBounds(origin, destination, 0f))
        assertEquals(destination, interpolateContainerBounds(origin, destination, 1f))

        val halfway = interpolateContainerBounds(origin, destination, 0.5f)
        assertEquals(18f, halfway.left, 0.001f)
        assertEquals(114f, halfway.top, 0.001f)
        assertEquals(654f, halfway.right, 0.001f)
        assertEquals(1362f, halfway.bottom, 0.001f)
    }

    @Test
    fun `container bounds clamp progress at both ends`() {
        val origin = Rect(20f, 30f, 120f, 180f)
        val destination = Rect(0f, 0f, 1080f, 2400f)

        assertEquals(origin, interpolateContainerBounds(origin, destination, -1f))
        assertEquals(destination, interpolateContainerBounds(origin, destination, 2f))
    }

    @Test
    fun `container corners settle with the bounds`() {
        assertEquals(36f, interpolateContainerCorner(36f, 0f), 0.001f)
        assertEquals(18f, interpolateContainerCorner(36f, 0.5f), 0.001f)
        assertEquals(0f, interpolateContainerCorner(36f, 1f), 0.001f)
    }

    @Test
    fun `push veil starts opaque and clears before the page settles`() {
        assertEquals(1f, pushSurfaceVeilAlpha(0f), 0.001f)
        assertEquals(1f, pushSurfaceVeilAlpha(0.08f), 0.001f)
        assertEquals(0f, pushSurfaceVeilAlpha(0.42f), 0.001f)
        assertEquals(0f, pushSurfaceVeilAlpha(1f), 0.001f)
    }

    @Test
    fun `pop veil returns to opaque as the page reaches its origin`() {
        assertEquals(0f, popSurfaceVeilAlpha(1f), 0.001f)
        assertEquals(0f, popSurfaceVeilAlpha(0.42f), 0.001f)
        assertEquals(1f, popSurfaceVeilAlpha(0.08f), 0.001f)
        assertEquals(1f, popSurfaceVeilAlpha(0f), 0.001f)
    }

    @Test
    fun `origin snapshot takes over as the page reaches its source`() {
        assertEquals(0f, popSnapshotAlpha(1f), 0.001f)
        assertEquals(0f, popSnapshotAlpha(0.42f), 0.001f)
        assertTrue(popSnapshotAlpha(0.28f) in 0f..1f)
        assertTrue(popSnapshotAlpha(0.28f) > 0f)
        assertTrue(popSnapshotAlpha(0.28f) < 1f)
        assertEquals(1f, popSnapshotAlpha(0.14f), 0.001f)
        assertEquals(1f, popSnapshotAlpha(0f), 0.001f)
    }
}
