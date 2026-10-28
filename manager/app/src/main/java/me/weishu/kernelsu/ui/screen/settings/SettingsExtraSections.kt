package me.weishu.kernelsu.ui.screen.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.basic.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.bottombar.BottomBarDestination
import me.weishu.kernelsu.ui.component.bottombar.FloatingBarConfig
import me.weishu.kernelsu.ui.component.bottombar.NavBarVisibilityConfig
import me.weishu.kernelsu.ui.component.bottombar.NavMode
import me.weishu.kernelsu.ui.component.bottombar.NavModeConfig
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SliderTrackConfig
import me.weishu.kernelsu.ui.component.material.TonalCard
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

private val SectionModifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp)

/**
 * Navigation and chrome settings lifted out of the former "theme" screen: the navigation badge,
 * predictive back and the bottom-bar / floating-bar configuration.
 *
 * Badge and predictive-back go through [SettingsScreenActions] because they are ViewModel state
 * (predictive back also flips the platform back callback). The bar configs are written to their own
 * config objects directly, exactly as the theme screen did.
 */
@Composable
fun NavigationSettingsSection(uiState: SettingsUiState, actions: SettingsScreenActions) {
    SegmentedColumn(
        modifier = SectionModifier,
        content = listOf(
            {
                SwitchPreference(
                    title = stringResource(id = R.string.settings_navigation_badge),
                    summary = stringResource(id = R.string.settings_navigation_badge_summary),
                    startAction = { Icon(Icons.Rounded.Pin, contentDescription = null) },
                    checked = uiState.enableNavigationBadge,
                    onCheckedChange = actions.onSetEnableNavigationBadge,
                )
            }
        )
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        SegmentedColumn(
            modifier = SectionModifier,
            content = listOf(
                {
                    SwitchPreference(
                        title = stringResource(id = R.string.settings_enable_predictive_back),
                        summary = stringResource(id = R.string.settings_enable_predictive_back_summary),
                        startAction = { Icon(Icons.AutoMirrored.Rounded.MenuOpen, contentDescription = null) },
                        checked = uiState.enablePredictiveBack,
                        onCheckedChange = actions.onSetEnablePredictiveBack,
                    )
                }
            )
        )
    }

    NavLayoutSection()
    NavModeSection()
}

@Composable
private fun NavLayoutSection() {
    val revision by NavBarVisibilityConfig.revision.collectAsState()
    val visibility = remember(revision) {
        NavBarVisibilityConfig.hideable.associateWith { NavBarVisibilityConfig.isVisible(it) }
    }

    SegmentedColumn(
        modifier = SectionModifier,
        title = stringResource(R.string.settings_nav_layout_title),
        content = buildList<@Composable () -> Unit> {
            NavBarVisibilityConfig.hideable.forEach { destination ->
                add {
                    SwitchPreference(
                        title = stringResource(
                                    when (destination) {
                                        BottomBarDestination.SuperUser -> R.string.settings_show_superuser
                                        else -> R.string.settings_show_module
                                    }
                                ),
                        startAction = { Icon(destination.iconNotSelected, contentDescription = null) },
                        checked = visibility[destination] == true,
                        onCheckedChange = { NavBarVisibilityConfig.setVisible(destination, it) },
                    )
                }
            }
        }
    )
}

