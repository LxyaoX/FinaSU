package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.RekernelXStatus
import me.weishu.kernelsu.data.rekernelx.RekernelXInstaller
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem

/**
 * ReKernel-X is the kernel half of tombstone tooling: it reports binder, signal and network
 * events of frozen processes. NeoSU ships the driver in its own assets, so the switch replaces
 * flashing the upstream module - turning it on stages the matching `.ko` and loads it at boot.
 */
@Composable
internal fun RekernelXStatusItem(state: SettingsUiState, actions: SettingsScreenActions) {
    val status = state.rekernelXStatus
    val summary = when {
        state.isRekernelXLoading -> stringResource(R.string.rekernel_x_loading)
        status != null && !state.rekernelXReadError -> rekernelXSummary(status)
        else -> stringResource(R.string.rekernel_x_read_error)
    }

    SegmentedSwitchItem(
        icon = Icons.Rounded.Bedtime,
        title = stringResource(R.string.settings_rekernel_x),
        summary = summary,
        checked = status?.enabled == true,
        enabled = status != null &&
            status.kmiSupported &&
            !status.externalModule &&
            !state.rekernelXReadError &&
            !state.isRekernelXLoading &&
            !state.isRekernelXWriting,
        onCheckedChange = actions.onSetRekernelXEnabled,
    )
}

/** One line per fact that changes what the switch would do, most actionable first. */
@Composable
private fun rekernelXSummary(status: RekernelXStatus): String {
    val release = status.kernelRelease.ifBlank { "-" }
    return when {
        // A flashed module wins: staging a second copy would just race it at boot.
        status.externalModule -> stringResource(R.string.rekernel_x_external_module)
        !status.kmiSupported -> stringResource(R.string.rekernel_x_kmi_unsupported, release)
        status.enabled && status.loaded -> stringResource(
            R.string.rekernel_x_loaded,
            RekernelXInstaller.VERSION,
            status.kmi,
        )
        status.enabled -> stringResource(
            R.string.rekernel_x_pending,
            RekernelXInstaller.VERSION,
            status.kmi,
        )
        status.loaded -> stringResource(R.string.rekernel_x_still_loaded)
        else -> stringResource(R.string.rekernel_x_off, RekernelXInstaller.VERSION)
    }
}
