package me.weishu.kernelsu.ui.screen.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.theme.FolkShape

/** A full-width tonal surface, the one shape every card on the home screen shares. */
@Composable
internal fun HomeCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    contentColor: Color = contentColorFor(containerColor),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    TonalCard(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = FolkShape.Corner20,
        cornerRadius = 20.dp,
        onClick = onClick,
        onLongClick = onLongClick,
        navOriginRadius = 20.dp,
        content = content,
    )
}
