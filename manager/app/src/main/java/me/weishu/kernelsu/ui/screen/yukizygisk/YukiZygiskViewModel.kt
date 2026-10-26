package me.weishu.kernelsu.ui.screen.yukizygisk

import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.YukiZygiskStatus
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.execKsud
import me.weishu.kernelsu.ui.util.execKsudResult

/**
 * Backs the YukiZygisk page: `ksud yukizygisk status` for what the engine is doing, and
 * `ksud yukizygisk set` for the settings document it reads at startup.
 *
 * Every write restarts the daemons, because the engine reads `yzconfig.json` once when it starts.
 * That is what makes a toggle visible immediately instead of "after the next reboot".
 */
class YukiZygiskViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val status: YukiZygiskStatus? = null,
        val readFailed: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) {
                val result = execKsudResult("yukizygisk status")
                if (!result.isSuccess) {
                    null
                } else {
                    YukiZygiskStatus.fromJson(result.out.joinToString("\n"))
                }
            }
            _uiState.value = UiState(loading = false, status = status, readFailed = status == null)
        }
    }

    fun setOption(key: String, value: String) {
        viewModelScope.launch {
            val applied = withContext(Dispatchers.IO) {
                execKsud("yukizygisk set $key $value --restart", newShell = true)
            }
            if (!applied) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ksuApp, R.string.yukizygisk_apply_failed, Toast.LENGTH_SHORT).show()
                }
            }
            refresh()
        }
    }

    fun restart() {
        viewModelScope.launch {
            val restarted = withContext(Dispatchers.IO) {
                execKsud("yukizygisk restart", newShell = true)
            }
            withContext(Dispatchers.Main) {
                val message = if (restarted) R.string.yukizygisk_restarted else R.string.yukizygisk_apply_failed
                Toast.makeText(ksuApp, message, Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
    }
}
