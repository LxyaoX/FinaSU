package me.weishu.kernelsu.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.weishu.kernelsu.data.model.NomountStatus
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl

/**
 * NoMount detail screen state.
 *
 * A failed read is kept as a flag instead of a fallback status: the screen must
 * never show a plausible-looking driver state it did not actually receive.
 */
@Immutable
data class NomountUiState(
    val isLoading: Boolean = true,
    val isWriting: Boolean = false,
    val status: NomountStatus? = null,
    val readError: Boolean = false,
    val actionError: Boolean = false,
)

class NomountViewModel(
    private val repo: SettingsRepository = SettingsRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(NomountUiState())
    val uiState: StateFlow<NomountUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isWriting) return
        _uiState.update { it.copy(isLoading = true, actionError = false) }
        viewModelScope.launch {
            val result = repo.getNomountStatus()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    status = result.getOrNull() ?: it.status,
                    readError = result.isFailure,
                )
            }
        }
    }

    fun injectRules() = runAction { repo.injectNomountRules() }

    fun clearRules() = runAction { repo.clearNomountRules() }

    fun setIsolated(enabled: Boolean) = runAction { repo.setNomountIsolated(enabled) }

    private fun runAction(block: suspend () -> Result<Unit>) {
        if (_uiState.value.isWriting) return
        _uiState.update { it.copy(isWriting = true, actionError = false) }
        viewModelScope.launch {
            val result = block()
            // Re-read instead of assuming the action produced the state we asked
            // for: the kernel may have refused part of it.
            val refreshed = repo.getNomountStatus()
            _uiState.update {
                it.copy(
                    isWriting = false,
                    actionError = result.isFailure,
                    status = refreshed.getOrNull() ?: it.status,
                    readError = refreshed.isFailure,
                )
            }
        }
    }
}