@Composable
private fun NavModeSection() {
    val navModeRevision by NavModeConfig.revision.collectAsState()
    val navMode = remember(navModeRevision) { NavModeConfig.mode }
    val floatingRevision by FloatingBarConfig.revision.collectAsState()
    val floatingStyle = remember(floatingRevision) { FloatingBarConfig.style }
    val floatingCompact = remember(floatingRevision) { FloatingBarConfig.compact }
    val floatingAutoHide = remember(floatingRevision) { FloatingBarConfig.autoHide }
    val floatingSwipeHide = remember(floatingRevision) { FloatingBarConfig.swipeHide }
    val navModes = NavMode.entries
    val navModeLabels = listOf(
        stringResource(R.string.settings_nav_mode_auto),
        stringResource(R.string.settings_nav_mode_bottom),
        stringResource(R.string.settings_nav_mode_rail),
        stringResource(R.string.settings_nav_mode_floating),
    )
    val styles = FloatingBarConfig.Style.entries
    val styleLabels = listOf(
        stringResource(R.string.settings_floating_bar_style_standard),
        stringResource(R.string.settings_floating_bar_style_drawer),
    )

    SegmentedColumn(
        modifier = SectionModifier,
        content = buildList<@Composable () -> Unit> {
            add {
                OverlayDropdownPreference(
                    title = stringResource(R.string.settings_nav_mode),
                    summary = stringResource(R.string.settings_nav_mode_summary),
                    items = navModeLabels,
                    startAction = { Icon(Icons.Rounded.SpaceDashboard, contentDescription = null) },
                    selectedIndex = navModes.indexOf(navMode).coerceAtLeast(0),
                    onSelectedIndexChange = { NavModeConfig.mode = navModes[it] },
                )
            }
            if (navMode == NavMode.Floating) {
                add {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.settings_floating_bar_style),
                        items = styleLabels,
                        startAction = { Icon(Icons.Rounded.Style, contentDescription = null) },
                        selectedIndex = styles.indexOf(floatingStyle).coerceAtLeast(0),
                        onSelectedIndexChange = { FloatingBarConfig.style = styles[it] },
                    )
                }
                add {
                    SwitchPreference(
                        title = stringResource(R.string.settings_floating_bar_compact),
                        startAction = { Icon(Icons.Rounded.RoundedCorner, contentDescription = null) },
                        checked = floatingCompact,
                        onCheckedChange = { FloatingBarConfig.compact = it },
                    )
                }
                add {
                    SwitchPreference(
                        title = stringResource(R.string.settings_floating_auto_hide),
                        summary = stringResource(R.string.settings_floating_auto_hide_summary),
                        startAction = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
                        checked = floatingAutoHide,
                        onCheckedChange = { FloatingBarConfig.autoHide = it },
                    )
                }
                add {
                    SwitchPreference(
                        title = stringResource(R.string.settings_floating_swipe_hide),
                        summary = stringResource(R.string.settings_floating_swipe_hide_summary),
                        startAction = { Icon(Icons.Rounded.SwipeUp, contentDescription = null) },
                        checked = floatingSwipeHide,
                        onCheckedChange = { FloatingBarConfig.swipeHide = it },
                    )
                }
            }
        }
    )
}

/**
 * Global display density controls that are not colour: page zoom and the module-description line
 * count. Kept beside their TonalCard sliders, mirroring how the old theme screen rendered them.
 */
@Composable
fun DisplayDensitySection(uiState: SettingsUiState, actions: SettingsScreenActions) {
    TonalCard(modifier = SectionModifier) {
        // miuix's slider keeps no state of its own; the row drags this and only writes on release.
        var pageScale by remember(uiState.pageScale) { mutableFloatStateOf(uiState.pageScale) }
        val pageScaleSteps = if (SliderTrackConfig.isDiscrete) 19 else 0

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.AspectRatio,
                    contentDescription = stringResource(id = R.string.settings_page_scale),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_page_scale),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = R.string.settings_page_scale_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${(pageScale * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Slider(
                value = pageScale,
                onValueChange = { pageScale = it },
                onValueChangeFinished = { actions.onSetPageScale(pageScale) },
                valueRange = 0.8f..1.1f,
                steps = pageScaleSteps,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    TonalCard(modifier = SectionModifier) {
        var descriptionLines by remember(uiState.moduleDescriptionMaxLines) {
            mutableFloatStateOf(uiState.moduleDescriptionMaxLines.toFloat())
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Description,
                    contentDescription = stringResource(id = R.string.settings_module_description_max_lines),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_module_description_max_lines),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = R.string.settings_module_description_max_lines_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${descriptionLines.roundToInt()} " + stringResource(R.string.unit_lines),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Slider(
                value = descriptionLines,
                onValueChange = { descriptionLines = it },
                onValueChangeFinished = {
                    actions.onSetModuleDescriptionMaxLines(descriptionLines.roundToInt())
                },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
