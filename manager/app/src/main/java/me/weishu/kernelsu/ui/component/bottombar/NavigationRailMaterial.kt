package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.component.material.ChromeEdge
import me.weishu.kernelsu.ui.component.material.WallpaperChromeZone
import me.weishu.kernelsu.ui.util.useFullFeaturedLayout
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem

/**
 * Side navigation rail matching the FolkPatch rail: an icon-only column whose items are vertically
 * centered. It is fixed - there is no collapse control and no label-bearing wide variant - so the
 * side bar looks the same on phones and tablets.
 *
 * In wallpaper mode it shares the docked bottom bar's rule ([BottomBarControl]): the rail turns
 * transparent so the wallpaper reads through, and a [ChromeEdge.Start] scrim softly fades it into
 * the content instead of ending in a hard colour band.
 */
@Composable
fun NavigationRailMaterial(
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
    if (!useFullFeaturedLayout()) return

    val mainPagerState = LocalMainPagerState.current
    val visibilityRevision = NavBarVisibilityConfig.revision.collectAsStateWithLifecycle().value
    val destinations = remember(visibilityRevision) { NavBarVisibilityConfig.visibleDestinations() }
    val railStyle = BottomBarControl.style(BottomBarLayout.Rail)
    WallpaperChromeZone(
        edge = ChromeEdge.Start,
        modifier = modifier,
        material = railStyle.scrim,
    ) {
        NavigationRail(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(railWindowInsets()),
            color = railStyle.containerColor,
            // The insets above are this app's own (bars plus cutout); miuix adds the whole system
            // bar, which would double the padding on one side.
            defaultWindowInsetsPadding = false,
        ) {
            Spacer(Modifier.weight(1f))
            destinations.forEach { destination ->
                val selected = mainPagerState.selectedDestination == destination
                val badge = badgeFor(destination, navigationBadge)
                val badgeSlot: (@Composable () -> Unit)? = if (badge != null) {
                    { NavBadgeDot(badge) }
                } else {
                    null
                }
                NavigationRailItem(
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            mainPagerState.animateToDestination(destination)
                        }
                    },
                    icon = if (selected) destination.iconSelected else destination.iconNotSelected,
                    label = stringResource(destination.label),
                    badge = badgeSlot,
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun railWindowInsets(): WindowInsets =
    WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
