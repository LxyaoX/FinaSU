package me.weishu.kernelsu.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.weishu.kernelsu.R
import me.weishu.kernelsu.modulebanner.BannerApiClient
import me.weishu.kernelsu.modulebanner.ModuleBannerConfig
import me.weishu.kernelsu.modulebanner.ModuleCardConfig
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSliderItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.snapToPercent
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import me.weishu.kernelsu.ui.component.material.ExpressiveDialog

private val SectionModifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp)

@Composable
fun ModuleCardSettingsSection() {
    SegmentedColumn(
        modifier = SectionModifier,
        title = stringResource(R.string.settings_module_display),
    ) {
        item {
            SwitchPreference(
                title = stringResource(R.string.module_card_show_more_info),
                summary = stringResource(R.string.module_card_show_more_info_summary),
                startAction = { Icon(Icons.Filled.Description, contentDescription = null) },
                checked = ModuleCardConfig.showMoreInfo,
                onCheckedChange = ModuleCardConfig::updateShowMoreInfo,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.module_card_fold_system),
                summary = stringResource(R.string.module_card_fold_system_summary),
                startAction = { Icon(Icons.Filled.Layers, contentDescription = null) },
                checked = ModuleCardConfig.foldSystemModule,
                onCheckedChange = ModuleCardConfig::updateFoldSystemModule,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.module_card_spliced),
                summary = stringResource(R.string.module_card_spliced_summary),
                startAction = { Icon(Icons.Filled.GridView, contentDescription = null) },
                checked = ModuleCardConfig.splicedCardGroup,
                onCheckedChange = ModuleCardConfig::updateSplicedCardGroup,
            )
        }
        item {
            val styleItems = listOf(
                stringResource(R.string.module_card_style_full),
                stringResource(R.string.module_card_style_icon),
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.module_card_button_style),
                summary = stringResource(R.string.module_card_button_style_summary),
                items = styleItems,
                startAction = { Icon(Icons.Filled.Palette, contentDescription = null) },
                selectedIndex = if (ModuleCardConfig.buttonStyle == ModuleCardConfig.STYLE_ICON_ONLY) 1 else 0,
                onSelectedIndexChange = {
                    index ->
                                       ModuleCardConfig.updateButtonStyle(
                                           if (index == 1) ModuleCardConfig.STYLE_ICON_ONLY else ModuleCardConfig.STYLE_FULL
                                       )
                },
            )
        }
    }
}

@Composable
fun ModuleBannerSettingsSection() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    var showSourceDialog by remember { mutableStateOf(false) }
    val policy = ModuleBannerConfig.policy
    // API controls follow the same parent gates used by card rendering.
    val apiRowsVisible = policy.apiActive
    val apiConfigured = ModuleBannerConfig.effectiveApiSource.isNotEmpty()

    SegmentedColumn(
        modifier = SectionModifier,
        title = stringResource(R.string.settings_module_banner),
    ) {
        // Every switch here is a full-width row that sits right above the opacity slider, so it must
        // not be flipped by a drag aimed at the slider: these rows answer to a real tap only.
        item {
            SwitchPreference(
                title = stringResource(R.string.module_banner_enabled),
                summary = stringResource(R.string.module_banner_enabled_summary),
                startAction = { Icon(Icons.Filled.Wallpaper, contentDescription = null) },
                checked = ModuleBannerConfig.enabled,
                onCheckedChange = ModuleBannerConfig::updateEnabled,
            )
        }
        item(visible = ModuleBannerConfig.enabled) {
            SwitchPreference(
                title = stringResource(R.string.module_banner_folk),
                summary = stringResource(R.string.module_banner_folk_summary),
                startAction = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                checked = ModuleBannerConfig.folkEnabled,
                onCheckedChange = ModuleBannerConfig::updateFolkEnabled,
            )
        }
        item(visible = policy.active) {
            SwitchPreference(
                title = stringResource(R.string.module_banner_api_mode),
                summary = stringResource(R.string.module_banner_api_mode_summary),
                startAction = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                checked = ModuleBannerConfig.apiModeEnabled,
                onCheckedChange = ModuleBannerConfig::updateApiModeEnabled,
            )
        }
        item(visible = apiRowsVisible) {
            ArrowPreference(
                title = stringResource(R.string.module_banner_api_source),
                summary = if (apiConfigured) ModuleBannerConfig.effectiveApiSource
                          else stringResource(R.string.module_banner_api_source_not_configured),
                startAction = { Icon(Icons.Outlined.Cloud, null) },
                onClick = { showSourceDialog = true },
            )
        }
        item(visible = apiRowsVisible) {
            ArrowPreference(
                title = stringResource(R.string.module_banner_marketplace),
                summary = stringResource(R.string.module_banner_marketplace_summary),
                startAction = { Icon(Icons.Outlined.Cloud, null) },
                onClick = { navigator.push(Route.ApiMarketplace) },
            )
        }
        item(visible = apiRowsVisible) {
            ArrowPreference(
                title = stringResource(R.string.module_banner_clear_cache),
                startAction = { Icon(Icons.Outlined.Cloud, null) },
                onClick = {
                    BannerApiClient.clearAll(context)
                    ModuleBannerConfig.requestReload()
                    Toast.makeText(context, R.string.module_banner_cache_cleared, Toast.LENGTH_SHORT).show()
                },
            )
        }
        item(visible = ModuleBannerConfig.enabled) {
            SwitchPreference(
                title = stringResource(R.string.module_banner_custom_opacity),
                summary = stringResource(R.string.module_banner_custom_opacity_summary),
                startAction = { Icon(Icons.Filled.Wallpaper, contentDescription = null) },
                checked = ModuleBannerConfig.customOpacityEnabled,
                onCheckedChange = ModuleBannerConfig::updateCustomOpacityEnabled,
            )
        }
        item(visible = ModuleBannerConfig.enabled && ModuleBannerConfig.customOpacityEnabled) {
            SegmentedSliderItem(
                icon = Icons.Filled.Wallpaper,
                title = stringResource(R.string.module_banner_opacity),
                value = ModuleBannerConfig.customOpacity,
                valueRange = 0.1f..1f,
                valueText = { "${(it * 100f).roundToInt()}%" },
                onValueChange = { ModuleBannerConfig.editCustomOpacity(snapToPercent(it)) },
                onValueChangeFinished = { ModuleBannerConfig.persist() },
            )
        }
    }

    if (showSourceDialog) {
        BannerApiSourceDialog(onDismiss = { showSourceDialog = false })
    }
}

@Composable
private fun BannerApiSourceDialog(onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(ModuleBannerConfig.apiSource) }

    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.module_banner_api_source_dialog_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.module_banner_api_source_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            MiuixTextButton(
                text = stringResource(android.R.string.ok),
                onClick = {
                    ModuleBannerConfig.updateApiSource(value.trim())
                    onDismiss()
                },
            )
        },
        dismissButton = {
            MiuixTextButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
            )
        },
    )
}
