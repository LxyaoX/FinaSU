package me.weishu.kernelsu.ui.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList

/**
 * The application language the manager runs in.
 *
 * Android 13+ owns the selection through [LocaleManager]; older releases fall back to the
 * `fpt_app_language` preference applied by [wrap] in `attachBaseContext`. The preference key keeps
 * its historical `fpt_` prefix so an upgrade does not reset the language.
 */
object AppLanguage {
    private const val PREFS = "settings"
    private const val KEY = "fpt_app_language"

    /** Currently selected BCP-47 tags, or an empty string for "follow the system". */
    fun currentTags(context: Context): String =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            context.getSharedPreferences(PREFS, 0).getString(KEY, "").orEmpty()
        }

    /**
     * Select application language. An empty [tags] resets to the system locale. On Android 13+
     * the platform recreates the activity; older releases must recreate the caller themselves
     * for [wrap] to take effect.
     */
    fun setTags(context: Context, tags: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tags.isEmpty()) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(tags)
        } else {
            check(context.getSharedPreferences(PREFS, 0).edit().putString(KEY, tags).commit())
        }
    }

    /** Android 12 locale fallback, using the same BCP-47 tags. */
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val tags = context.getSharedPreferences(PREFS, 0).getString(KEY, "").orEmpty()
        if (tags.isEmpty()) return context
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tags))
        })
    }
}
