package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.RekernelXStatus
import me.weishu.kernelsu.data.rekernelx.RekernelXInstaller
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import androidx.compose.material3.Icon
import top.yukonga.miuix.kmp.preference.SwitchPreference

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

    SwitchPreference(
            title = stringResource(R.string.settings_rekernel_x),
            summary = summary,
            startAction = { Icon(Icons.Rounded.Bedtime, contentDescription = null) },
            checked = status?.enabled == true,
            onCheckedChange = actions.onSetRekernelXEnabled,
        )
}

/** One line per fact that changes what the switch would do, most actionable first. */
@Composable
private fun rekernelXSummary(status: RekernelXStatus): String {
    val release = status.kernelRelease.ifBlank { "-" }
    // Name the file the boot script will load: with several builds per KMI in circulation, the
    // bundle version alone would not say which one is in memory.
    val driver = status.stagedDriver.ifBlank { RekernelXInstaller.VERSION }
    return when {
        // A flashed module wins: staging a second copy would just race it at boot.
        status.externalModule -> stringResource(R.string.rekernel_x_external_module)
        !status.kmiSupported -> stringResource(R.string.rekernel_x_kmi_unsupported, release)
        status.enabled && status.loaded -> stringResource(
            R.string.rekernel_x_loaded,
            driver,
            status.kmi,
        )
        // The one case worth spelling out: the switch is on, the driver is not in memory, and the
        // same load will fail at boot.
        status.enabled && status.lastLoadError.isNotBlank() -> stringResource(
            R.string.rekernel_x_load_failed,
            status.lastLoadError,
        )

        status.enabled -> stringResource(
            R.string.rekernel_x_pending,
            driver,
            status.kmi,
        )

        status.loaded -> stringResource(R.string.rekernel_x_still_loaded)
        else -> stringResource(R.string.rekernel_x_off, RekernelXInstaller.VERSION)
    }
}
