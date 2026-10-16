package me.weishu.kernelsu.ui.component.bottombar

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.weishu.kernelsu.ksuApp

/**
 * Which destinations the navigation bar offers.
 *
 * Home and settings always stay, so the bar can never end up empty; the other three can be hidden
 * to keep only the entries the user needs. Stored in the shared `settings` preferences, with
 * [revision] bumping on every write so the bars and the pager update immediately.
 */
object NavBarVisibilityConfig {

    private const val PREFS = "settings"
    private const val KEY_PLUGIN = "show_nav_kpm"
    private const val KEY_SUPERUSER = "show_nav_superuser"
    private const val KEY_MODULE = "show_nav_apm"

    private val prefs get() = ksuApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** The destinations the user may hide, in bar order. */
    val hideable: List<BottomBarDestination> = listOf(
        BottomBarDestination.Plugin,
        BottomBarDestination.SuperUser,
        BottomBarDestination.Module,
    )

    fun isVisible(destination: BottomBarDestination): Boolean = when (destination) {
        BottomBarDestination.Plugin -> prefs.getBoolean(KEY_PLUGIN, true)
        BottomBarDestination.SuperUser -> prefs.getBoolean(KEY_SUPERUSER, true)
        BottomBarDestination.Module -> prefs.getBoolean(KEY_MODULE, true)
        else -> true
    }

    fun setVisible(destination: BottomBarDestination, visible: Boolean) {
        val key = keyFor(destination) ?: return
        if (isVisible(destination) == visible) return
        prefs.edit { putBoolean(key, visible) }
        _revision.value++
    }

    /** Every destination in bar order, with the hidden ones filtered out. */
    fun visibleDestinations(): List<BottomBarDestination> =
        BottomBarDestination.entries.filter { isVisible(it) }

    private fun keyFor(destination: BottomBarDestination): String? = when (destination) {
        BottomBarDestination.Plugin -> KEY_PLUGIN
        BottomBarDestination.SuperUser -> KEY_SUPERUSER
        BottomBarDestination.Module -> KEY_MODULE
        else -> null
    }
}
