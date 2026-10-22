package me.weishu.kernelsu.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.KernelModule
import me.weishu.kernelsu.data.repository.KernelModuleRepository
import me.weishu.kernelsu.data.repository.KernelModuleRepositoryImpl
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.modulebanner.LkmBannerSlot
import me.weishu.kernelsu.modulebanner.ModuleBannerCache
import me.weishu.kernelsu.ui.screen.kernelmodule.KernelModuleUiState
import me.weishu.kernelsu.ui.screen.kernelmodule.stageKernelModule
import me.weishu.kernelsu.ui.util.rootAvailable

class KernelModuleViewModel(
    private val repo: KernelModuleRepository = KernelModuleRepositoryImpl(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(KernelModuleUiState())
    val uiState: StateFlow<KernelModuleUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var logJob: Job? = null

    fun ensureLoaded() {
        if (!_uiState.value.hasLoaded && loadJob?.isActive != true) refresh(showIndicator = false)
    }

    fun refresh(showIndicator: Boolean = true) {
        if (loadJob?.isActive == true) return
        _uiState.update {
            it.copy(isRefreshing = showIndicator, loadError = null, error = null)
        }
        loadJob = viewModelScope.launch {
            if (!rootAvailable()) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        hasLoaded = true,
                        rootAvailable = false,
                    )
                }
                return@launch
            }
            repo.getModules()
                .onSuccess { collection ->
                    ModuleBannerCache.prune(LkmBannerSlot, collection.modules.map { it.id }.toSet())
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            hasLoaded = true,
                            rootAvailable = true,
                            loadError = null,
                            safeMode = collection.safeMode,
                            modules = collection.modules,
                        )
                    }
                }
                .onFailure(::fail)
        }
    }

    fun updateSearchText(text: String) {
        _uiState.update { it.copy(searchText = text) }
    }

    fun install(uri: Uri) {
        if (_uiState.value.isInstalling) return
        _uiState.update { it.copy(isInstalling = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                val path = withContext(Dispatchers.IO) { stageKernelModule(ksuApp, uri) }
                repo.install(path).getOrThrow()
            }
            _uiState.update { it.copy(isInstalling = false) }
            result
                .onSuccess {
                    _uiState.update {
                        it.copy(notice = ksuApp.getString(R.string.kernel_modules_installed))
                    }
                    reload()
                }
                .onFailure(::fail)
        }
    }

    fun toggleAutoload(module: KernelModule, enabled: Boolean) {
        val state = _uiState.value
        if (module.id in state.pending || module.id in state.autoloadPending) return
        _uiState.update {
            it.copy(autoloadPending = it.autoloadPending + module.id, error = null)
        }
        viewModelScope.launch {
            runCatching { repo.setAutoload(module.id, enabled).getOrThrow() }
                .onFailure(::fail)
            reload()
            _uiState.update { it.copy(autoloadPending = it.autoloadPending - module.id) }
        }
    }

    fun load(module: KernelModule) {
        if (module.id in _uiState.value.pending) return
        runModuleAction(
            module.id,
            action = { repo.load(module.id).getOrThrow() },
        )
    }

    fun enable(module: KernelModule) {
        if (module.id in _uiState.value.pending) return
        runModuleAction(
            id = module.id,
            action = { repo.enable(module.id).getOrThrow() },
            onSuccess = { _uiState.update { it.copy(detailModule = null) } },
        )
    }

    fun disable(module: KernelModule) {
        if (module.id in _uiState.value.pending) return
        runModuleAction(
            id = module.id,
            action = { repo.disable(module.id).getOrThrow() },
            onSuccess = { _uiState.update { it.copy(detailModule = null) } },
        )
    }

    fun requestRemove(module: KernelModule) {
        _uiState.update { it.copy(confirmRemove = module) }
    }

    fun confirmRemove() {
        val module = _uiState.value.confirmRemove ?: return
        _uiState.update { it.copy(confirmRemove = null) }
        runModuleAction(
            module.id,
            action = { repo.remove(module.id).getOrThrow() },
        )
    }

    fun dismissRemove() {
        _uiState.update { it.copy(confirmRemove = null) }
    }

    fun openDetails(module: KernelModule) {
        _uiState.update { it.copy(detailModule = module) }
    }

    fun dismissDetails() {
        _uiState.update { it.copy(detailModule = null) }
    }

    fun openParams(module: KernelModule) {
        _uiState.update {
            it.copy(paramsModule = module, paramsDraft = module.params)
        }
    }

    fun updateParamsDraft(value: String) {
        _uiState.update { it.copy(paramsDraft = value) }
    }

    fun saveParams() {
        val module = _uiState.value.paramsModule ?: return
        val params = _uiState.value.paramsDraft
        runModuleAction(
            id = module.id,
            action = { repo.setParams(module.id, params).getOrThrow() },
            onSuccess = { _uiState.update { it.copy(paramsModule = null) } },
        )
    }

    fun dismissParams() {
        _uiState.update { it.copy(paramsModule = null, paramsDraft = "") }
    }

    fun openLog(module: KernelModule) {
        logJob?.cancel()
        _uiState.update {
            it.copy(logModule = module, logResult = null, isLoadingLog = true)
        }
        logJob = viewModelScope.launch {
            repo.getLog(module.id)
                .onSuccess { result ->
                    _uiState.update {
                        if (it.logModule?.id == module.id) {
                            it.copy(logResult = result, isLoadingLog = false)
                        } else {
                            it
                        }
                    }
                }
                .onFailure { error ->
                    if (_uiState.value.logModule?.id == module.id) {
                        _uiState.update {
                            it.copy(isLoadingLog = false, logModule = null, logResult = null)
                        }
                        fail(error)
                    }
                }
        }
    }

    fun dismissLog() {
        logJob?.cancel()
        logJob = null
        _uiState.update { it.copy(logModule = null, logResult = null, isLoadingLog = false) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun runModuleAction(
        id: String,
        action: suspend () -> Unit,
        onSuccess: () -> Unit = {},
    ) {
        _uiState.update { it.copy(pending = it.pending + id, error = null) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { onSuccess() }
                .onFailure(::fail)
            _uiState.update { it.copy(pending = it.pending - id) }
            reload()
        }
    }

    private suspend fun reload() {
        if (!rootAvailable()) return
        repo.getModules()
            .onSuccess { collection ->
                ModuleBannerCache.prune(LkmBannerSlot, collection.modules.map { it.id }.toSet())
                _uiState.update {
                    it.copy(
                        hasLoaded = true,
                        rootAvailable = true,
                        loadError = null,
                        safeMode = collection.safeMode,
                        modules = collection.modules,
                    )
                }
            }
            .onFailure(::fail)
    }

    private fun fail(error: Throwable) {
        val message = error.message ?: error.toString()
        _uiState.update {
            it.copy(
                isRefreshing = false,
                isInstalling = false,
                loadError = if (!it.hasLoaded) message else it.loadError,
                error = message,
            )
        }
    }
}
