package me.weishu.kernelsu.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.netisolate.NetIsolateDataSource
import me.weishu.kernelsu.data.netisolate.NetIsolateRepository
import me.weishu.kernelsu.data.netisolate.NetIsolateStatus
import me.weishu.kernelsu.data.uts.UtsTarget
import me.weishu.kernelsu.data.uts.UtsTargetApp
import me.weishu.kernelsu.ui.util.RootShell
import me.weishu.kernelsu.ksuApp

@Immutable
data class NetIsolateUiState(
    val status: NetIsolateStatus = NetIsolateStatus(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val hasLoaded: Boolean = false,
    val message: String? = null,
    val apps: List<UtsTargetApp> = emptyList(),
    val appsLoading: Boolean = false,
    val appsLoaded: Boolean = false,
)

class NetIsolateViewModel(
    private val repository: NetIsolateDataSource = NetIsolateRepository(),
    private val shellGeneration: () -> Long = { RootShell.generation.value },
    private val getString: (Int) -> String = { ksuApp.getString(it) },
) : ViewModel() {
    private val _uiState = MutableStateFlow(NetIsolateUiState())
    val uiState: StateFlow<NetIsolateUiState> = _uiState.asStateFlow()
    private var requestToken = 0L

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.busy) return
        val request = ++requestToken
        val generation = shellGeneration()
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val status = repository.status()
                if (request != requestToken || generation != shellGeneration()) return@launch
                _uiState.update { it.copy(status = status, hasLoaded = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == requestToken && generation == shellGeneration()) {
                    _uiState.update {
                        it.copy(message = e.message ?: getString(R.string.netisolate_status_failed))
                    }
                }
            } finally {
                if (request == requestToken) _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        val status = _uiState.value.status
        if (enabled && status.uids.isEmpty()) {
            _uiState.update { it.copy(message = getString(R.string.netisolate_enter_targets)) }
            return
        }
        operate {
            if (enabled) {
                repository.save(
                    true,
                    status.uids,
                    status.strictSocket,
                    status.allowLoopback,
                )
            } else {
                repository.disable()
            }
        }
    }

    fun setTargets(targets: List<UtsTarget>) {
        val status = _uiState.value.status
        val uids = targets.map(UtsTarget::uid).distinct()
        if (status.enabled && uids.isEmpty()) {
            _uiState.update { it.copy(message = getString(R.string.netisolate_enter_targets)) }
            return
        }
        operate {
            repository.save(
                status.enabled,
                uids,
                status.strictSocket,
                status.allowLoopback,
            )
        }
    }

    fun setStrictSocket(strictSocket: Boolean) {
        val status = _uiState.value.status
        if (strictSocket && !status.strictSocketSupported) {
            _uiState.update {
                it.copy(message = getString(R.string.netisolate_strict_socket_unsupported))
            }
            return
        }
        operate {
            repository.save(
                status.enabled,
                status.uids,
                strictSocket,
                if (strictSocket) false else status.allowLoopback,
            )
        }
    }

    fun setAllowLoopback(allowLoopback: Boolean) {
        val status = _uiState.value.status
        if (allowLoopback && !status.allowLoopbackSupported) {
            _uiState.update {
                it.copy(message = getString(R.string.netisolate_allow_loopback_unsupported))
            }
            return
        }
        operate {
            repository.save(
                status.enabled,
                status.uids,
                if (allowLoopback) false else status.strictSocket,
                allowLoopback,
            )
        }
    }

    fun clear() {
        operate { repository.clear() }
    }

    fun loadApps() {
        if (_uiState.value.appsLoading) return
        _uiState.update { it.copy(appsLoading = true) }
        viewModelScope.launch {
            try {
                val apps = repository.loadApps()
                _uiState.update { it.copy(apps = apps, appsLoaded = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = e.message ?: getString(R.string.netisolate_apps_failed))
                }
            } finally {
                _uiState.update { it.copy(appsLoading = false) }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun operate(block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        val request = ++requestToken
        val generation = shellGeneration()
        _uiState.update { it.copy(busy = true, loading = false, message = null) }
        viewModelScope.launch {
            var refreshAfterFailure = false
            try {
                block()
                val status = repository.status()
                if (request != requestToken || generation != shellGeneration()) return@launch
                _uiState.update {
                    it.copy(
                        status = status,
                        hasLoaded = true,
                        message = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refreshAfterFailure = true
                if (request == requestToken) {
                    _uiState.update {
                        it.copy(
                            message = e.message ?: getString(R.string.netisolate_operation_failed),
                        )
                    }
                }
            } finally {
                if (request == requestToken) {
                    _uiState.update { it.copy(busy = false) }
                    if (refreshAfterFailure) refresh()
                }
            }
        }
    }
}
