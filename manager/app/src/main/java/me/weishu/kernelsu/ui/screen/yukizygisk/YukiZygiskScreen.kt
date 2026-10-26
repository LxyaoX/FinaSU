package me.weishu.kernelsu.ui.screen.yukizygisk

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * The YukiZygisk page: what the engine is doing right now, and the six switches the daemon reads
 * from `yzconfig.json` when it starts.
 *
 * There is no enable/disable toggle of its own: the engine is either installed and running, or the
 * page says why it is not. Per-module Zygisk behaviour still comes from the modules themselves.
 */
@Composable
fun YukiZygiskScreen() {
    val viewModel = viewModel<YukiZygiskViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    ExpressiveScaffold(
        topBar = {
            MiuixTopAppBar(
                title = stringResource(R.string.yukizygisk_title),
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            val status = state.status
            SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                item {
                    SwitchPreference(
                        title = stringResource(R.string.yukizygisk_enabled_title),
                        summary = if (status?.enabled != false) {
                            stringResource(R.string.yukizygisk_enabled_summary)
                        } else {
                            stringResource(R.string.yukizygisk_disabled_summary)
                        },
                        startAction = { Icon(Icons.Filled.Vaccines, contentDescription = null) },
                        checked = status?.enabled ?: true,
                        onCheckedChange = viewModel::setEnabled,
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.yukizygisk_title),
                        summary = when {
                            state.readFailed -> stringResource(R.string.yukizygisk_read_failed)
                            status != null && !status.supported -> stringResource(R.string.yukizygisk_status_unsupported)
                            status == null || !status.installed -> stringResource(R.string.yukizygisk_status_missing)
                            status.running -> stringResource(R.string.yukizygisk_status_running, status.daemons.size)
                            else -> stringResource(R.string.yukizygisk_status_stopped)
                        },
                        startAction = { Icon(Icons.Filled.Memory, contentDescription = null) },
                        onClick = viewModel::refresh,
                    )
                }
                if (status != null && !status.kernelSupported) {
                    item {
                        ArrowPreference(
                            title = stringResource(R.string.yukizygisk_kernel_missing_title),
                            summary = stringResource(R.string.yukizygisk_kernel_missing_summary),
                            startAction = { Icon(Icons.Filled.Warning, contentDescription = null) },
                            onClick = viewModel::refresh,
                        )
                    }
                }
                if (status != null && !status.running && status.lastError != null) {
                    item {
                        ArrowPreference(
                            title = stringResource(R.string.yukizygisk_last_error_title),
                            summary = status.lastError,
                            startAction = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                            onClick = viewModel::refresh,
                        )
                    }
                }
                if (status != null && status.daemons.isNotEmpty()) {
                    status.daemons.forEach { daemon ->
                        item {
                            ArrowPreference(
                                title = stringResource(R.string.yukizygisk_status_daemon, daemon.abi, daemon.pid),
                                startAction = { Icon(Icons.Filled.Code, contentDescription = null) },
                                onClick = viewModel::refresh,
                            )
                        }
                    }
                }
            }

            if (status != null && status.kernelSupported) {
                val config = status.config
                SegmentedColumn(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    title = stringResource(R.string.yukizygisk_title),
                ) {
                    item {
                        SwitchPreference(
                            title = stringResource(R.string.yukizygisk_yukilinker_title),
                            summary = stringResource(R.string.yukizygisk_yukilinker_summary),
                            startAction = { Icon(Icons.Filled.Tune, contentDescription = null) },
                            checked = config.yukilinker,
                            onCheckedChange = { viewModel.setOption("yukilinker", it.toString()) },
                        )
                    }
                    item {
                        SwitchPreference(
                            title = stringResource(R.string.yukizygisk_anonymous_memory_title),
                            summary = stringResource(R.string.yukizygisk_anonymous_memory_summary),
                            startAction = { Icon(Icons.Filled.Memory, contentDescription = null) },
                            checked = config.anonymousMemory,
                            onCheckedChange = { viewModel.setOption("anonymous_memory", it.toString()) },
                        )
                    }
                    item {
                        SwitchPreference(
                            title = stringResource(R.string.yukizygisk_early_load_title),
                            summary = stringResource(R.string.yukizygisk_early_load_summary),
                            startAction = { Icon(Icons.Filled.Bolt, contentDescription = null) },
                            checked = config.earlyLoad,
                            onCheckedChange = { viewModel.setOption("early_load", it.toString()) },
                        )
                    }
                    item {
                        SwitchPreference(
                            title = stringResource(R.string.yukizygisk_crash_protection_title),
                            summary = stringResource(R.string.yukizygisk_crash_protection_summary),
                            startAction = { Icon(Icons.Filled.Shield, contentDescription = null) },
                            checked = config.crashProtection,
                            onCheckedChange = { viewModel.setOption("crash_protection", it.toString()) },
                        )
                    }
                    item {
                        SwitchPreference(
                            title = stringResource(R.string.yukizygisk_dmesg_title),
                            summary = stringResource(R.string.yukizygisk_dmesg_summary),
                            startAction = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                            checked = config.dmesgLog,
                            onCheckedChange = { viewModel.setOption("dmesg_log", it.toString()) },
                        )
                    }
                    item {
                        val modes = listOf(
                            stringResource(R.string.yukizygisk_denylist_off),
                            stringResource(R.string.yukizygisk_denylist_force),
                            stringResource(R.string.yukizygisk_denylist_restore),
                        )
                        SegmentedDropdownItem(
                            title = stringResource(R.string.yukizygisk_denylist_title),
                            items = modes,
                            selectedIndex = config.denylistMode.coerceIn(0, modes.lastIndex),
                            onItemSelected = { viewModel.setOption("denylist_mode", it.toString()) },
                            icon = Icons.Filled.Tune,
                        )
                    }
                }
            }

            SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                item {
                    ArrowPreference(
                        title = stringResource(R.string.yukizygisk_restart),
                        startAction = { Icon(Icons.Filled.RestartAlt, contentDescription = null) },
                        onClick = viewModel::restart,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
