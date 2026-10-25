package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.component.material.ChromeEdge
import me.weishu.kernelsu.ui.component.material.WallpaperChromeZone
import me.weishu.kernelsu.ui.util.useFullFeaturedLayout
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem

/**
 * The docked bar, drawn with miuix's navigation components.
 *
 * miuix owns the indicator, the selected-icon swap and the ripple, so the destinations only have to
 * say which of their two vectors applies. Wallpaper chrome still supplies the container colour, so a
 * theme that paints the bar keeps doing so.
 */
@Composable
fun BottomBarMaterial(navigationBadge: NavigationBadgeState) {
    val fullFeatured = useFullFeaturedLayout()
    if (!fullFeatured) return

    val mainPagerState = LocalMainPagerState.current
    val visibilityRevision = NavBarVisibilityConfig.revision.collectAsStateWithLifecycle().value
    val destinations = remember(visibilityRevision) { NavBarVisibilityConfig.visibleDestinations() }
    val barStyle = BottomBarControl.style(BottomBarLayout.Docked)

    WallpaperChromeZone(edge = ChromeEdge.Bottom, material = barStyle.scrim) {
        NavigationBar(color = barStyle.containerColor) {
            destinations.forEach { destination ->
                val selected = mainPagerState.selectedDestination == destination
                NavigationBarItem(
                    modifier = Modifier.weight(1f),
                    icon = if (selected) destination.iconSelected else destination.iconNotSelected,
                    label = stringResource(destination.label),
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            mainPagerState.animateToDestination(destination)
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun NavigationIconWithBadge(
    destination: BottomBarDestination,
    selected: Boolean,
    badge: NavBadge?,
) {
    if (badge != null) {
        BadgedBox(
            badge = {
                when (badge.tone) {
                    BadgeTone.Alert -> Badge {
                        Text(badge.count.toString())
                    }

                    BadgeTone.Accent -> Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(badge.count.toString())
                    }
                }
            }
        ) {
            NavBarIcon(destination, selected)
        }
    } else {
        NavBarIcon(destination, selected)
    }
}
