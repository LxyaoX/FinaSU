package me.weishu.kernelsu.ui.component.material

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import me.weishu.kernelsu.ksuApp

/**
 * Whether switches draw a check or cross on the thumb.
 *
 * Read by every [ExpressiveSwitch] so one toggle covers the whole app, and kept in the shared
 * `settings` preferences so the choice survives a restart.
 */
object SwitchIconConfig {

    private const val PREFS = "settings"
    private const val KEY = "show_switch_icon"

    var showIcon: Boolean by mutableStateOf(true)
        private set

    fun setIconVisible(visible: Boolean) {
        if (showIcon == visible) return
        showIcon = visible
        ksuApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, visible) }
    }

    fun load(context: Context) {
        showIcon = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }
}
