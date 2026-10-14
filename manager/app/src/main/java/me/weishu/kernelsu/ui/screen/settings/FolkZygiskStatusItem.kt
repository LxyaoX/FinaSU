package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem

@Composable
internal fun FolkZygiskStatusItem(state: SettingsUiState, actions: SettingsScreenActions) {
    val status = state.folkZygiskStatus
    val summary = when {
        state.isFolkZygiskLoading -> stringResource(R.string.folkzygisk_loading)
        state.folkZygiskReadError || status == null -> stringResource(R.string.folkzygisk_read_error)
        else -> {
            val injection = stringResource(when (status.injectionState) {
                "injected" -> R.string.folkzygisk_injected
                "partial" -> R.string.folkzygisk_partial
                "starting" -> R.string.folkzygisk_starting
                "failed" -> R.string.folkzygisk_failed
                else -> R.string.folkzygisk_unverified
            })
            val external = status.externalProviders
            val selection = stringResource(if (status.enabled) R.string.folkzygisk_next_enabled
                else R.string.folkzygisk_next_disabled)
            val blockers = status.enableBlockers.map { reason ->
                stringResource(when (reason) {
                    "license_review_required" -> R.string.folkzygisk_license_gate
                    "device_acceptance_required" -> R.string.folkzygisk_device_gate
                    "engine_missing" -> R.string.folkzygisk_engine_missing
                    "startup_protection" -> R.string.folkzygisk_startup_protection
                    else -> R.string.folkzygisk_unknown_gate
                })
            }.distinct()
            buildList {
                add(stringResource(R.string.folkzygisk_current, injection))
                add(selection)
                if (external.isNotEmpty()) {
                    val names = external.joinToString(", ")
                    // The built-in engine only yields to a provider at boot, so until a reboot the
                    // injection state above is still current; this line states next-boot precedence.
                    add(
                        if (status.enabled) stringResource(R.string.folkzygisk_external_precedence, names)
                        else stringResource(R.string.folkzygisk_external, names)
                    )
                }
                if (status.rebootRequired == true || state.folkZygiskRebootPending) add(stringResource(R.string.folkzygisk_reboot_required))
                if (blockers.isNotEmpty()) add(stringResource(R.string.folkzygisk_blockers, blockers.joinToString("; ")))
            }.joinToString("\n")
        }
    }
    SegmentedSwitchItem(
        icon = Icons.Filled.Code,
        title = "FolkZygisk",
        summary = summary,
        checked = status?.enabled == true,
        enabled = status != null && !state.folkZygiskReadError && !state.isFolkZygiskLoading && !state.isFolkZygiskWriting,
        onCheckedChange = { actions.onSetFolkZygiskEnabled(it) },
    )
}
