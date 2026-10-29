package me.weishu.kernelsu.data.pif

import android.util.Base64
import com.topjohnwu.superuser.Shell
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream
import me.weishu.kernelsu.data.model.PifConfig
import me.weishu.kernelsu.data.model.PifOption
import me.weishu.kernelsu.data.model.PifProp
import me.weishu.kernelsu.data.model.PifStatus
import me.weishu.kernelsu.ksuApp

/**
 * Installs the bundled PlayIntegrityFix payload.
 *
 * Unlike the TEESimulator runtime, this one cannot be staged outside `/data/adb/modules`: a Zygisk
 * implementation finds modules by walking that directory and loading `zygisk/<abi>.so` from each,
 * and this project's own daemon does the same. So the module is a real module - which also means
 * it shows up in the manager's module list, and that it only takes effect after a reboot, because
 * Zygisk hands the library to zygote at start-up.
 *
 * What the manager owns on top of the module is its `pif.prop`: the switches and the identity the
 * module spoofs. The file is edited line by line ([PifProp]) so comments and keys this page does
 * not model survive.
 */
object PifInstaller {
    /** Upstream release carried in the assets; keep in sync with the archive and its NOTICE. */
    const val VERSION = "v4.9-inject-s"

    const val MODULE_DIR = "/data/adb/modules/playintegrityfix"
    const val UPDATE_DIR = "/data/adb/modules_update/playintegrityfix"
    const val PROP_PATH = "$MODULE_DIR/pif.prop"

    /** ksud reads this marker: present means "installed but not loaded". */
    const val DISABLE_PATH = "$MODULE_DIR/disable"

    private const val ASSET_ARCHIVE = "pif/playintegrityfix-arm64.zip"
    private const val STAGE_DIR = "pif-stage"
    private const val PROP_BACKUP = "pif.prop.keep"

    /** The one file that proves the archive is the module and not an empty zip. */
    private const val ZYGISK_LIB = "$MODULE_DIR/zygisk/arm64-v8a.so"

    /** Current state of the module and the file the page edits. */
    fun status(): PifStatus {
        val probe = output(
            "if [ -f $MODULE_DIR/module.prop ]; then echo installed=1; else echo installed=0; fi",
            "if [ -f $DISABLE_PATH ]; then echo disabled=1; else echo disabled=0; fi",
            "grep -m1 '^version=' $MODULE_DIR/module.prop 2>/dev/null",
        )
        val installed = probe.lineSequence().any { it.trim() == "installed=1" }
        // Only read the config once the module is there; a stale file next to a removed module
        // would otherwise be presented as the current identity.
        val config = if (installed) output("cat $PROP_PATH 2>/dev/null") else ""
        return PifStatus.fromProbe(probe, config)
    }

    /**
     * Stages the bundled module, keeping the user's `pif.prop`.
     *
     * A reinstall is how an update lands, so the identity must survive it: the existing file is
     * copied out before the directory is replaced and put back afterwards.
     */
    fun install() {
        val stage = File(ksuApp.cacheDir, STAGE_DIR)
        stage.deleteRecursively()
        check(stage.mkdirs() || stage.isDirectory) { "could not create ${stage.absolutePath}" }
        extract(stage)
        val backup = File(ksuApp.cacheDir, PROP_BACKUP)
        backup.delete()

        val result = run(
            "if [ -f $PROP_PATH ]; then cp $PROP_PATH '${backup.absolutePath}'; fi",
            // A pending update of the same id would be applied over this one at next boot.
            "rm -rf $MODULE_DIR $UPDATE_DIR",
            "mkdir -p $MODULE_DIR",
            "for f in '${stage.absolutePath}'/*; do cp -R \"\$f\" $MODULE_DIR/; done",
            "chown -R 0:0 $MODULE_DIR",
            // `X` gives the execute bit to directories only; the scripts are fixed below.
            "chmod -R u+rwX,go+rX $MODULE_DIR",
            "chmod 0755 $MODULE_DIR/post-fs-data.sh $MODULE_DIR/service.sh " +
                "$MODULE_DIR/uninstall.sh $MODULE_DIR/customize.sh $MODULE_DIR/action.sh " +
                "$MODULE_DIR/autopif.sh $MODULE_DIR/autopif_ota.sh $MODULE_DIR/security_patch.sh " +
                "$MODULE_DIR/common_func.sh",
            "if [ -f '${backup.absolutePath}' ]; then mv -f '${backup.absolutePath}' $PROP_PATH; fi",
            "if [ -f $PROP_PATH ]; then chmod 0644 $PROP_PATH; fi",
            // Guard against a wrong bundled archive: Zygisk loads exactly this path.
            "test -f $ZYGISK_LIB",
        )
        stage.deleteRecursively()
        backup.delete()
        check(result.isSuccess) { "could not stage PlayIntegrityFix in $MODULE_DIR" }
    }

    /**
     * Turns the module on or off.
     *
     * The marker is ksud's, so this is the same switch as the module page's; the library is only
     * handed to zygote at start-up, which is why the caller says "reboot to apply".
     */
    fun setEnabled(enabled: Boolean) {
        check(status().installed) { "$MODULE_DIR is not installed" }
        val result = if (enabled) {
            run("rm -f $DISABLE_PATH")
        } else {
            run("touch $DISABLE_PATH", "chmod 0644 $DISABLE_PATH")
        }
        check(result.isSuccess) { "could not write $DISABLE_PATH" }
    }

    /** Removes the module; upstream's own `uninstall.sh` puts the props it changed back first. */
    fun uninstall() {
        run("if [ -f $MODULE_DIR/uninstall.sh ]; then sh $MODULE_DIR/uninstall.sh; fi")
        val result = run("rm -rf $MODULE_DIR $UPDATE_DIR")
        check(result.isSuccess) { "could not remove $MODULE_DIR" }
    }

    /** Flips one switch in `pif.prop`, leaving every other line untouched. */
    fun setOption(option: PifOption, value: Boolean) {
        check(status().installed) { "$MODULE_DIR is not installed" }
        val current = output("cat $PROP_PATH 2>/dev/null")
        check(current.isNotBlank()) { "$PROP_PATH is missing" }
        writeProp(PifProp.withOption(current, option, value))
    }

    fun config(): PifConfig = PifConfig.parse(output("cat $PROP_PATH 2>/dev/null"))

    /**
     * Publishes `pif.prop` atomically.
     *
     * The module reads the file during `post-fs-data`, so a half-written one would simply be read as
     * a shorter config; writing to a temporary name and renaming keeps the previous contents until
     * the replacement is complete.
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

    /** Unpacks the bundled module; the archive carries no nesting a zip reader cannot handle. */
    private fun extract(stage: File) {
        val root = stage.canonicalPath + File.separator
        ksuApp.assets.open(ASSET_ARCHIVE).use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = File(stage, entry.name)
                    check(target.canonicalPath.startsWith(root)) {
                        "zip entry escapes the staging directory: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out -> zip.copyTo(out) }
                    }
                    zip.closeEntry()
                }
            }
        }
    }

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
