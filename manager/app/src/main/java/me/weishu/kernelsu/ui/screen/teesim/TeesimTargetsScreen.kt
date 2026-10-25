package me.weishu.kernelsu.ui.screen.teesim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.TeesimConfig
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.SegmentedTextField
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.TeesimViewModel
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * Picks the apps whose attestation the simulator answers.
 *
 * Selection is by package name because that is what `config.json` stores. Apps that belong to a
 * profile other than `default` are left out of the list on purpose: those profiles carry their own
 * keybox, and moving an app into `default` from here would silently change what signs it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeesimTargetsScreen() {
    val viewModel = viewModel<TeesimViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults
        .exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) { viewModel.loadApps() }

    val savedApps = state.status?.targetApps.orEmpty()
    var selected by remember(savedApps) { mutableStateOf(savedApps.toSet()) }
    var query by rememberSaveable { mutableStateOf("") }

    val reserved = state.status?.otherApps.orEmpty().toSet()
    val visible = state.apps.filter { app ->
        app.packageName !in reserved &&
            (state.showSystemApps || !app.system) &&
            (query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true))
    }
    val visiblePackages = visible.map { it.packageName }
    // Saving replaces the whole app list, so an unreadable or unparsable config must not be
    // edited: an empty picker would otherwise write an empty profile over the user's selection.
    val config = state.status?.config
    val configUsable = config != null && !config.broken
    val canEdit = !state.appsLoading && !state.writing && !state.loading && configUsable

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.teesim_targets_title)) },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                actions = {
                    TextButton(
                        enabled = canEdit,
                        onClick = {
                            viewModel.setTargets(selected.toList())
                            navigator.pop()
                        },
                    ) {
                        Text(stringResource(R.string.teesim_save))
                    }
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                item {
                    SegmentedTextField(
                        label = stringResource(R.string.teesim_search_apps),
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                    )
                }
                item {
                    SwitchPreference(
            title = stringResource(R.string.teesim_show_system_apps),
            checked = state.showSystemApps,
            onCheckedChange = viewModel::setShowSystemApps,
        )
                }
            }

            // Bulk actions work on what the filter currently shows, so selecting a vendor prefix
            // stays a two-tap action instead of a scroll through the whole package list.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.teesim_selected_count, selected.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    enabled = canEdit && visiblePackages.isNotEmpty(),
                    onClick = { selected = selected + visiblePackages },
                ) {
                    Icon(
                        Icons.Filled.SelectAll,
                        contentDescription = stringResource(R.string.teesim_select_all),
                    )
                }
                IconButton(
                    enabled = canEdit && selected.isNotEmpty(),
                    onClick = { selected = selected - visiblePackages.toSet() },
                ) {
                    Icon(
                        Icons.Filled.Deselect,
                        contentDescription = stringResource(R.string.teesim_clear_selection),
                    )
                }
            }

            if (state.appsLoading || state.loading) {
                Text(
                    text = stringResource(R.string.teesim_loading),
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (!state.loading && !configUsable) {
                        item(key = "unreadable") {
                            Text(
                                text = stringResource(R.string.teesim_read_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    if (reserved.isNotEmpty()) {
                        item(key = "reserved") {
                            Text(
                                text = stringResource(
                                    R.string.teesim_targets_hint,
                                    reserved.size,
                                    TeesimConfig.DEFAULT_PROFILE,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    items(visible, key = { it.packageName }) { app ->
                        val checked = selected.contains(app.packageName)
                        SegmentedListItem(
                            enabled = canEdit,
                            onClick = {
                                selected = if (checked) {
                                    selected - app.packageName
                                } else {
                                    selected + app.packageName
                                }
                            },
                            leadingContent = {
                                AppIconImage(
                                    packageInfo = app.packageInfo,
                                    label = app.label,
                                    modifier = Modifier.size(40.dp),
                                )
                            },
                            headlineContent = { Text(app.label) },
                            supportingContent = { Text(app.packageName) },
                            trailingContent = {
                                androidx.compose.material3.Checkbox(
                                    checked = checked,
                                    // The row toggles too, so either gesture works.
                                    onCheckedChange = { nowChecked ->
                                        selected = if (nowChecked) {
                                            selected + app.packageName
                                        } else {
                                            selected - app.packageName
                                        }
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
