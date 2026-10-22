package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Composable
import me.weishu.kernelsu.ui.util.useFullFeaturedLayout

@Composable
internal fun CircleHomeContent(
    state: HomeUiState,
    actions: HomeActions,
    superuserCount: Int,
    moduleEnabledCount: Int,
) {
    StatusCard(
        state = state,
        actions = actions,
    )
    // The counts only exist on a device where the kernel module is present.
    if (useFullFeaturedLayout()) {
        CountCardPair(
            superuserCount = superuserCount,
            moduleEnabledCount = moduleEnabledCount,
            onOpenSuperUser = actions.onOpenSuperUser,
            onOpenModule = actions.onOpenModule,
            layout = CountCardLayout.Horizontal,
        )
    }
}
