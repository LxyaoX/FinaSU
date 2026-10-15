package me.weishu.kernelsu.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.weishu.kernelsu.data.model.SuiApp
import me.weishu.kernelsu.data.model.SuiMode
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.data.sui.SuiAdbRootMode
import me.weishu.kernelsu.data.sui.SuiBridge

/** One-shot messages the screen shows once and then clears. */
enum class SuiNotice {
    REBOOT_REQUIRED,
}

data class SuiPermissionsUiState(
    val apps: List<SuiApp> = emptyList(),
    val visible: List<SuiApp> = emptyList(),
    val search: String = "",
    val searchResults: List<SuiApp> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val busy: Boolean = false,
    /** Null until Sui answers, so an unreadable value is never shown as a setting. */
    val settingsFlags: Int? = null,
    val defaultMode: SuiMode = SuiMode.DEFAULT,
    val hiddenCount: Int? = null,
    val error: String? = null,
    val notice: SuiNotice? = null,
) {
    val showOnlyShizukuApps: Boolean
        get() = settingsFlags?.and(SuiBridge.FLAG_SHOW_ONLY_SHIZUKU_APPS) != 0

    val legacyBinderCompat: Boolean
        get() = settingsFlags?.and(SuiBridge.FLAG_LEGACY_SHIZUKU_BINDER_COMPAT) != 0

    val ksuNoEscape: Boolean
        get() = settingsFlags?.and(SuiBridge.FLAG_KSU_NO_ESCAPE) != 0

    val adbRootMode: SuiAdbRootMode
        get() = settingsFlags?.let(SuiAdbRootMode::of) ?: SuiAdbRootMode.OFF
}

/** The list Sui's own manager shows for "Show only Shizuku apps". */
internal fun visibleSuiApps(apps: List<SuiApp>, onlyShizukuApps: Boolean): List<SuiApp> =
    if (onlyShizukuApps) apps.filter { it.isShizukuApp } else apps

internal fun searchSuiApps(apps: List<SuiApp>, query: String): List<SuiApp> {
    if (query.isBlank()) return apps
    return apps.filter {
        it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
    }
}

/** Per-app Shizuku permissions, read and written through the verified Sui management channel. */
class SuiPermissionsViewModel(
    private val repo: SettingsRepository = SettingsRepositoryImpl(),
) : ViewModel() {
    private companion object {
        const val TAG = "SuiManagement"
    }

    private val _uiState = MutableStateFlow(SuiPermissionsUiState())
    val uiState: StateFlow<SuiPermissionsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        _uiState.update { it.copy(loading = !refresh, refreshing = refresh, error = null) }
        viewModelScope.launch {
            val result = repo.suiAppPermissions()
            val flags = repo.suiSettingsFlags().getOrNull()
            val default = repo.suiDefaultMode().getOrNull()
            val hidden = repo.suiHiddenUids().getOrNull()?.size
            Log.i(TAG, "permission list: ${result.getOrNull()?.size ?: -1} apps")
            _uiState.update { state ->
                state.withLists(
                    apps = result.getOrNull() ?: state.apps,
                    flags = flags ?: state.settingsFlags,
                ).copy(
                    loading = false,
                    refreshing = false,
                    defaultMode = default?.let(SuiMode::of) ?: state.defaultMode,
                    hiddenCount = hidden,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun setSearch(text: String) {
        _uiState.update { it.withLists(search = text) }
    }

    fun setShowOnlyShizukuApps(enabled: Boolean) {
        writeFlag(SuiBridge.FLAG_SHOW_ONLY_SHIZUKU_APPS, enabled)
    }

    fun setLegacyBinderCompat(enabled: Boolean) {
        writeFlag(SuiBridge.FLAG_LEGACY_SHIZUKU_BINDER_COMPAT, enabled)
    }

    fun setKsuNoEscape(enabled: Boolean) {
        writeFlag(SuiBridge.FLAG_KSU_NO_ESCAPE, enabled, notice = SuiNotice.REBOOT_REQUIRED)
    }

    fun setAdbRootMode(mode: SuiAdbRootMode) {
        val previous = _uiState.value.settingsFlags ?: return
        val updated = previous and SuiBridge.MASK_ADB_ROOT.inv() or mode.flags
        writeSettings(previous, updated, SuiNotice.REBOOT_REQUIRED)
    }

    /** The permission used for apps without a rule; Sui refreshes those clients by itself. */
    fun setDefaultMode(mode: SuiMode) {
        val previous = _uiState.value.defaultMode
        if (previous == mode) return
        _uiState.update { it.copy(defaultMode = mode) }
        viewModelScope.launch {
            val result = repo.setSuiDefaultMode(mode.flags)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(defaultMode = previous, error = result.exceptionOrNull()?.message)
                }
            }
        }
    }

    fun setMode(app: SuiApp, mode: SuiMode) {
        if (_uiState.value.busy || app.suiMode == mode) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = repo.setSuiAppMode(app.uid, mode.flags)
            _uiState.update { state ->
                if (result.isFailure) {
                    state.copy(busy = false, error = result.exceptionOrNull()?.message)
                } else {
                    state.withLists(
                        apps = state.apps.map { if (it.uid == app.uid) it.copy(mode = mode.flags) else it },
                    ).copy(busy = false)
                }
            }
        }
    }

    fun consumeNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private fun writeFlag(flag: Int, enabled: Boolean, notice: SuiNotice? = null) {
        val previous = _uiState.value.settingsFlags ?: return
        val updated = if (enabled) previous or flag else previous and flag.inv()
        writeSettings(previous, updated, notice)
    }

    private fun writeSettings(previous: Int, updated: Int, notice: SuiNotice?) {
        if (updated == previous) return
        _uiState.update { it.withLists(flags = updated).copy(error = null) }
        viewModelScope.launch {
            val result = repo.setSuiSettingsFlags(updated)
            if (result.isFailure) {
                _uiState.update {
                    it.withLists(flags = previous).copy(error = result.exceptionOrNull()?.message)
                }
            } else if (notice != null) {
                _uiState.update { it.copy(notice = notice) }
            }
        }
    }

    /** Rebuilds the two lists the screen draws from one place, so they can never disagree. */
    private fun SuiPermissionsUiState.withLists(
        apps: List<SuiApp> = this.apps,
        search: String = this.search,
        flags: Int? = this.settingsFlags,
    ): SuiPermissionsUiState {
        val visible = visibleSuiApps(apps, flags?.and(SuiBridge.FLAG_SHOW_ONLY_SHIZUKU_APPS) != 0)
        return copy(
            apps = apps,
            search = search,
            settingsFlags = flags,
            visible = visible,
            searchResults = searchSuiApps(visible, search),
        )
    }
}
