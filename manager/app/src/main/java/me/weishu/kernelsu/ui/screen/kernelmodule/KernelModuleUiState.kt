package me.weishu.kernelsu.ui.screen.kernelmodule

import android.net.Uri
import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.data.model.KernelModule
import me.weishu.kernelsu.data.model.KernelModuleLastResult

@Immutable
data class KernelModuleUiState(
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val rootAvailable: Boolean = true,
    val safeMode: Boolean = false,
    val modules: List<KernelModule> = emptyList(),
    val searchText: String = "",
    val isInstalling: Boolean = false,
    val pending: Set<String> = emptySet(),
    val autoloadPending: Set<String> = emptySet(),
    val detailModule: KernelModule? = null,
    val paramsModule: KernelModule? = null,
    val paramsDraft: String = "",
    val logModule: KernelModule? = null,
    val logResult: KernelModuleLastResult? = null,
    val isLoadingLog: Boolean = false,
    val confirmRemove: KernelModule? = null,
    val loadError: String? = null,
    val error: String? = null,
    val notice: String? = null,
) {
    val visibleModules: List<KernelModule>
        get() {
            val query = searchText.trim()
            val filtered = if (query.isBlank()) {
                modules
            } else {
                modules.filter { module ->
                    module.name.contains(query, ignoreCase = true) ||
                        module.id.contains(query, ignoreCase = true) ||
                        module.internalName.orEmpty().contains(query, ignoreCase = true) ||
                        module.authors.any { it.contains(query, ignoreCase = true) } ||
                        module.description.contains(query, ignoreCase = true)
                }
            }
            return filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
}

@Immutable
data class KernelModuleActions(
    val onRefresh: () -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onInstall: (Uri) -> Unit,
    val onToggleAutoload: (KernelModule, Boolean) -> Unit,
    val onLoad: (KernelModule) -> Unit,
    val onEnable: (KernelModule) -> Unit,
    val onDisable: (KernelModule) -> Unit,
    val onOpenDetails: (KernelModule) -> Unit,
    val onDismissDetails: () -> Unit,
    val onOpenParams: (KernelModule) -> Unit,
    val onParamsDraftChange: (String) -> Unit,
    val onSaveParams: () -> Unit,
    val onDismissParams: () -> Unit,
    val onOpenLog: (KernelModule) -> Unit,
    val onDismissLog: () -> Unit,
    val onRequestRemove: (KernelModule) -> Unit,
    val onConfirmRemove: () -> Unit,
    val onDismissRemove: () -> Unit,
    val onNoticeShown: () -> Unit,
    val onErrorShown: () -> Unit,
)
