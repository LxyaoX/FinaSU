package me.weishu.kernelsu.ui.screen.teesim

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.teesim.TeesimInstaller
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.viewmodel.TeesimUiState
import me.weishu.kernelsu.ui.viewmodel.TeesimViewModel
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import me.weishu.kernelsu.ui.component.material.ExpressiveDialog

/**
 * TEESimulator settings.
 *
 * The page owns the three things the daemon cannot do for itself: staging the bundled runtime, the
 * boot hook ksud reads, and the keybox file the daemon signs with. Upstream's WebUI is not part of
 * that runtime, so everything it used to edit (per-profile device identity, stored keys, logs) has
 * to be handled here or by editing `/data/adb/teesim/config.json` by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeesimScreen() {
    val viewModel = viewModel<TeesimViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior()
    var confirmUninstall by remember { mutableStateOf(false) }

    // OpenDocument rather than GetContent: a keybox is usually pulled from the file manager, and
    // the returned URI is only read, never written back.
    val keyboxPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importKeybox(uri)
    }
    // The target picker is a separate screen with its own state, so the counts above go stale
    // while it is on top; re-read on the way back.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    ExpressiveScaffold(
        topBar = {
            MiuixTopAppBar(
                title = stringResource(R.string.teesim_title),
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                item {
                    val status = state.status
                    // What is on disk wins: an asset update without a reinstall then shows the old
                    // version here instead of claiming the runtime was upgraded with the app. The
                    // "not installed" wording belongs to exactly one case - nothing staged - because
                    // it used to be the fallback for any missing version, which made this row deny a
                    // runtime the line right below it reported as running.
                    val installedVersion = status?.installedVersion.orEmpty()
                    val bundled = TeesimInstaller.VERSION
                    ArrowPreference(
                        title = when {
                                    status?.installed != true ->
                                        stringResource(R.string.teesim_bundled_version, bundled)

                                    installedVersion.isBlank() ->
                                        stringResource(R.string.teesim_runtime_unversioned)

                                    installedVersion != bundled -> stringResource(
                                        R.string.teesim_runtime_bundled_pair,
                                        installedVersion,
                                        bundled,
                                    )

                                    else -> installedVersion
                                },
                        summary = teesimSummary(state),
                        startAction = { Icon(Icons.Filled.VerifiedUser, null) },
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.teesim_enable),
                        summary = stringResource(R.string.teesim_enable_summary),
                        startAction = { Icon(Icons.Filled.Security, contentDescription = null) },
                        checked = state.status?.enabled == true,
                        onCheckedChange = viewModel::setEnabled,
                        enabled = !state.loading && !state.writing,
                    )
                }
                item {
                    val status = state.status
                    ArrowPreference(
                        title = stringResource(R.string.teesim_keybox),
                        summary = if (status != null && status.keyboxPresent) {
                                      stringResource(
                                          R.string.teesim_keybox_present,
                                          keyboxSize(status.keyboxBytes),
                                      )
                                  } else {
                                      stringResource(R.string.teesim_keybox_missing)
                                  },
                        startAction = { Icon(Icons.Filled.Lock, null) },
                        onClick = {
                            // Some providers report a keybox as octet-stream, so keep the
                            // declared XML types but do not lock the picker to them.
                            keyboxPicker.launch(
                                arrayOf("text/xml", "application/xml", "text/plain", "*/*")
                            )
                        },
                        enabled = status?.installed == true && !state.writing,
                    )
                }
                if (state.status?.keyboxPresent == true) {
                    item {
                        ArrowPreference(
                            title = stringResource(R.string.teesim_keybox_remove),
                            startAction = { Icon(Icons.Filled.Delete, null) },
                            onClick = viewModel::removeKeybox,
                            enabled = !state.writing,
                        )
                    }
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.teesim_targets),
                        summary = stringResource(
                                      R.string.teesim_targets_summary,
                                      state.status?.targetApps.orEmpty().size,
                                  ),
                        startAction = { Icon(Icons.Filled.Apps, null) },
                        onClick = { navigator.push(Route.TeesimTargets) },
                        enabled = state.status?.installed == true && !state.writing,
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(
                                    if (state.status?.installed == true) {
                                        R.string.teesim_reinstall
                                    } else {
                                        R.string.teesim_install
                                    }
                                ),
                        summary = stringResource(
                                      R.string.teesim_install_summary,
                                      TeesimInstaller.VERSION,
                                  ),
                        startAction = { Icon(Icons.Filled.Download, null) },
                        onClick = viewModel::install,
                        enabled = !state.loading && !state.writing,
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.teesim_uninstall),
                        summary = stringResource(R.string.teesim_uninstall_summary),
                        startAction = { Icon(Icons.Filled.Delete, null) },
                        onClick = { confirmUninstall = true },
                        enabled = state.status?.installed == true && !state.writing,
                    )
                }
            }
        }
    }

    if (confirmUninstall) {
        ExpressiveDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text(stringResource(R.string.teesim_uninstall_confirm_title)) },
            text = { Text(stringResource(R.string.teesim_uninstall_confirm)) },
            confirmButton = {
                MiuixTextButton(
                    text = stringResource(R.string.teesim_uninstall),
                    onClick = {
                        confirmUninstall = false
                        viewModel.uninstall()
                    },
                )
            },
            dismissButton = {
                MiuixTextButton(
                    text = stringResource(R.string.teesim_cancel),
                    onClick = { confirmUninstall = false },
                )
            },
        )
    }
}

/** Human readable keybox size: a raw byte count says nothing about whether a keybox is real. */
private fun keyboxSize(bytes: Long): String =
    if (bytes >= 1024) "${bytes / 1024} KB" else "$bytes B"

/** One line per fact that changes what the switch would do, most actionable first. */
@Composable
private fun teesimSummary(state: TeesimUiState): String {
    val status = state.status
    return when {
        state.loading -> stringResource(R.string.teesim_loading)
        status == null -> stringResource(R.string.teesim_read_error)
        // A daemon can outlive the files it was started from, and "not installed" would then deny
        // what the process list says. Naming the half-state points at the one action that fixes it.
        !status.installed && status.daemonRunning ->
            stringResource(R.string.teesim_state_partial)

        !status.installed -> stringResource(R.string.teesim_state_missing)
        !status.enabled -> stringResource(R.string.teesim_state_disabled)
        // Without a keybox the interceptor stays a no-op, so say that instead of "ready".
        !status.keyboxPresent -> stringResource(R.string.teesim_state_no_keybox)
        status.daemonRunning -> stringResource(
            R.string.teesim_state_running,
            status.targetApps.size,
        )

        else -> stringResource(R.string.teesim_state_pending)
    }
}
