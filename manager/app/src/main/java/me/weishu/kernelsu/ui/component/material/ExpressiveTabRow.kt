package me.weishu.kernelsu.ui.component.material

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.TabRow

/**
 * The tab strip a paged screen puts under its bar.
 *
 * This was Material's `PrimaryTabRow` with a hand-drawn ripple and a capsule indicator; miuix draws
 * its own segmented-pill strip, which is the one the rest of the app already looks like. The
 * Material-shaped signature is kept so the call site does not have to know.
 */
@Composable
fun ExpressiveTabRow(
    selectedTabIndex: Int,
    tabs: List<String>,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    TabRow(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex),
        onTabSelected = onTabClick,
        modifier = modifier,
    )
}
