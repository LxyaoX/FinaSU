package me.weishu.kernelsu.ui.screen.nomount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.SegmentedTextField
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.NomountViewModel

/**
 * Pick the apps that must see the stock filesystem.
 *
 * The selection is a list of UIDs: an app with a shared UID selects every app
 * that reports it, which is why rows show the UID next to the label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NomountExclusionsScreen() {
    val viewModel = viewModel<NomountViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults
        .exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) { viewModel.loadApps() }

    // The selection starts from what the kernel policy stores and is applied
    // only when the user saves.
    var selected by remember(state.status?.uids) {
        mutableStateOf(state.status?.uids?.toSet() ?: emptySet())
    }
    var query by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val visible = state.apps.filter { app ->
        (state.showSystemApps || !app.isSystem) &&
            (query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true))
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nomount_exclusions_title)) },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                actions = {
                    TextButton(
                        enabled = !state.isWriting,
                        onClick = {
                            viewModel.saveExclusions(selected.toList())
                            navigator.pop()
                        },
                    ) {
                        Text(stringResource(R.string.nomount_save))
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
                        label = stringResource(R.string.nomount_search_apps),
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                    )
                }
                item {
                    SegmentedSwitchItem(
                        title = stringResource(R.string.nomount_show_system_apps),
                        checked = state.showSystemApps,
                        onCheckedChange = viewModel::setShowSystemApps,
                    )
                }
                item {
                    SegmentedListItem(
                        headlineContent = {
                            Text(stringResource(R.string.nomount_follow_profiles))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.nomount_follow_profiles_summary))
                        },
                        enabled = !state.appsLoading && !state.isWriting,
                        onClick = {
                            scope.launch {
                                selected = selected +
                                    viewModel.profileExcludedUids(state.showSystemApps)
                            }
                        },
                    )
                }
            }

            if (state.appsLoading) {
                Text(
                    text = stringResource(R.string.nomount_loading),
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item {
                        Text(
                            text = stringResource(
                                R.string.nomount_selected_count,
                                selected.size,
                            ),
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                        )
                    }
                    items(visible, key = { it.uid }) { app ->
                        val checked = selected.contains(app.uid)
                        SegmentedListItem(
                            enabled = !state.isWriting,
                            onClick = {
                                selected = if (checked) {
                                    selected + app.uid
                                } else {
                                    selected - app.uid
                                }
                            },
                            headlineContent = { Text(app.label) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.nomount_app_uid,
                                        app.packageName,
                                        app.uid,
                                    )
                                )
                            },
                            trailingContent = {
                                androidx.compose.material3.Checkbox(
                                    checked = checked,
                                    // The checkbox is its own touch target; the row
                                    // toggles too, so either gesture works.
                                    onCheckedChange = { nowChecked ->
                                        selected = if (nowChecked) {
                                            selected + app.uid
                                        } else {
                                            selected - app.uid
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
