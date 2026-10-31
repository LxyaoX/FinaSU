package me.weishu.kernelsu.ui.component.material

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The page frame every screen shares: a miuix scaffold whose bars, container and content colours all
 * come from the miuix scheme.
 *
 * It used to wrap Material's scaffold, which painted its own `surfaceContainer` behind every page;
 * the bar sitting on it was already miuix, so the page and its own header came from two different
 * palettes. The signature stays the Material-shaped one the screens already call.
 */
@Composable
fun ExpressiveScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = MiuixTheme.colorScheme.surface,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    MiuixScaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = containerColor,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

@Composable
fun expressiveTopAppBarColors(
    containerColor: Color = MiuixTheme.colorScheme.surface,
    scrolledContainerColor: Color = containerColor,
): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = containerColor,
    scrolledContainerColor = scrolledContainerColor,
)
