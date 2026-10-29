package me.weishu.kernelsu.data.pif

import android.util.Base64
import com.topjohnwu.superuser.Shell
import java.io.File
import me.weishu.kernelsu.data.model.PifConfig
import me.weishu.kernelsu.data.model.PifOption
import me.weishu.kernelsu.data.model.PifProp
import me.weishu.kernelsu.data.model.PifStatus
import me.weishu.kernelsu.ksuApp

/**
 * The bundled PlayIntegrityFix payload: where it lives, what it is doing, and the one file this page
 * edits.
 *
 * The payload belongs to ksud. It ships inside `libksud.so`, ksud writes it to [ROOT] on every boot
 * and links that directory into `/data/adb/modules` there - because the library it carries opens
 * `/data/adb/modules/playintegrityfix/{module.prop,pif.prop,classes.dex}` by absolute name and has
 * no setting to move them. The link is a symbolic link, which is what keeps the thing out of the
 * module list and out of NoMount's planner.
 *
 * So nothing here installs or removes a module: this object reports what ksud left on disk, flips
 * the disable marker that zygiskd reads, and edits `pif.prop` - the identity and the six switches.
 * The one write that belongs to ksud is [reinstall], which is how a payload whose files were damaged
 * is put back.
 */
object PifInstaller {
    /** Upstream release carried by the payload; keep in sync with `pif::VERSION`. */
    const val VERSION = "v4.9-inject-s"

    /** Where ksud installs the payload. Mirrors `pif::ROOT`. */
    const val ROOT = "/data/adb/ksu/lib/yukizygisk/builtin/playintegrityfix"

    /** The path the library opens by name; a symbolic link to [ROOT]. Mirrors `pif::LINK`. */
    const val MODULE_DIR = "/data/adb/modules/playintegrityfix"

    const val PROP_PATH = "$ROOT/pif.prop"

    /** ksud's own marker: present means "installed but not loaded". Mirrors `pif::DISABLE`. */
    const val DISABLE_PATH = "$ROOT/disable"

    /** Current state of the payload and the file the page edits. */
    fun status(): PifStatus {
        val probe = output(
            "if [ -f $ROOT/module.prop ]; then echo installed=1; else echo installed=0; fi",
            "if [ -f $DISABLE_PATH ]; then echo disabled=1; else echo disabled=0; fi",
            // Linked means zygiskd will load it; a real directory means an upstream install owns
            // the path and the built-in payload is sitting there unused.
            "if [ -L $MODULE_DIR ]; then echo linked=1; fi",
            "if [ -d $MODULE_DIR ] && [ ! -L $MODULE_DIR ]; then echo external=1; fi",
            "grep -m1 '^version=' $ROOT/module.prop 2>/dev/null",
        )
        val installed = probe.lineSequence().any { it.trim() == "installed=1" }
        // Only read the config once the payload is there; a stale file would otherwise be presented
        // as the current identity.
        val config = if (installed) output("cat $PROP_PATH 2>/dev/null") else ""
        return PifStatus.fromProbe(probe, config)
    }

    /**
     * Asks ksud to write the payload again, keeping `pif.prop`.
     *
     * A ksud update does this on the next boot by itself; this is for the case where the files were
     * damaged or the link went stale and the user does not want to wait for a reboot.
     */
    fun reinstall() {
        val result = run("$DAEMON pif install")
        check(result.isSuccess) { "could not write the payload under $ROOT" }
    }

    /**
     * Takes over an upstream install at [MODULE_DIR], keeping its `pif.prop`.
     *
     * Only meaningful while [PifStatus.external] is true: upstream's module owns the path, so the
     * built-in payload cannot be linked until that directory is gone. ksud runs upstream's own
     * `uninstall.sh` first so the properties it changed go back.
     */
    fun adopt() {
        val result = run("$DAEMON pif adopt")
        check(result.isSuccess) { "could not take over $MODULE_DIR" }
    }

    /**
     * Turns the payload on or off.
     *
     * The marker is ksud's and zygiskd's, so this is the same switch the engine's own page uses; the
     * library is only handed to zygote at start-up, which is why the caller says "reboot to apply".
     */
    fun setEnabled(enabled: Boolean) {
        check(status().installed) { "$ROOT is not installed" }
        val result = if (enabled) {
            run("rm -f $DISABLE_PATH")
        } else {
            run("touch $DISABLE_PATH", "chmod 0644 $DISABLE_PATH")
        }
        check(result.isSuccess) { "could not write $DISABLE_PATH" }
    }

    /** Flips one switch in `pif.prop`, leaving every other line untouched. */
    fun setOption(option: PifOption, value: Boolean) {
        check(status().installed) { "$ROOT is not installed" }
        val current = output("cat $PROP_PATH 2>/dev/null")
        check(current.isNotBlank()) { "$PROP_PATH is missing" }
        writeProp(PifProp.withOption(current, option, value))
    }

    fun config(): PifConfig = PifConfig.parse(output("cat $PROP_PATH 2>/dev/null"))

    /**
     * Publishes `pif.prop` atomically.
     *
     * The library reads the file during zygote start-up, so a half-written one would be read as a
     * shorter config; writing to a temporary name and renaming keeps the previous contents until the
     * replacement is complete.
     */
    private fun writeProp(text: String) {
        val encoded = singleLineBase64(
            Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
        )
        val result = run(
            "echo '$encoded' | base64 -d > $PROP_PATH.tmp",
            "chmod 0644 $PROP_PATH.tmp",
            "chown 0:0 $PROP_PATH.tmp",
            "mv -f $PROP_PATH.tmp $PROP_PATH",
        )
        check(result.isSuccess) { "could not write $PROP_PATH" }
    }

    /** [run] splits on newlines, so a wrapped payload would be executed as several commands. */
    private fun singleLineBase64(encoded: String): String =
        encoded.filterNot { it.isWhitespace() }.also {
            require(it.isNotEmpty()) { "empty base64 payload" }
        }

    /** The ksud binary this app ships, which is also what owns the payload's layout. */
    private val DAEMON: String
        get() = File(ksuApp.applicationInfo.nativeLibraryDir, "libksud.so").absolutePath

    private fun job(vararg commands: String): Shell.Job {
        val lines = commands.asSequence()
            .flatMap { it.lineSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        return Shell.getShell().newJob().add(*lines.toTypedArray())
    }

    private fun run(vararg commands: String): Shell.Result = job(*commands).exec()

    private fun output(vararg commands: String): String {
        val lines = ArrayList<String>()
        job(*commands).to(lines).exec()
        return lines.joinToString("\n")
    }
}
