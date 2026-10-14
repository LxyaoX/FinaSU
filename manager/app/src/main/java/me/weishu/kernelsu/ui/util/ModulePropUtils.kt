package me.weishu.kernelsu.ui.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only real module ids reach the shell, so the id can never inject arguments. */
private val ModuleIdPattern = Regex("[A-Za-z0-9._-]+")

/**
 * Reads the `name=` field of an installed module's `module.prop` through a root shell, or null when
 * the id is not a plausible module name, no prop is found, or root is not available yet.
 *
 * The shell is awaited on [Dispatchers.IO], because a non-root fallback shell cannot read
 * `/data/adb`; callers may show the id itself while root is still being established. The whole prop
 * is collected through a job's stdout: `ShellUtils.fastCmd` returns a single output line, and
 * `name=` is not the first line of a `module.prop`.
 */
suspend fun getModuleDisplayName(moduleId: String): String? {
    if (!ModuleIdPattern.matches(moduleId)) return null
    return withContext(Dispatchers.IO) {
        val shell = try {
            RootShell.awaitRoot()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext null
        }
        val out = arrayListOf<String>()
        shell.newJob()
            .add(
                "cat /data/adb/modules/$moduleId/module.prop 2>/dev/null; " +
                    "cat /data/adb/modules_update/$moduleId/module.prop 2>/dev/null"
            )
            .to(out)
            .exec()
        out.asSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("name=") }
            ?.substringAfter("name=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
