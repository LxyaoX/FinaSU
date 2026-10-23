package me.weishu.kernelsu.ui.screen.netisolate

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.netisolate.NETISOLATE_MAX_UIDS
import me.weishu.kernelsu.data.netisolate.NetIsolateEvent
import me.weishu.kernelsu.data.netisolate.NetIsolateEvents
import me.weishu.kernelsu.data.netisolate.NetIsolatePolicy
import me.weishu.kernelsu.data.netisolate.NetIsolateStatus
import me.weishu.kernelsu.data.uts.UtsTarget
import me.weishu.kernelsu.data.uts.UtsTargetApp
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.screen.uts.TargetAppSheet
import me.weishu.kernelsu.ui.viewmodel.NetIsolateViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NETISOLATE_EVENT_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)

@Composable
fun NetIsolateScreen() {
    val viewModel = viewModel<NetIsolateViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showTargets by rememberSaveable { mutableStateOf(false) }
    var showPolicies by rememberSaveable { mutableStateOf(false) }
    var showEvents by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmClearEvents by rememberSaveable { mutableStateOf(false) }
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

    LifecycleResumeEffect(showEvents) {
        viewModel.refresh()
        if (showEvents) viewModel.loadEvents()
        onPauseOrDispose { }
    }

    LaunchedEffect(showTargets, showPolicies, showEvents) {
        if (showTargets || showPolicies || showEvents) viewModel.loadApps()
    }

    LaunchedEffect(showEvents) {
        if (showEvents) viewModel.loadEvents()
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

    if (confirmClearEvents) {
        AlertDialog(
            onDismissRequest = { confirmClearEvents = false },
            title = { Text(stringResource(R.string.netisolate_events_clear)) },
            text = { Text(stringResource(R.string.netisolate_events_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearEvents = false
                        viewModel.clearEvents()
                    },
                ) {
                    Text(stringResource(R.string.netisolate_events_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearEvents = false }) {
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

    if (showPolicies) {
        NetIsolatePolicySheet(
            status = status,
            targets = targets,
            apps = state.apps,
            busy = state.busy,
            onDismiss = { showPolicies = false },
            onSetPolicy = viewModel::setPolicy,
        )
    }

    if (showEvents) {
        NetIsolateEventsSheet(
            events = state.events,
            loading = state.eventsLoading && !state.eventsLoaded,
            apps = state.apps,
            onDismiss = { showEvents = false },
            onClear = { confirmClearEvents = true },
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
                item(visible = targets.isNotEmpty()) {
                    SegmentedListItem(
                        headlineContent = {
                            Text(stringResource(R.string.netisolate_app_policies))
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (status.policySupported) {
                                        R.string.netisolate_policy_summary
                                    } else {
                                        R.string.netisolate_policy_unsupported
                                    },
                                ),
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Security, null) },
                        enabled = canEdit && status.policySupported,
                        onClick = { showPolicies = true },
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
                            Text(stringResource(R.string.netisolate_events))
                        },
                        leadingContent = { Icon(Icons.Filled.History, null) },
                        onClick = { showEvents = true },
                    )
                }
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

@Composable
private fun NetIsolateEventsSheet(
    events: NetIsolateEvents,
    loading: Boolean,
    apps: List<UtsTargetApp>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    val appByUid = remember(apps) { apps.associateBy(UtsTargetApp::uid) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.netisolate_events),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (events.dropped > 0) {
                        Text(
                            text = stringResource(
                                R.string.netisolate_events_dropped,
                                events.dropped,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(
                    onClick = onClear,
                    enabled = events.supported && !loading && events.events.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.netisolate_events_clear))
                }
            }

            when {
                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(24.dp),
                    )
                }
                !events.supported -> {
                    Text(
                        text = stringResource(R.string.netisolate_events_unsupported),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                events.events.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.netisolate_events_empty),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    val displayedEvents = events.events.asReversed()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    ) {
                        itemsIndexed(
                            displayedEvents,
                            key = { _, event ->
                                "${event.timestampNanos}-${event.uid}-${event.operation}-" +
                                    "${event.address}-${event.port}"
                            },
                        ) { index, event ->
                            SegmentedItem(index = index, count = displayedEvents.size) {
                                NetIsolateEventRow(
                                    event = event,
                                    appLabel = appByUid[event.uid]?.label ?: "UID ${event.uid}",
                                    modifier = if (index < displayedEvents.lastIndex) {
                                        Modifier.padding(bottom = 2.dp)
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetIsolateEventRow(
    event: NetIsolateEvent,
    appLabel: String,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        modifier = modifier,
        headlineContent = {
            Text(
                text = "${formatNetIsolateTime(event.timestampNanos)}  " +
                    "$appLabel  ${event.operation}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = formatNetIsolateEndpoint(event),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

private fun formatNetIsolateTime(timestampNanos: Long): String {
    val ageMillis = (SystemClock.uptimeMillis() - timestampNanos / 1_000_000).coerceAtLeast(0)
    val epochMillis = System.currentTimeMillis() - ageMillis
    return NETISOLATE_EVENT_TIME.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
    )
}

private fun formatNetIsolateEndpoint(event: NetIsolateEvent): String {
    if (event.address.isBlank()) {
        return when (event.family) {
            "inet" -> "AF_INET"
            "inet6" -> "AF_INET6"
            else -> event.family.uppercase(Locale.ROOT)
        }
    }
    if (event.port <= 0) return event.address
    return if (event.family == "inet6") {
        "[${event.address}]:${event.port}"
    } else {
        "${event.address}:${event.port}"
    }
}

@Composable
private fun NetIsolatePolicySheet(
    status: NetIsolateStatus,
    targets: List<UtsTarget>,
    apps: List<UtsTargetApp>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSetPolicy: (uid: Int, strictSocket: Boolean, allowLoopback: Boolean) -> Unit,
) {
    val appByUid = remember(apps) { apps.associateBy(UtsTargetApp::uid) }
    val policiesByUid = remember(status.policies) {
        status.policies.associateBy(NetIsolatePolicy::uid)
    }
    var expandedUid by rememberSaveable { mutableStateOf<Int?>(null) }
    val orderedTargets = remember(targets) { targets.sortedBy(UtsTarget::uid) }
    val defaultLabel = stringResource(R.string.netisolate_policy_default)
    val loopbackLabel = stringResource(R.string.netisolate_policy_loopback)
    val strictLabel = stringResource(R.string.netisolate_policy_strict)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.netisolate_app_policies),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.netisolate_policy_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                items(orderedTargets, key = UtsTarget::uid) { target ->
                    val app = appByUid[target.uid]
                    val policy = policiesByUid[target.uid] ?: NetIsolatePolicy(
                        uid = target.uid,
                        strictSocket = status.strictSocket,
                        allowLoopback = status.allowLoopback,
                    )
                    val expanded = expandedUid == target.uid
                    val policyLabel = when {
                        policy.strictSocket -> strictLabel
                        policy.allowLoopback -> loopbackLabel
                        else -> defaultLabel
                    }
                    val policyBackground = when {
                        policy.strictSocket -> MaterialTheme.colorScheme.errorContainer
                        policy.allowLoopback -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val policyForeground = when {
                        policy.strictSocket -> MaterialTheme.colorScheme.onErrorContainer
                        policy.allowLoopback -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onPrimary
                    }

                    SegmentedColumn {
                        item(key = "app-${target.uid}") {
                            SegmentedListItem(
                                selected = expanded,
                                onClick = {
                                    expandedUid = if (expanded) null else target.uid
                                },
                                headlineContent = {
                                    Text(
                                        text = app?.label ?: target.packageName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = app?.packageName ?: target.packageName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingContent = {
                                    app?.packageInfo?.let { packageInfo ->
                                        AppIconImage(
                                            packageInfo = packageInfo,
                                            label = app.label,
                                            modifier = Modifier.size(48.dp),
                                        )
                                    } ?: run {
                                        Icon(
                                            imageVector = Icons.Filled.Apps,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp),
                                        )
                                    }
                                },
                                trailingContent = {
                                    StatusTag(
                                        label = policyLabel,
                                        backgroundColor = policyBackground,
                                        contentColor = policyForeground,
                                    )
                                },
                            )
                        }
                        item(key = "default-${target.uid}", visible = expanded) {
                            PolicyChoice(
                                title = defaultLabel,
                                selected = !policy.strictSocket && !policy.allowLoopback,
                                enabled = !busy,
                                onClick = { onSetPolicy(target.uid, false, false) },
                            )
                        }
                        item(key = "loopback-${target.uid}", visible = expanded) {
                            PolicyChoice(
                                title = loopbackLabel,
                                selected = policy.allowLoopback,
                                enabled = !busy && status.allowLoopbackSupported,
                                onClick = { onSetPolicy(target.uid, false, true) },
                            )
                        }
                        item(key = "strict-${target.uid}", visible = expanded) {
                            PolicyChoice(
                                title = strictLabel,
                                selected = policy.strictSocket,
                                enabled = !busy && status.strictSocketSupported,
                                onClick = { onSetPolicy(target.uid, true, false) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyChoice(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
            )
        },
        enabled = enabled,
        onClick = onClick,
    )
}
