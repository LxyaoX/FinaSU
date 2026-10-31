package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Which bottom-navigation form the app is rendering. Adding a form means adding a case here and a
 * branch in [BottomBarControl], instead of patching the bar composable in place.
 */
enum class BottomBarLayout {
    /** Today's full-width bar pinned to the bottom edge. */
    Docked,

    /** Reserved for the upcoming floating bar. */
    Floating,

    /** The side navigation rail hugging the start edge. */
    Rail,
}

/**
 * The resolved bottom-bar surface.
 *
 * [containerColor] is the fill behind the bar itself. [indicatorColor] is the selected item's
 * selection pill; `null` keeps the Material default.
 */
@Immutable
data class BottomBarStyle(
    val containerColor: Color,
    val indicatorColor: Color?,
)

/**
 * The single place where the bottom bar's surface is decided, so the docked bar, the future floating
 * bar and the wide navigation rail can each keep their own responsibility while sharing one rule.
 * Same shape as the home work-card control.
 */
object BottomBarControl {
    @Composable
    fun style(layout: BottomBarLayout): BottomBarStyle = when (layout) {
        BottomBarLayout.Docked -> dockedStyle()
        BottomBarLayout.Floating -> floatingStyle()
        // The rail is the same "chrome" surface as the docked bar, only laid out along the start edge.
        BottomBarLayout.Rail -> dockedStyle()
    }

    /** The bar is a band flush with the edge, painted in the canvas colour. */
    @Composable
    private fun dockedStyle(): BottomBarStyle = BottomBarStyle(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        indicatorColor = null,
    )

    /**
     * The floating bar is a pill laid over page content rather than a band flush with the edge, so it
     * cannot borrow the docked bar's container. That container is the canvas colour
     * ([MaterialTheme.colorScheme.surfaceContainer], see MainActivity), and a pill painted in it
     * disappears into the page.
     *
     * A second named token cannot be trusted either, because the canvas is not a fixed colour: the
     * preset palettes fill the `surfaceContainer` ramp from their own table, monet and the contrast
     * levels rewrite it again, and AMOLED flattens the whole family to black. A pair of tokens that
     * happens to read well in one theme can end up level with the page in the next.
     *
     * So the pill is derived from the canvas instead: one step toward the raised end, sized by how
     * dark the page is. That keeps the theme's hue, can never land on the canvas, and settles at
     * roughly the raised neutral the cards use without depending on it. [FloatingBar] also draws the
     * pill's own hairline edge, so the step never has to carry the separation alone.
     *
     */
    @Composable
    private fun floatingStyle(): BottomBarStyle = BottomBarStyle(
        containerColor = floatingContainerColor(),
        indicatorColor = null,
    )

    /**
     * The page canvas, lifted one step. A light page has little headroom left, so it takes the larger
     * share of what remains; a dark page only needs a short lift, which is also what keeps the pill
     * from turning into a glaring light slab in dark and AMOLED themes.
     */
    @Composable
    private fun floatingContainerColor(): Color {
        val canvas = MaterialTheme.colorScheme.surfaceContainer
        val step = if (canvas.luminance() < 0.5f) DARK_CANVAS_STEP else LIGHT_CANVAS_STEP
        return lerp(canvas, Color.White, step)
    }

    /** Steps the pill takes off a dark and a light page respectively, toward [Color.White]. */
    private const val DARK_CANVAS_STEP = 0.16f
    private const val LIGHT_CANVAS_STEP = 0.62f
}
