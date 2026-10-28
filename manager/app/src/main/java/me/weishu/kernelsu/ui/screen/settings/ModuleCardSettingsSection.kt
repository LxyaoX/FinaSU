package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.modulecard.ModuleCardConfig
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

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
                onSelectedIndexChange = { index ->
                    ModuleCardConfig.updateButtonStyle(
                        if (index == 1) ModuleCardConfig.STYLE_ICON_ONLY else ModuleCardConfig.STYLE_FULL
                    )
                },
            )
        }
    }
}
