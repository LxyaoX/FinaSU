package me.weishu.kernelsu.ui.screen.uts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.uts.UtsTarget
import me.weishu.kernelsu.data.uts.UtsTargetApp
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.material.FolkFilledTonalButton
import me.weishu.kernelsu.ui.component.material.FolkIconButton
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.SegmentedTextField
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun TargetAppSheet(
    apps: List<UtsTargetApp>,
    selectedTargets: List<UtsTarget>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<UtsTarget>) -> Unit,
    maxTargets: Int,
    title: String,
    countText: @Composable (Int, Int) -> String,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showSystemApps by rememberSaveable { mutableStateOf(false) }
    var selected by remember(selectedTargets) {
        mutableStateOf(selectedTargets.associateBy { it.uid })
    }
    val allApps = remember(apps, selectedTargets) {
        val loadedUids = apps.mapTo(mutableSetOf()) { it.uid }
        apps + selectedTargets
            .filter { it.uid !in loadedUids }
            .map { target ->
                UtsTargetApp(
                    label = target.packageName,
                    packageName = target.packageName,
                    uid = target.uid,
                    isSystem = false,
                    packageInfo = null,
                    missing = true,
                )
            }
    }
    val visible = remember(allApps, query, showSystemApps) {
        allApps.filter { app ->
            (app.missing || showSystemApps || !app.isSystem) &&
                (query.isBlank() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true))
        }
    }

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
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = countText(selected.size, maxTargets),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FolkIconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }

            SegmentedColumn {
                item {
                    SegmentedTextField(
                        label = stringResource(R.string.uts_view_search_apps),
                        value = query,
                        onValueChange = { query = it },
                        leadingContent = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                    )
                }
                item {
                    SwitchPreference(
            title = stringResource(R.string.uts_view_show_system_apps),
            checked = showSystemApps,
            onCheckedChange = { showSystemApps = it },
        )
                }
            }

            if (loading) {
                Text(
                    text = stringResource(R.string.uts_view_loading_apps),
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(visible, key = { _, app -> app.uid }) { index, app ->
                        val checked = selected.containsKey(app.uid)
                        val canSelect = checked || selected.size < maxTargets
                        SegmentedItem(index = index, count = visible.size) {
                            SegmentedListItem(
                                selected = checked,
                                enabled = canSelect,
                                onClick = {
                                    selected = selected.toMutableMap().apply {
                                        if (checked) {
                                            remove(app.uid)
                                        } else if (size < maxTargets) {
                                            put(
                                                app.uid,
                                                UtsTarget(
                                                    packageName = app.packageName,
                                                    uid = app.uid,
                                                ),
                                            )
                                        }
                                    }
                                },
                                leadingContent = {
                                    app.packageInfo?.let { packageInfo ->
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
                                headlineContent = {
                                    Text(
                                        text = app.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = stringResource(
                                            if (app.missing) {
                                                R.string.uts_view_target_missing
                                            } else {
                                                R.string.uts_view_target_app_summary
                                            },
                                            app.packageName,
                                            app.uid,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    Checkbox(
                                        checked = checked,
                                        enabled = canSelect,
                                        onCheckedChange = { nowChecked ->
                                            selected = selected.toMutableMap().apply {
                                                if (nowChecked) {
                                                    put(
                                                        app.uid,
                                                        UtsTarget(
                                                            packageName = app.packageName,
                                                            uid = app.uid,
                                                        ),
                                                    )
                                                } else {
                                                    remove(app.uid)
                                                }
                                            }
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }

            FolkFilledTonalButton(
                onClick = { onConfirm(selected.values.toList()) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.uts_view_apply_targets))
            }
        }
    }
}
