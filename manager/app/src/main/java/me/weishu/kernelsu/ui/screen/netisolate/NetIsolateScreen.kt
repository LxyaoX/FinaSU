package me.weishu.kernelsu.ui.screen.netisolate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.netisolate.NETISOLATE_MAX_UIDS
import me.weishu.kernelsu.data.uts.UtsTarget
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.screen.uts.TargetAppSheet
import me.weishu.kernelsu.ui.viewmodel.NetIsolateViewModel

@Composable
fun NetIsolateScreen() {
    val viewModel = viewModel<NetIsolateViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showTargets by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val status = state.status
    val canEdit = status.supported && !state.busy && !state.loading
    val packageByUid = remember(state.apps) { state.apps.associate { it.uid to it.packageName } }
    val targets = remember(status.uids, packageByUid) {
        status.uids.map { uid ->
            UtsTarget(
                packageName = packageByUid[uid] ?: "UID $uid",
                uid = uid,
            )
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LaunchedEffect(showTargets) {
        if (showTargets) viewModel.loadApps()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.netisolate_clear)) },
            text = { Text(stringResource(R.string.netisolate_clear_summary)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clear()
                    },
                ) {
                    Text(stringResource(R.string.netisolate_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showTargets) {
        TargetAppSheet(
            apps = state.apps,
            selectedTargets = targets,
            loading = state.appsLoading || !state.appsLoaded,
            onDismiss = { showTargets = false },
            onConfirm = {
                showTargets = false
                viewModel.setTargets(it)
            },
            maxTargets = NETISOLATE_MAX_UIDS,
            title = stringResource(R.string.netisolate_target_apps),
            countText = { selected, maximum ->
                stringResource(R.string.netisolate_target_count, selected, maximum)
            },
        )
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.netisolate_title)) },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackbar) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(innerPadding)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            SegmentedColumn {
                item {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.WifiOff,
                        title = stringResource(R.string.netisolate_enable),
                        summary = stringResource(
                            when {
                                !state.hasLoaded || state.loading ->
                                    R.string.netisolate_loading
                                !status.supported -> R.string.netisolate_unsupported
                                status.enabled -> R.string.netisolate_enabled_summary
                                else -> R.string.netisolate_disabled_summary
                            },
                        ),
                        checked = status.enabled,
                        enabled = canEdit,
                        onCheckedChange = viewModel::setEnabled,
                    )
                }
                item(visible = status.enabled) {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Security,
                        title = stringResource(R.string.netisolate_strict_socket),
                        summary = stringResource(
                            if (status.strictSocketSupported) {
                                R.string.netisolate_strict_socket_summary
                            } else {
                                R.string.netisolate_strict_socket_unsupported
                            },
                        ),
                        checked = status.strictSocket,
                        enabled = canEdit && status.strictSocketSupported,
                        onCheckedChange = viewModel::setStrictSocket,
                    )
                }
                item(visible = status.enabled) {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.netisolate_allow_loopback),
                        summary = stringResource(
                            when {
                                !status.allowLoopbackSupported ->
                                    R.string.netisolate_allow_loopback_unsupported
                                status.strictSocket ->
                                    R.string.netisolate_strict_loopback_conflict
                                else -> R.string.netisolate_allow_loopback_summary
                            },
                        ),
                        checked = status.allowLoopback,
                        enabled = canEdit &&
                            status.allowLoopbackSupported &&
                            !status.strictSocket,
                        onCheckedChange = viewModel::setAllowLoopback,
                    )
                }
                item {
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(R.string.netisolate_target_apps)) },
                        supportingContent = {
                            Text(
                                if (targets.isEmpty()) {
                                    stringResource(R.string.netisolate_no_targets)
                                } else {
                                    stringResource(
                                        R.string.netisolate_target_count,
                                        targets.size,
                                        NETISOLATE_MAX_UIDS,
                                    )
                                },
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Apps, null) },
                        enabled = canEdit,
                        onClick = { showTargets = true },
                    )
                }
            }

            if (!status.supported) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.netisolate_unsupported_summary),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            SegmentedColumn {
                item {
                    SegmentedListItem(
                        headlineContent = {
                            Text(stringResource(R.string.netisolate_existing_connections))
                        },
                        leadingContent = { Icon(Icons.Filled.Info, null) },
                    )
                }
                item(visible = status.enabled) {
                    SegmentedListItem(
                        headlineContent = {
                            Text(
                                stringResource(
                                    if (status.allowLoopback) {
                                        R.string.netisolate_allow_loopback_proxy_warning
                                    } else {
                                        R.string.netisolate_loopback_blocked
                                    },
                                ),
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Security, null) },
                    )
                }
                item {
                    SegmentedListItem(
                        headlineContent = {
                            Text(stringResource(R.string.netisolate_manager_exempt))
                        },
                        leadingContent = { Icon(Icons.Filled.Info, null) },
                    )
                }
                item {
                    SegmentedListItem(
                        headlineContent = {
                            Text(
                                stringResource(
                                    if (status.compatSupported) {
                                        R.string.netisolate_compat_supported
                                    } else {
                                        R.string.netisolate_compat_unsupported
                                    },
                                ),
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Info, null) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SegmentedColumn {
                item {
                    SegmentedListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.netisolate_clear),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        supportingContent = {
                            Text(stringResource(R.string.netisolate_clear_summary))
                        },
                        leadingContent = { Icon(Icons.Filled.DeleteSweep, null) },
                        enabled = !state.busy && !state.loading,
                        onClick = { confirmClear = true },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
