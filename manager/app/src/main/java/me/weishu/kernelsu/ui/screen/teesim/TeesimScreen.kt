package me.weishu.kernelsu.ui.screen.teesim

import android.content.Intent
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
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
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.module.ShortcutType
import me.weishu.kernelsu.ui.util.module.Shortcut
import me.weishu.kernelsu.ui.viewmodel.TeesimUiState
import me.weishu.kernelsu.ui.viewmodel.TeesimViewModel
import me.weishu.kernelsu.ui.webui.WebUIActivity

/**
 * TEESimulator settings.
 *
 * The page owns the three things the daemon cannot do for itself: installing the bundled module,
 * the on/off marker ksud reads, and the keybox file it signs with. Everything finer grained
 * (per-profile device identity, stored keys, logs) stays in the module's own WebUI, which this
 * page opens when the bundled build carries one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeesimScreen() {
    val viewModel = viewModel<TeesimViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults
        .exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var confirmUninstall by remember { mutableStateOf(false) }

    // OpenDocument rather than GetContent: a keybox is usually pulled from the file manager, and
    // the returned URI is only read, never written back.
    val keyboxPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importKeybox(uri)
    }
    val webUiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { /* The module keeps its own state; nothing to refresh here. */ }

    // The target picker and the module WebUI are separate screens with their own state, so the
    // counts above go stale while they are on top; re-read on the way back.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.teesim_title)) },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                colors = expressiveTopAppBarColors(),
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
                    SegmentedListItem(
                        headlineContent = {
                            Text(
                                when {
                                    status == null -> stringResource(
                                        R.string.teesim_bundled_version,
                                        TeesimInstaller.VERSION,
                                    )

                                    status.installed -> status.version.ifBlank {
                                        TeesimInstaller.VERSION
                                    }

                                    else -> stringResource(
                                        R.string.teesim_bundled_version,
                                        TeesimInstaller.VERSION,
                                    )
                                }
                            )
                        },
                        supportingContent = { Text(teesimSummary(state)) },
                        leadingContent = { Icon(Icons.Filled.VerifiedUser, null) },
                    )
                }
                item {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Security,
                        title = stringResource(R.string.teesim_enable),
                        summary = stringResource(R.string.teesim_enable_summary),
                        checked = state.status?.enabled == true,
                        enabled = !state.loading && !state.writing,
                        onCheckedChange = viewModel::setEnabled,
                    )
                }
                item {
                    val status = state.status
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(R.string.teesim_keybox)) },
                        supportingContent = {
                            Text(
                                if (status != null && status.keyboxPresent) {
                                    stringResource(
                                        R.string.teesim_keybox_present,
                                        status.keyboxBytes,
                                    )
                                } else {
                                    stringResource(R.string.teesim_keybox_missing)
                                }
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Lock, null) },
                        enabled = status?.installed == true && !state.writing,
                        onClick = {
                            // Some providers report a keybox as octet-stream, so keep the
                            // declared XML types but do not lock the picker to them.
                            keyboxPicker.launch(
                                arrayOf("text/xml", "application/xml", "text/plain", "*/*")
                            )
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        },
                    )
                }
                if (state.status?.keyboxPresent == true) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.teesim_keybox_remove)) },
                            leadingContent = { Icon(Icons.Filled.Delete, null) },
                            enabled = !state.writing,
                            onClick = viewModel::removeKeybox,
                        )
                    }
                }
                item {
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(R.string.teesim_targets)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.teesim_targets_summary,
                                    state.status?.targetApps.orEmpty().size,
                                )
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Apps, null) },
                        enabled = state.status?.installed == true && !state.writing,
                        onClick = { navigator.push(Route.TeesimTargets) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        },
                    )
                }
                item {
                    val status = state.status
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(R.string.teesim_open_webui)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (status?.webUiAvailable == true) {
                                        R.string.teesim_open_webui_summary
                                    } else {
                                        R.string.teesim_webui_unavailable
                                    }
                                )
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Language, null) },
                        enabled = status != null &&
                            status.installed &&
                            status.webUiAvailable &&
                            !state.writing,
                        onClick = {
                            webUiLauncher.launch(
                                Intent(context, WebUIActivity::class.java)
                                    .setData(
                                        Shortcut.buildShortcutUri(
                                            TeesimInstaller.MODULE_ID,
                                            ShortcutType.WebUI,
                                        )
                                    )
                            )
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        },
                    )
                }
                item {
                    SegmentedListItem(
                        headlineContent = {
                            Text(
                                stringResource(
                                    if (state.status?.installed == true) {
                                        R.string.teesim_reinstall
                                    } else {
                                        R.string.teesim_install
                                    }
                                )
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.teesim_install_summary,
                                    TeesimInstaller.VERSION,
                                )
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Download, null) },
                        enabled = !state.loading && !state.writing,
                        onClick = viewModel::install,
                    )
                }
                item {
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(R.string.teesim_uninstall)) },
                        supportingContent = {
                            Text(stringResource(R.string.teesim_uninstall_summary))
                        },
                        leadingContent = { Icon(Icons.Filled.Delete, null) },
                        enabled = state.status?.installed == true && !state.writing,
                        onClick = { confirmUninstall = true },
                    )
                }
            }
        }
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text(stringResource(R.string.teesim_uninstall_confirm_title)) },
            text = { Text(stringResource(R.string.teesim_uninstall_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUninstall = false
                        viewModel.uninstall()
                    },
                ) {
                    Text(stringResource(R.string.teesim_uninstall))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) {
                    Text(stringResource(R.string.teesim_cancel))
                }
            },
        )
    }
}

/** One line per fact that changes what the switch would do, most actionable first. */
@Composable
private fun teesimSummary(state: TeesimUiState): String {
    val status = state.status
    return when {
        state.loading -> stringResource(R.string.teesim_loading)
        status == null -> stringResource(R.string.teesim_read_error)
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
