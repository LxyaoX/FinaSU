package me.weishu.kernelsu.ui.component.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A bottom sheet with Material's mechanics and miuix's geometry.
 *
 * The sheet keeps sliding, dragging and nested scrolling from [ModalBottomSheet], because those are
 * the parts a caller drives through its [SheetState] and none of the app's sheets were written for a
 * different state model. What changes is everything the eye sees: miuix's background role instead of
 * Material's tonal surface, its 28dp top corners, no tonal elevation and a miuix drag handle, so a
 * sheet no longer reads as the one Material component left on the page.
 *
 * @param containerColor overrides the fill; the miuix background is used when left unspecified.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolkBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = if (containerColor == Color.Unspecified) {
            MiuixTheme.colorScheme.background
        } else {
            containerColor
        },
        scrimColor = MiuixTheme.colorScheme.windowDimming,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { FolkDragHandle() },
        content = content,
    )
}

/** The thin bar miuix puts on top of a sheet: 45dp wide, 4dp tall, faded into the surface. */
@Composable
private fun FolkDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(45.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.2f)),
        )
    }
}
