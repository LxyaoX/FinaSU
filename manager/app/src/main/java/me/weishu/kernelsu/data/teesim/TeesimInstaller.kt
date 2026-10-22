package me.weishu.kernelsu.data.teesim

import android.util.Base64
import com.topjohnwu.superuser.Shell
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream
import me.weishu.kernelsu.data.model.TeesimConfig
import me.weishu.kernelsu.data.model.TeesimStatus
import me.weishu.kernelsu.ksuApp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Installs and drives the TEESimulator module bundled in `assets/teesim`.
 *
 * Upstream ships the module as a flashable zip; carrying it in the APK lets the settings page do
 * the same work without a reboot into recovery or a trip through the module installer:
 *
 * 1. extract `teesim-arm64.zip` into the app cache (no root needed);
 * 2. copy the tree to `/data/adb/modules/teesim` as root with the permissions upstream's
 *    `customize.sh` would have set, disabling a competing TrickyStore the same way it does;
 * 3. seed `/data/adb/teesim/config.json` once and keep it across updates.
 *
 * The module itself is unchanged, so its own WebUI and its daemon keep working, and a later flash
 * of the upstream zip updates the same directory. Runtime state is split the way upstream expects:
 * the module directory holds the code, `/data/adb/teesim` holds the configuration and the keybox.
 */
object TeesimInstaller {
    /** Upstream build carried in the assets; keep in sync with the archive and its NOTICE. */
    const val VERSION = "v4.0 (75-fcf1738-release)"

    /** Module id, i.e. the directory name under `/data/adb/modules` and the WebUI key. */
    const val MODULE_ID = "teesim"

    const val MODULE_DIR = "/data/adb/modules/teesim"
    const val DATA_DIR = "/data/adb/teesim"
    const val CONFIG_PATH = "/data/adb/teesim/config.json"
    const val KEYBOX_PATH = "/data/adb/teesim/keybox.xml"

    private const val ASSET_ZIP = "teesim/teesim-arm64.zip"
    private const val STAGE_DIR = "teesim-stage"

    /**
     * One fact per line, so [TeesimStatus.fromProbe] never has to guess which output answers which
     * question, and a missing file degrades to an empty value instead of an error.
     */
    private val probeCommands = arrayOf(
        "if [ -d $MODULE_DIR ]; then echo tee:installed=1; else echo tee:installed=0; fi",
        "if [ -e $MODULE_DIR/disable ]; then echo tee:disabled=1; else echo tee:disabled=0; fi",
        // `pidof` matches the short process name; `pgrep -f` covers a kernel where the nice name
        // was truncated, so a live daemon is never reported as stopped.
        "if pidof teesim >/dev/null 2>&1 || pgrep -f org.matrix.teesim.App >/dev/null 2>&1; " +
            "then echo tee:running=1; else echo tee:running=0; fi",
        "if [ -s $KEYBOX_PATH ]; then echo tee:keybox=1; else echo tee:keybox=0; fi",
        "if [ -f $KEYBOX_PATH ]; then echo \"tee:keyboxbytes=\$(stat -c %s $KEYBOX_PATH)\"; " +
            "else echo tee:keyboxbytes=0; fi",
        "if [ -d $MODULE_DIR/webroot ]; then echo tee:webui=1; else echo tee:webui=0; fi",
        "sed -n 's/^version=//p' $MODULE_DIR/module.prop 2>/dev/null | head -n 1 " +
            "| sed 's/^/tee:version=/'",
    )

    /** Current state of the module, its daemon and the configuration the manager edits. */
    fun status(): TeesimStatus {
        val probe = TeesimStatus.fromProbe(output(*probeCommands))
        if (!probe.installed) return probe
        return probe.copy(config = TeesimConfig.parse(output("cat $CONFIG_PATH 2>/dev/null")))
    }

