package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.BootPropsStatus
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem

/**
 * The switch that corrects what the bootloader told init.
 *
 * On an unlocked device `ro.boot.flash.locked` is 0, `ro.boot.verifiedbootstate` is orange and
 * `ro.boot.vbmeta.device_state` is often not reported at all, which is the cheapest way for a
 * detector to spot a modified device. KernelSU already carries `resetprop` inside `ksud`, so the
 * manager rewrites those properties itself instead of leaving it to a separate module.
 */
@Composable
internal fun BootPropsItem(state: SettingsUiState, actions: SettingsScreenActions) {
    val status = state.bootPropsStatus
    val summary = when {
        state.isBootPropsLoading -> stringResource(R.string.boot_props_loading)
        status == null || state.bootPropsReadError -> stringResource(R.string.boot_props_read_error)
        else -> bootPropsSummary(status)
    }

    SegmentedSwitchItem(
        icon = Icons.Rounded.Lock,
        title = stringResource(R.string.settings_boot_props),
        summary = summary,
        checked = status?.enabled == true,
        enabled = status != null &&
            status.ksudAvailable &&
            !state.bootPropsReadError &&
            !state.isBootPropsLoading &&
            !state.isBootPropsWriting,
        onCheckedChange = actions.onSetBootPropsEnabled,
    )
}

/** One line that says what the switch is doing and, when it is not enough, what is left. */
@Composable
private fun bootPropsSummary(status: BootPropsStatus): String = when {
    // Without ksud there is no way to touch a ro. property, so this is not a state the switch can
    // fix by being toggled.
    !status.ksudAvailable -> stringResource(R.string.boot_props_no_ksud)
    !status.enabled && status.allApplied -> stringResource(R.string.boot_props_off_clean)
    !status.enabled -> stringResource(R.string.boot_props_off, status.tracked)
    status.allApplied -> stringResource(R.string.boot_props_on, status.applied)
    else -> stringResource(
        R.string.boot_props_pending,
        status.pending.size,
        status.tracked,
        status.pending.joinToString(", "),
    )
}
