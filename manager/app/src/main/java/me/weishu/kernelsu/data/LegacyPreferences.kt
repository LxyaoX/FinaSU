package me.weishu.kernelsu.data

import android.content.Context
import androidx.core.content.edit
import java.util.concurrent.Executors

/**
 * One-shot removal of the preferences that features of this fork used to write and no longer ship.
 *
 * Nothing reads any of these keys now - they only occupy the settings file and the app's storage.
 * A boolean gates the pass so the first start after an upgrade pays for it once; every later start
 * reads that flag and returns.
 *
 * Add a key here when a setting's screen is deleted, not before: an entry for something that is
 * still in use would silently reset it on the next start.
 */
object LegacyPreferences {
    private const val SETTINGS_PREFS = "settings"
    private const val KEY_DONE = "legacy_prefs_purged_v1"

    /** Keys whose whole family is gone: the custom bottom-nav icons and the glass tint sliders. */
    private val DeadPrefixes = listOf("nav_icon_", "floating_glass_")

    /** Single keys left behind by deleted switches. */
    private val DeadKeys = listOf(
        "floating_bar_glass",
        "ui_mode",
    )

    /** Prefs files whose only writer was a removed feature. */
    private val DeadPrefsFiles = listOf("module_banner", "profile")

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "legacy-prefs").apply { isDaemon = true }
    }

    fun purge(context: Context) {
        val app = context.applicationContext
        val settings = app.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        if (settings.getBoolean(KEY_DONE, false)) return

        val dead = settings.all.keys.filter { key ->
            key in DeadKeys || DeadPrefixes.any { prefix -> key.startsWith(prefix) }
        }
        settings.edit {
            dead.forEach { key -> remove(key) }
            putBoolean(KEY_DONE, true)
        }

        // Deleting a prefs file and unlinking the avatar are file work; the flag is already set, so
        // this never runs twice even if the process dies before the executor finishes.
        executor.execute {
            DeadPrefsFiles.forEach { app.deleteSharedPreferences(it) }
            app.getFileStreamPath("profile_avatar").delete()
            app.cacheDir.resolve("profile_avatar_download").deleteRecursively()
        }
    }
}