    /**
     * Copies the bundled module over whatever is installed and clears the `disable` marker.
     *
     * Reinstalling keeps `/data/adb/teesim` untouched, so an update never costs the user their
     * keybox or their profile assignments.
     */
    fun install() {
        // Replacing the tree under a live daemon would leave it running from deleted inodes, i.e.
        // on the previous build until the next reboot. Stop it first, then bring the boot script
        // back only if it had been running: a fresh install must keep waiting for the boot
        // sepolicy rule instead of starting a hook that cannot inject yet.
        val wasRunning = status().daemonRunning
        if (wasRunning) stopDaemon()

        val stage = File(ksuApp.cacheDir, STAGE_DIR)
        stage.deleteRecursively()
        check(stage.mkdirs() || stage.isDirectory) { "could not create ${stage.absolutePath}" }
        extract(stage)

        // `cp -R` per entry instead of `cp -R stage/. dest` so the copy does not depend on how the
        // shell treats a trailing "/." — the archive has no dotfiles either way.
        val result = run(
            "rm -rf $MODULE_DIR",
            "mkdir -p $MODULE_DIR",
            "for f in '${stage.absolutePath}'/*; do cp -R \"\$f\" $MODULE_DIR/; done",
            "chown -R 0:0 $MODULE_DIR",
            // `X` gives the execute bit to directories only, so the tree stays readable without
            // turning every asset into an executable. The real entry points are fixed below.
            "chmod -R u+rwX,go+rX $MODULE_DIR",
            "chmod 0755 $MODULE_DIR/daemon $MODULE_DIR/customize.sh $MODULE_DIR/service.sh",
            "chmod 0755 $MODULE_DIR/arm64-v8a/inject $MODULE_DIR/arm64-v8a/teesim-uds",
            "mkdir -p $DATA_DIR",
            "chmod 0700 $DATA_DIR",
            "if [ ! -f $CONFIG_PATH ]; then cp $MODULE_DIR/config.default.json $CONFIG_PATH; fi",
            "chmod 0600 $CONFIG_PATH",
            // Adopt a keybox the user already had for TrickyStore, like upstream customize.sh.
            "if [ ! -f $KEYBOX_PATH ] && [ -f /data/adb/tricky_store/keybox.xml ]; " +
                "then cp /data/adb/tricky_store/keybox.xml $KEYBOX_PATH; fi",
            "if [ -f $KEYBOX_PATH ]; then chmod 0600 $KEYBOX_PATH; fi",
            // TrickyStore hooks the same keystore path; upstream disables it rather than share it.
            "for ts in /data/adb/modules/tricky_store /data/adb/modules_update/tricky_store; " +
                "do if [ -d \"\$ts\" ] && [ ! -e \"\$ts/disable\" ]; then touch \"\$ts/disable\"; fi; done",
            "rm -f $MODULE_DIR/disable $MODULE_DIR/remove",
            // Guard against a wrong bundled archive: the daemon only works under this id.
            "grep -q '^id=teesim' $MODULE_DIR/module.prop",
        )
        stage.deleteRecursively()
        check(result.isSuccess) { "could not install the TEESimulator module into $MODULE_DIR" }
        if (wasRunning) startDaemon()
    }

    /**
     * Enables or disables the module through the marker ksud reads at boot.
     *
     * Enabling installs the bundled copy when nothing is there yet. The daemon injects into
     * `keystore2` under a policy rule that is applied at boot, so a fresh enable is reported as
     * pending instead of pretending the hook is live; disabling stops the daemon at once.
     */
    fun setEnabled(enabled: Boolean) {
        val current = status()
        if (!current.installed) {
            // Enabling has to install the bundled copy first; disabling an absent module is
            // already the state the caller asked for.
            if (enabled) install()
            return
        }
        if (enabled) {
            val result = run("rm -f $MODULE_DIR/disable $MODULE_DIR/remove")
            check(result.isSuccess) { "could not clear the module markers" }
            return
        }
        check(run("touch $MODULE_DIR/disable").isSuccess) {
            "could not write $MODULE_DIR/disable"
        }
        stopDaemon()
    }

