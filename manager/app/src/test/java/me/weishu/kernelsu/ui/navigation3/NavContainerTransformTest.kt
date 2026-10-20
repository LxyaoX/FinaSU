package me.weishu.kernelsu.ui.navigation3

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class NavContainerTransformTest {
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
}
