package me.weishu.kernelsu.ui.viewmodel

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.PifOption
import me.weishu.kernelsu.data.model.PifStatus
import me.weishu.kernelsu.data.model.TeesimStatus
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.teesim.TeesimKeybox
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ksuApp

/** One row of the target picker, carrying what it needs to draw itself. */
data class TeesimApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val packageInfo: PackageInfo,
)

data class TeesimUiState(
    val status: TeesimStatus? = null,
    val loading: Boolean = true,
    val readError: Boolean = false,
    val writing: Boolean = false,
    val apps: List<TeesimApp> = emptyList(),
    val appsLoading: Boolean = false,
    val showSystemApps: Boolean = false,
    /** The bundled PlayIntegrityFix module, or null while it has not been read yet. */
    val pif: PifStatus? = null,
)

/**
 * Drives the TEESimulator page.
 *
 * Every write re-reads the module afterwards: the switch reflects the marker ksud will act on, the
 * keybox row reflects the file the daemon will read, and a failed command must not leave the page
 * claiming success.
 */
class TeesimViewModel(
    private val repo: SettingsRepository = SettingsRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeesimUiState())
    val uiState: StateFlow<TeesimUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val result = repo.getTeesimStatus()
            val pif = repo.getPifStatus()
            _uiState.update {
                it.copy(
                    status = result.getOrNull() ?: it.status,
                    readError = result.isFailure,
                    loading = false,
                    pif = pif.getOrNull() ?: it.pif,
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (_uiState.value.writing) return
        _uiState.update { it.copy(writing = true) }
        viewModelScope.launch {
            val result = repo.setTeesimEnabled(enabled)
            val status = repo.getTeesimStatus()
            _uiState.update {
                it.copy(
                    writing = false,
                    loading = false,
                    status = status.getOrNull() ?: it.status,
                    readError = status.isFailure,
                )
            }
            toast(
                when {
                    result.isFailure -> R.string.teesim_write_failed
                    // The hook is installed by the daemon at boot under a policy rule, so a fresh
                    // enable cannot be reported as live just because the marker is gone.
                    !enabled -> R.string.teesim_disabled_toast
                    status.getOrNull()?.daemonRunning == true -> R.string.teesim_enabled_toast
                    // The rule is applied live, so a daemon that did not come up is a real fault,
                    // not something a reboot fixes.
                    else -> R.string.teesim_state_pending
                }
            )
        }
    }

    fun install() = write(R.string.teesim_installed, R.string.teesim_install_failed) {
        repo.installTeesim()
    }

    fun uninstall() = write(R.string.teesim_uninstall_done, R.string.teesim_uninstall_failed) {
        repo.uninstallTeesim()
    }

    fun removeKeybox() = write(R.string.teesim_keybox_removed, R.string.teesim_keybox_write_failed) {
        repo.removeTeesimKeybox()
    }

    // PlayIntegrityFix is a Zygisk module, so every one of these only takes effect at the next
    // boot; the page says so instead of claiming the new identity is live.

    fun installPif() = write(R.string.pif_installed_toast, R.string.pif_write_failed) {
        repo.installPif()
    }

    fun uninstallPif() = write(R.string.pif_uninstall_done, R.string.pif_write_failed) {
        repo.uninstallPif()
    }

    fun setPifEnabled(enabled: Boolean) =
        write(R.string.pif_saved, R.string.pif_write_failed) {
            repo.setPifEnabled(enabled)
        }

    fun setPifOption(option: PifOption, value: Boolean) =
        write(R.string.pif_saved, R.string.pif_write_failed) {
            repo.setPifOption(option, value)
        }

    fun setTargets(packages: List<String>) =
        write(R.string.teesim_targets_saved, R.string.teesim_write_failed) {
            repo.setTeesimTargetApps(packages)
        }

    /**
     * Validates the picked file before it reaches the daemon: a keybox that misses one of the two
     * keys or a certificate chain would leave the module installed but silently inert.
     */
    fun importKeybox(uri: Uri) {
        if (_uiState.value.writing) return
        _uiState.update { it.copy(writing = true) }
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                val encoded = runCatching {
                    ksuApp.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        check(bytes.size in 1..MAX_KEYBOX_BYTES) { "unexpected keybox size" }
                        val text = String(bytes, Charsets.UTF_8).trim()
                        check(TeesimKeybox.isUsable(text)) { "not a keybox" }
                        Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    }
                }.getOrNull()
                when {
                    encoded == null -> R.string.teesim_keybox_invalid
                    repo.importTeesimKeybox(encoded).isSuccess -> R.string.teesim_keybox_imported
                    else -> R.string.teesim_keybox_write_failed
                }
            }
            val status = repo.getTeesimStatus()
            _uiState.update {
                it.copy(
                    writing = false,
                    loading = false,
                    status = status.getOrNull() ?: it.status,
                    readError = status.isFailure,
                )
            }
            toast(message)
        }
    }

    fun loadApps() {
        val state = _uiState.value
        if (state.apps.isNotEmpty() || state.appsLoading) return
        _uiState.update { it.copy(appsLoading = true) }
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                runCatching {
                    // The superuser list is a process-wide cache; fill it once and reuse it here
                    // instead of walking PackageManager a second time.
                    if (SuperUserViewModel.apps.isEmpty()) SuperUserViewModel().fetchAppList()
                    SuperUserViewModel.apps
                        // The superuser list carries synthetic rows (WebView Zygote has no
                        // package name); config.json targets packages, so only real apps fit.
                        .filter { it.packageName.isNotBlank() && !it.special }
                        .map { app ->
                            TeesimApp(
                                packageName = app.packageName,
                                label = app.label,
                                system = ((app.packageInfo.applicationInfo?.flags ?: 0) and
                                    ApplicationInfo.FLAG_SYSTEM) != 0,
                                packageInfo = app.packageInfo,
                            )
                        }
                        .distinctBy { it.packageName }
                        .sortedBy { it.label.lowercase() }
                }.getOrDefault(emptyList())
            }
            _uiState.update { it.copy(apps = apps, appsLoading = false) }
        }
    }

    fun setShowSystemApps(show: Boolean) {
        _uiState.update { it.copy(showSystemApps = show) }
    }

    private fun write(
        successMessage: Int,
        failureMessage: Int,
        block: suspend () -> Result<Unit>,
    ) {
        if (_uiState.value.writing) return
        _uiState.update { it.copy(writing = true) }
        viewModelScope.launch {
            val result = block()
            val status = repo.getTeesimStatus()
            val pif = repo.getPifStatus()
            _uiState.update {
                it.copy(
                    writing = false,
                    loading = false,
                    status = status.getOrNull() ?: it.status,
                    readError = status.isFailure,
                    pif = pif.getOrNull() ?: it.pif,
                )
            }
            toast(if (result.isSuccess) successMessage else failureMessage)
        }
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(ksuApp, messageRes, Toast.LENGTH_LONG).show()
    }

    private companion object {
        /** A real keybox is a few dozen kilobytes; anything past this is not one. */
        const val MAX_KEYBOX_BYTES = 1_048_576
    }
}