    /** Removes the module directory; the configuration and the keybox stay in [DATA_DIR]. */
    fun uninstall() {
        stopDaemon()
        val result = run("rm -rf $MODULE_DIR", "rm -f $DATA_DIR/teesim-uds")
        check(result.isSuccess) { "could not remove $MODULE_DIR" }
    }

    /**
     * Writes a keybox over the current one and fixes its permissions.
     *
     * [base64] is the file content, not a path: the daemon runs as root and only ever reads
     * `/data/adb/teesim/keybox.xml`, so the manager hands the bytes over the shell it already has
     * instead of staging them somewhere root has to be trusted to read.
     */
    fun importKeybox(base64: String) {
        require(base64.isNotBlank()) { "empty keybox" }
        val result = run(
            "mkdir -p $DATA_DIR",
            "chmod 0700 $DATA_DIR",
            "echo '$base64' | base64 -d > $KEYBOX_PATH.tmp",
            "chmod 0600 $KEYBOX_PATH.tmp",
            "chown 0:0 $KEYBOX_PATH.tmp",
            // The daemon watches this path, so publish the finished file atomically.
            "mv -f $KEYBOX_PATH.tmp $KEYBOX_PATH",
        )
        check(result.isSuccess) { "could not write $KEYBOX_PATH" }
        check(status().keyboxBytes > 0) { "$KEYBOX_PATH is empty" }
    }

    fun removeKeybox() {
        check(run("rm -f $KEYBOX_PATH").isSuccess) { "could not remove $KEYBOX_PATH" }
        // The daemon resolves the keybox from the configuration and reloads when it changes, so
        // nudge that file instead of leaving the removed keybox in the daemon's memory.
        run("touch $CONFIG_PATH")
    }

    /**
     * Replaces the app list of the `default` profile, leaving every other profile by itself.
     *
     * The daemon validates the file and reloads it live, so no reboot is needed here. A profile is
     * never created implicitly: a missing `default` means the configuration was edited into
     * something the manager does not understand, and guessing would be worse than failing.
     */
    fun setTargetApps(packages: List<String>) {
        val raw = output("cat $CONFIG_PATH 2>/dev/null")
        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: error("$CONFIG_PATH is missing or not valid JSON")
        val profiles = root.optJSONObject("profiles")
            ?: error("$CONFIG_PATH has no profiles")
        val profile = profiles.optJSONObject(TeesimConfig.DEFAULT_PROFILE)
            ?: error("$CONFIG_PATH has no ${TeesimConfig.DEFAULT_PROFILE} profile")

        profile.put("apps", JSONArray(packages.distinct().sorted()))
        val encoded = Base64.encodeToString(
            root.toString(2).toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )
        val result = run(
            "echo '$encoded' | base64 -d > $CONFIG_PATH.tmp",
            "chmod 0600 $CONFIG_PATH.tmp",
            "chown 0:0 $CONFIG_PATH.tmp",
            "mv -f $CONFIG_PATH.tmp $CONFIG_PATH",
        )
        check(result.isSuccess) { "could not write $CONFIG_PATH" }
    }

    /**
     * Stops the daemon the way a reboot would.
     *
     * `service.sh` respawns the daemon every two seconds, so killing the daemon alone leaves it
     * running again a moment later; the respawn loop itself has to go first. Both commands are
     * best effort - a module that never started has neither process.
     */
    private fun stopDaemon() {
        run(
            "pkill -f '$MODULE_DIR/service.sh'",
            "kill \$(pidof teesim) 2>/dev/null",
        )
    }

    /** Relaunches the boot script detached, the way ksud starts it at late_start. */
    private fun startDaemon() {
        run("nohup sh $MODULE_DIR/service.sh >/dev/null 2>&1 &")
    }

    private fun extract(stage: File) {
        val root = stage.canonicalPath + File.separator
        ksuApp.assets.open(ASSET_ZIP).use { input ->
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

    /**
     * Builds a job from newline separated scripts.
     *
     * libsu feeds each added command to one shell process, so variables and loops behave like a
     * script while every line still has to be self-contained — the caller passes one-liners.
     */
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
