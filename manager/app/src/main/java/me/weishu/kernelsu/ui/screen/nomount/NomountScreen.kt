package me.weishu.kernelsu.ui.screen.nomount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.NomountDriverState
import me.weishu.kernelsu.data.model.NomountStatus
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.NomountUiState
import me.weishu.kernelsu.ui.viewmodel.NomountViewModel

/**
 * NoMount driver, injection and visibility state.
 *
 * Every row that can look healthy is gated on the driver actually answering:
 * an unusable driver disables the controls instead of offering actions that
 * would silently do nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NomountScreen() {
    val viewModel = viewModel<NomountViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults
        .exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_nomount)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DriverSection(state, viewModel)
            InjectionSection(state, viewModel)
            VisibilitySection(state, viewModel)
        }
    }
}

@Composable
private fun DriverSection(state: NomountUiState, viewModel: NomountViewModel) {
    SegmentedColumn(title = stringResource(R.string.nomount_section_driver)) {
        item {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.nomount_driver_state)) },
                supportingContent = { Text(driverSummary(state)) },
                leadingContent = { Icon(Icons.Filled.Layers, null) },
                onClick = viewModel::refresh,
                trailingContent = { Icon(Icons.Filled.Refresh, null) },
            )
        }
    }
}

@Composable
private fun driverSummary(state: NomountUiState): String {
    if (state.readError) return stringResource(R.string.nomount_read_error)
    val status = state.status ?: return stringResource(R.string.nomount_loading)
    val label = stringResource(
        when (status.driverState) {
            NomountDriverState.ABSENT -> R.string.nomount_driver_absent
            NomountDriverState.OUTDATED -> R.string.nomount_driver_outdated
            NomountDriverState.READY -> R.string.nomount_driver_ready
        }
    )
    val version = status.driverVersion
    return if (version.isNullOrEmpty()) label else "$label ($version)"
}

@Composable
private fun InjectionSection(state: NomountUiState, viewModel: NomountViewModel) {
    val status = state.status
    val ready = status?.apiOk == true && !state.isWriting
    SegmentedColumn(title = stringResource(R.string.nomount_section_injection)) {
        item {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.nomount_rules)) },
                supportingContent = { Text(rulesSummary(status)) },
                leadingContent = { Icon(Icons.Filled.PlayArrow, null) },
                enabled = ready,
                onClick = viewModel::injectRules,
                trailingContent = { Icon(Icons.Filled.PlayArrow, null) },
            )
        }
        item {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.nomount_clear_rules)) },
                supportingContent = { Text(stringResource(R.string.nomount_clear_rules_summary)) },
                leadingContent = { Icon(Icons.Filled.DeleteSweep, null) },
                enabled = ready,
                onClick = viewModel::clearRules,
            )
        }
        item(visible = state.actionError) {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.nomount_action_failed)) },
                supportingContent = {
                    Text(stringResource(R.string.nomount_action_failed_summary))
                },
                leadingContent = { Icon(Icons.Filled.Refresh, null) },
                enabled = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun rulesSummary(status: NomountStatus?): String {
    val state = status ?: return stringResource(R.string.nomount_loading)
    val summary = stringResource(
        R.string.nomount_rules_value,
        state.rulesApplied,
        state.rulesExpected,
    )
    return if (state.injected) summary else "$summary\n${stringResource(R.string.nomount_not_injected)}"
}

@Composable
private fun VisibilitySection(state: NomountUiState, viewModel: NomountViewModel) {
    val status = state.status
    val ready = status?.apiOk == true && !state.isWriting
    SegmentedColumn(title = stringResource(R.string.nomount_section_visibility)) {
        item {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.nomount_uid_exclusions)) },
                supportingContent = {
                    Text(stringResource(R.string.nomount_uid_exclusions_value, status?.uidCount ?: 0))
                },
                overlineContent = { Text(stringResource(R.string.nomount_uid_source_hint)) },
                leadingContent = { Icon(Icons.Filled.VisibilityOff, null) },
                enabled = false,
                onClick = {},
            )
        }
        item {
            SegmentedSwitchItem(
                icon = Icons.Filled.VisibilityOff,
                title = stringResource(R.string.nomount_block_isolated),
                summary = stringResource(R.string.nomount_block_isolated_summary),
                checked = status?.isolatedBlocked == true,
                enabled = ready,
                onCheckedChange = viewModel::setIsolated,
            )
        }
    }
}
