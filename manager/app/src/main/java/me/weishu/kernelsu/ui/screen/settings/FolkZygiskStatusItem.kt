package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference

internal fun showFolkZygiskRecovery(state: SettingsUiState): Boolean =
    state.folkZygiskStatus?.startupBlocked == true

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
            val failure = status.startupFailureCode?.let { code ->
                stringResource(when (code) {
                    "engine_missing" -> R.string.folkzygisk_failure_engine_missing
                    "runtime_unsafe" -> R.string.folkzygisk_failure_runtime_unsafe
                    "monitor_start_failed" -> R.string.folkzygisk_failure_monitor_start_failed
                    "monitor_exited" -> R.string.folkzygisk_failure_monitor_exited
                    "readiness_timeout" -> R.string.folkzygisk_failure_readiness_timeout
                    "injection_unconfirmed" -> R.string.folkzygisk_failure_injection_unconfirmed
                    "runtime_invalid" -> R.string.folkzygisk_failure_runtime_invalid
                    else -> R.string.folkzygisk_failure_unknown
                })
            }
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
                if (status.startupBlocked) add(stringResource(R.string.folkzygisk_startup_paused, status.startupFailures))
                failure?.let { add(it) }
                status.startupFailureDetail?.takeIf(String::isNotBlank)?.let(::add)
            }.joinToString("\n")
        }
    }
    SwitchPreference(
        title = stringResource(R.string.settings_folk_zygisk),
        summary = summary,
        startAction = { Icon(Icons.Filled.Code, contentDescription = null) },
        checked = status?.enabled == true,
        onCheckedChange = { actions.onSetFolkZygiskEnabled(it) },
        enabled = status != null && !state.folkZygiskReadError && !state.isFolkZygiskLoading && !state.isFolkZygiskWriting,
    )
    if (showFolkZygiskRecovery(state)) {
        ArrowPreference(
            title = stringResource(R.string.folkzygisk_recover),
            summary = stringResource(R.string.folkzygisk_recover_summary),
            startAction = { Icon(Icons.Filled.Refresh, null) },
            onClick = actions.onRecoverFolkZygisk,
            enabled = !state.folkZygiskReadError &&
                      !state.isFolkZygiskLoading &&
                      !state.isFolkZygiskWriting,
        )
    }
}
