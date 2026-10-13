package me.weishu.kernelsu.modulebanner

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import me.weishu.kernelsu.ksuApp

/** Shared Compose state keeps settings and cards on the same banner configuration. */
object ModuleBannerConfig {
    private const val PREFS_NAME = "module_banner"
    private const val KEY_ENABLED = "banner_enabled"
    private const val KEY_FOLK_ENABLED = "folk_banner_enabled"
    private const val KEY_CUSTOM_OPACITY_ENABLED = "banner_custom_opacity_enabled"
    private const val KEY_CUSTOM_OPACITY = "banner_custom_opacity"
    private const val KEY_API_MODE_ENABLED = "banner_api_mode_enabled"
    private const val KEY_API_SOURCE = "banner_api_source"
    private const val KEY_LEGACY_API_SOURCE = "folk_banner_api_source"

    private val prefs get() = ksuApp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Master switch for every module banner source. */
    var enabled by mutableStateOf(true)
        private set

    /** Gates all banner sources while retaining their nested settings. */
    var folkEnabled by mutableStateOf(true)
        private set

    var customOpacityEnabled by mutableStateOf(true)
        private set

    var customOpacity by mutableFloatStateOf(1f)
        private set

    var apiModeEnabled by mutableStateOf(false)
        private set

    var apiSource by mutableStateOf("")
        private set

    /** Bumped to force banner consumers to re-read (cache cleared, image edited). */
    var reloadKey by mutableIntStateOf(0)
        private set

    val effectiveApiSource: String get() = apiSource.trim()

    val policy: BannerPolicy
        get() = BannerPolicy(enabled, folkEnabled, apiModeEnabled, effectiveApiSource)

    private var loaded = false

    fun loadOnce() {
        if (loaded) return
        load()
        loaded = true
    }

    fun load() {
        val p = prefs
        enabled = p.getBoolean(KEY_ENABLED, true)
        folkEnabled = p.getBoolean(KEY_FOLK_ENABLED, true)
        customOpacityEnabled = p.getBoolean(KEY_CUSTOM_OPACITY_ENABLED, true)
        customOpacity = p.getFloat(KEY_CUSTOM_OPACITY, 1f)
        apiModeEnabled = p.getBoolean(KEY_API_MODE_ENABLED, false)
        apiSource = p.getString(KEY_API_SOURCE, null)
            ?: p.getString(KEY_LEGACY_API_SOURCE, "").orEmpty()
    }

    // Waiting for disk prevents a completed edit from reverting after abrupt process death.
    private fun save(durable: Boolean = false) = prefs.edit(commit = durable) {
        putBoolean(KEY_ENABLED, enabled)
        putBoolean(KEY_FOLK_ENABLED, folkEnabled)
        putBoolean(KEY_CUSTOM_OPACITY_ENABLED, customOpacityEnabled)
        putFloat(KEY_CUSTOM_OPACITY, customOpacity)
        putBoolean(KEY_API_MODE_ENABLED, apiModeEnabled)
        putString(KEY_API_SOURCE, apiSource)
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        save(durable = true)
    }

    fun updateFolkEnabled(value: Boolean) {
        folkEnabled = value
        save(durable = true)
    }

    fun updateCustomOpacityEnabled(value: Boolean) {
        customOpacityEnabled = value
        save(durable = true)
    }

    /** Live edit while a slider gesture is in flight; cards repaint, prefs are untouched. */
    fun editCustomOpacity(value: Float) {
        customOpacity = value.coerceIn(0f, 1f)
    }

    // Debounce and disposal also flush edits when scrolling cancels the slider's finish callback.
    fun persist() {
        save(durable = true)
    }

    fun updateApiModeEnabled(value: Boolean) {
        apiModeEnabled = value
        save(durable = true)
    }

    fun updateApiSource(value: String) {
        apiSource = value
        save(durable = true)
    }

    fun requestReload() {
        reloadKey++
    }
}
