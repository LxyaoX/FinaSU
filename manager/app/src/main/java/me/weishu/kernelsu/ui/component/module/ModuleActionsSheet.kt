package me.weishu.kernelsu.ui.component.module

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.component.material.FolkBottomSheet
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem

@Immutable
data class ModuleCardAction(
    val icon: ImageVector,
    val title: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleActionsSheet(
    show: Boolean,
    title: String,
    actions: List<ModuleCardAction>,
    onDismiss: () -> Unit,
) {
    if (!show) return

    FolkBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            SegmentedColumn {
                actions.forEachIndexed { index, action ->
                    item(key = index) {
                        val contentColor = if (action.destructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        SegmentedListItem(
                            onClick = if (action.enabled) {
                                {
                                    onDismiss()
                                    action.onClick()
                                }
                            } else {
                                null
                            },
                            onLongClick = action.onLongClick,
                            enabled = action.enabled,
                            headlineContent = {
                                Text(action.title, color = contentColor)
                            },
                            supportingContent = action.description?.let { description ->
                                { Text(description) }
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = null,
                                    tint = contentColor,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
