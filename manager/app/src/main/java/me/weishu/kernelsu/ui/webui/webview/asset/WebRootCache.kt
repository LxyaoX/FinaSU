package me.weishu.kernelsu.ui.webui.webview.asset

import android.content.Context
import android.os.Process
import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File

private const val TAG = "WebRootCache"
private const val CACHE_DIR = "webui"
private const val STAMP_FILE = ".stamp"

/**
 * Keeps an app-readable copy of a module's `webroot` so the WebView does not have to read every
 * asset through a root shell.
 *
 * A module's webroot lives under `/data/adb`, which only root can read, so serving a page used to
 * mean one `SuFileInputStream` - and therefore one shell command - per file. A WebUI with thirty
 * assets paid thirty round trips before it could finish its first paint. Copying the tree once per
 * module version turns that into a single `cp`; afterwards every request is an ordinary
 * [java.io.FileInputStream].
 *
 * The copy is keyed on `module.prop`'s mtime and size, so an updated module gets a fresh copy on the
 * next open while an unchanged one reuses what is already on disk. When the copy cannot be made
 * (no shell, unwritable filesDir, unsupported `stat`) the callers fall back to reading through the
 * shell, which is what they did before this cache existed.
 */
class WebRootCache(
    private val context: Context,
    private val moduleId: String,
    private val moduleDir: String,
    private val shell: Shell,
) {
    @Volatile
    private var resolved = false

    @Volatile
    private var cachedDir: File? = null

    /** The cached directory, prepared on first use. Null means "keep reading through the shell". */
    fun directory(): File? {
        if (resolved) return cachedDir
        synchronized(this) {
            if (!resolved) {
                cachedDir = runCatching { prepare() }
                    .onFailure { Log.w(TAG, "Could not cache $moduleDir/webroot", it) }
                    .getOrNull()
                resolved = true
            }
        }
        return cachedDir
    }

    private fun prepare(): File? {
        val target = File(File(context.filesDir, CACHE_DIR), moduleId)
        val stamp = stampOfModule() ?: return null
        if (target.isDirectory && stampOfCache(target) == stamp) return target
        return if (copy(target, stamp)) target else null
    }

    /** `mtime:size` of `module.prop`, in one round trip. */
    private fun stampOfModule(): String? {
        val prop = "$moduleDir/module.prop"
        val out = runCatching {
            shell.newJob().add("stat -c '%Y:%s' '$prop' 2>/dev/null").exec().out
        }.getOrNull() ?: return null
        return out.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() && it != "0:0" }
    }

    private fun stampOfCache(dir: File): String? =
        runCatching { File(dir, STAMP_FILE).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun copy(target: File, stamp: String): Boolean {
        val uid = Process.myUid()
        val path = target.absolutePath
        val script = buildString {
            appendLine("rm -rf '$path'")
            appendLine("mkdir -p '$path'")
            // `cp -a` keeps the module's own modes; -r fallback covers busybox builds without -a.
            appendLine("cp -a '$moduleDir/webroot/.' '$path/' 2>/dev/null || cp -r '$moduleDir/webroot/.' '$path/'")
            // The copy was written by root, so hand it back to the app before anything reads it.
            appendLine("chown -R $uid:$uid '$path'")
            appendLine("chmod -R u+rwX,go+rX '$path'")
        }
        val ok = runCatching { shell.newJob().add(script).exec().isSuccess }.getOrDefault(false)
        if (!ok) return false
        runCatching { File(target, STAMP_FILE).writeText(stamp) }
        return true
    }
}
