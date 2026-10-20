package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fletchmckee.liquid.LiquidState
import androidx.compose.ui.platform.LocalDensity
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import me.weishu.kernelsu.ui.component.liquid.InnerShadow
import me.weishu.kernelsu.ui.component.liquid.innerShadow
import me.weishu.kernelsu.ui.component.liquid.lens
import me.weishu.kernelsu.ui.component.liquid.vibrancy
import me.weishu.kernelsu.ui.util.useFullFeaturedLayout
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens

/** Vertical space the floating bar occupies from the screen bottom, excluding system insets. */
fun floatingBarReservedHeight(compact: Boolean, style: FloatingBarConfig.Style): Dp =
    (if (style == FloatingBarConfig.Style.Drawer) 64.dp else if (compact) 68.dp else 72.dp) + 32.dp

/**
 * Floating bottom navigation bar rendered above the page content.
 *
 * Two forms ([FloatingBarConfig.Style]): [Standard][FloatingBarStandard] capsule and the
 * expanding [Drawer][FloatingBarDrawer]. With [FloatingBarConfig.glass] the surface becomes a
 * frosted-glass panel (real-time blur on Android 13+) linked to [liquidState].
 */
@Composable
internal fun FloatingBar(
    navigationBadge: NavigationBadgeState,
    liquidState: LiquidState?,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
    onUserInteraction: () -> Unit = {},
) {
    if (!useFullFeaturedLayout()) return
    val mainPagerState = LocalMainPagerState.current
    val revision by FloatingBarConfig.revision.collectAsStateWithLifecycle()
    val style = remember(revision) { FloatingBarConfig.style }
    val compact = remember(revision) { FloatingBarConfig.compact }
    val useGlass = remember(revision) { FloatingBarConfig.glass } && liquidState != null
    // Without a backdrop attached the liquid style falls back to the frosted look.
    val liquidBackdrop = backdrop?.takeIf {
        remember(revision) { FloatingBarConfig.glassStyle } == FloatingBarConfig.GlassStyle.Liquid
    }
    val effectPx = with(LocalDensity.current) { 4.dp.toPx() }
    val lensPx = with(LocalDensity.current) { 24.dp.toPx() }
    val liquidHighlight by rememberGravityRotatedHighlight(IosGlassHighlight)
    val barStyle = BottomBarControl.style(BottomBarLayout.Floating)
    val containerColor = barStyle.containerColor
    // A wallpaper selection fill fades with the surface it sits on; the liquid film has to be the
    // same colour, otherwise the lens below it is covered by an opaque pill again.
    val selectionColor = barStyle.indicatorColor
    val selectionContentColor = LocalFolkWallpaperTokens.current?.selection?.contentColor
    // The indicator samples the page, never a backdrop recorded from the row it lives in: that
    // records itself and crashes the render thread.
    val liquidIndicator = liquidBackdrop?.let {
        LiquidIndicatorGlass(
            backdrop = it,
            highlight = liquidHighlight,
            // surfaceBright is the brightest step in either polarity, so the selected item is always a
            // step up from the bar instead of a hole in it when the page behind it is dark.
            film = selectionColor ?: MaterialTheme.colorScheme.surfaceBright.copy(alpha = LIQUID_FILM_ALPHA),
            contentShadow = Shadow(
                color = if (isInDarkTheme()) {
                    Color.Black.copy(alpha = 0.45f)
                } else {
                    Color.White.copy(alpha = 0.7f)
                },
                blurRadius = 6f,
            ),
        )
    }
    val glassBlurStrength = remember(revision) { FloatingBarConfig.glassBlurStrength }
    val glassTransparency = remember(revision) { FloatingBarConfig.glassTransparency }
    val glassHighlightStrength = remember(revision) { FloatingBarConfig.glassHighlightStrength }
    val glassSpecular = remember(revision) { FloatingBarConfig.glassSpecular }
    val glassInnerGlow = remember(revision) { FloatingBarConfig.glassInnerGlow }
    val glassBorder = remember(revision) { FloatingBarConfig.glassBorder }
    val visibilityRevision by NavBarVisibilityConfig.revision.collectAsStateWithLifecycle()
    val destinations = remember(visibilityRevision) { NavBarVisibilityConfig.visibleDestinations() }
    val selectedIndex = mainPagerState.selectedPage
    // Outside wallpaper mode the pill's fill is derived from the page, so it does not carry the raised
    // neutral's own lift and a tonal tint on top would only pull it back toward the canvas it was
    // moved off of. It is given a hairline edge instead: an outline depends on nothing the theme
    // painted behind the bar, so the shape stays legible even where the step is small. Wallpaper mode
    // keeps the lift it has always had.
    val wallpaperActive = LocalFolkWallpaperTokens.current != null

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        val horizontalPadding = when {
            maxWidth > 600.dp -> 32.dp
            maxWidth > 400.dp -> 24.dp
            else -> 16.dp
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val barShape: Shape = when {
                style == FloatingBarConfig.Style.Drawer -> CircleShape
                compact -> RoundedCornerShape(percent = 50)
                useGlass -> CircleShape
                else -> MaterialTheme.shapes.large
            }
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .clip(barShape)
                    .then(
                        if (liquidBackdrop != null) {
                            Modifier
                                .drawBackdrop(
                                    backdrop = liquidBackdrop,
                                    shape = { barShape },
                                    effects = {
                                        vibrancy()
                                        blur(effectPx, effectPx)
                                        lens(
                                            refractionHeight = lensPx,
                                            refractionAmount = lensPx,
                                            depthEffect = true,
                                            chromaticAberration = 0.5f,
                                        )
                                    },
                                    highlight = { liquidHighlight.copy(alpha = 0.75f) },
                                )
                                .innerShadow(shape = barShape) {
                                    InnerShadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.15f))
                                }
                        } else if (useGlass) {
                            Modifier.navBarGlassEffect(
                                shape = barShape,
                                blurStrength = glassBlurStrength,
                                transparency = glassTransparency,
                                highlightStrength = glassHighlightStrength,
                                enableSpecular = glassSpecular,
                                enableInnerGlow = glassInnerGlow,
                                enableBorder = glassBorder,
                                liquidState = liquidState,
                            )
                        } else {
                            Modifier
                        }
                    ),
                shape = barShape,
                // The liquid look keeps a thin film over the lens; the frosted one draws its material
                // inside the glass modifier.
                color = when {
                    liquidBackdrop != null -> containerColor.copy(alpha = 0.55f)
                    useGlass -> Color.Transparent
                    else -> containerColor
                },
                // A translucent surface cannot carry a shadow: the shadow is painted behind a fill that
                // no longer hides it, so it reads as a dark halo around the capsule instead of a lift.
                tonalElevation = 0.dp,
                shadowElevation = if (useGlass || wallpaperActive) 0.dp else 8.dp,
                // A white specular rim is invisible on a bright page, so the liquid pill keeps a
                // hairline of its own to hold the edge in either polarity.
                border = when {
                    liquidBackdrop != null -> BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                    useGlass || wallpaperActive -> null
                    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
            ) {
                if (style == FloatingBarConfig.Style.Drawer) {
                    FloatingBarDrawer(
                        destinations = destinations,
                        selectedIndex = selectedIndex,
                        badge = { badgeFor(it, navigationBadge) },
                        onSelect = {
                            if (it == mainPagerState.selectedDestination) return@FloatingBarDrawer
                            onUserInteraction()
                            mainPagerState.animateToDestination(it)
                            me.weishu.kernelsu.media.MediaFeedback.navigation()
                        },
                        liquidIndicator = liquidIndicator,
                        selectionColor = selectionColor,
                        selectionContentColor = selectionContentColor,
                    )
                } else {
                    FloatingBarStandard(
                        destinations = destinations,
                        selectedIndex = selectedIndex,
                        badge = { badgeFor(it, navigationBadge) },
                        onSelect = {
                            onUserInteraction()
                            mainPagerState.animateToDestination(it)
                            me.weishu.kernelsu.media.MediaFeedback.navigation()
                        },
                        liquidIndicator = liquidIndicator,
                        selectionColor = selectionColor,
                        selectionContentColor = selectionContentColor,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FloatingBadge(badge: NavBadge) {
    when (badge.tone) {
        BadgeTone.Alert -> Badge { Text(badge.count.toString()) }
        BadgeTone.Accent -> Badge(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) { Text(badge.count.toString()) }
    }
}
