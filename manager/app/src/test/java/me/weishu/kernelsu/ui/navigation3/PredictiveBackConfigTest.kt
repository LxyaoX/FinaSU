package me.weishu.kernelsu.ui.navigation3

import androidx.navigationevent.NavigationEvent
import me.weishu.kernelsu.ui.navigation3.PredictiveBackAnimation.Aosp
import me.weishu.kernelsu.ui.navigation3.PredictiveBackAnimation.Default
import me.weishu.kernelsu.ui.navigation3.PredictiveBackAnimation.KernelSUClassic
import me.weishu.kernelsu.ui.navigation3.PredictiveBackAnimation.None
import me.weishu.kernelsu.ui.navigation3.PredictiveBackAnimation.Scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the stored tokens (they outlive a release) and the exit direction mapping. */
class PredictiveBackConfigTest {

    @Test
    fun `style tokens round trip`() {
        PredictiveBackAnimation.entries.forEach { animation ->
            assertEquals(animation, PredictiveBackAnimation.fromValue(animation.storageValue))
        }
    }

    @Test
    fun `style falls back to scale`() {
        assertEquals(Scale, PredictiveBackAnimation.fromValue(null))
        assertEquals(Scale, PredictiveBackAnimation.fromValue(""))
        assertEquals(Scale, PredictiveBackAnimation.fromValue("slidey"))
    }

    @Test
    fun `exit direction tokens round trip`() {
        PredictiveBackExitDirection.entries.forEach { direction ->
            assertEquals(direction, PredictiveBackExitDirection.fromValue(direction.storageValue))
        }
        assertEquals(
            PredictiveBackExitDirection.FollowGesture,
            PredictiveBackExitDirection.fromValue("nonsense"),
        )
    }

    @Test
    fun `only none previews nothing`() {
        assertTrue(None.previewsNothing)
        listOf(Scale, Aosp, KernelSUClassic, Default).forEach { animation ->
            assertFalse(animation.previewsNothing)
        }
    }

    @Test
    fun `follow gesture mirrors the edge it started from`() {
        val direction = PredictiveBackExitDirection.FollowGesture
        assertEquals(1f, exitSign(direction, NavigationEvent.EDGE_LEFT, rtl = false))
        assertEquals(-1f, exitSign(direction, NavigationEvent.EDGE_RIGHT, rtl = false))
        assertEquals(1f, exitSign(direction, NavigationEvent.EDGE_LEFT, rtl = true))
        assertEquals(-1f, exitSign(direction, NavigationEvent.EDGE_RIGHT, rtl = true))
    }

    @Test
    fun `plain pop follows the layout direction`() {
        val direction = PredictiveBackExitDirection.FollowGesture
        assertEquals(1f, exitSign(direction, null, rtl = false))
        assertEquals(-1f, exitSign(direction, null, rtl = true))
        // A gesture that reports no edge (a hardware back press) behaves like a plain pop.
        assertEquals(1f, exitSign(direction, NavigationEvent.EDGE_NONE, rtl = false))
        assertEquals(-1f, exitSign(direction, NavigationEvent.EDGE_NONE, rtl = true))
    }

    @Test
    fun `pinned directions ignore the gesture`() {
        assertEquals(1f, exitSign(PredictiveBackExitDirection.AlwaysRight, NavigationEvent.EDGE_RIGHT, rtl = true))
        assertEquals(-1f, exitSign(PredictiveBackExitDirection.AlwaysLeft, NavigationEvent.EDGE_LEFT, rtl = false))
    }
}
