package me.weishu.kernelsu.data.teesim

import android.util.Base64
import com.topjohnwu.superuser.Shell
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.tukaani.xz.XZInputStream
import me.weishu.kernelsu.data.model.TeesimConfig
import me.weishu.kernelsu.data.model.TeesimStatus
import me.weishu.kernelsu.ksuApp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Runs the TEESimulator payload bundled in `assets/teesim` without installing a module.
 *
 * Upstream ships a flashable module: `module.prop`, `service.sh` and a `sepolicy.rule` that ksud
 * only reads from `/data/adb/modules`. Having a second module in the list is exactly what bundling
 * it was supposed to avoid, so the payload is staged as plain data under
 * `/data/adb/teesim/runtime` and the two things the module wrapper used to provide are done here:
 *
 * * `sepolicy.rule` is applied through `ksud sepolicy apply` - live when the switch is turned on,
 *   and again from a `post-fs-data.d` script at boot (ksud runs that directory for every install,
 *   no module needed);
 * * the daemon is started from that same script: it waits for keystore2 (there is nothing to hook
 *   before the main class starts), then runs the respawn loop upstream's `service.sh` used to run;
 * * the native logd reader (`libteesim_logcat.so`) is dropped. It exists to feed the WebUI's Logs
 *   panel, which this runtime does not carry, and left in place it would tail logd and rotate files
 *   into `/data/adb/teesim/log` that nothing ever reads.
 *
 * Nothing is written to `/data/adb/modules`, so the manager's module list stays clean and the whole
 * runtime goes away with one toggle.
 */
object TeesimInstaller {
    /** Upstream build carried in the assets; keep in sync with the archive and its NOTICE. */
    const val VERSION = "v4.0 (76-06f9f2e-release)"

    const val DATA_DIR = "/data/adb/teesim"
    const val RUNTIME_DIR = "/data/adb/teesim/runtime"
    const val CONFIG_PATH = "/data/adb/teesim/config.json"
    const val KEYBOX_NAME = "keybox.xml"
    const val KEYBOX_PATH = "/data/adb/teesim/keybox.xml"

    /**
     * Version of the runtime on disk, written by [install].
     *
     * The wrapper that used to carry it (`module.prop`) is not shipped, so without this the manager
     * could only compare [VERSION] against itself and would never notice a runtime staged by an
     * older build.
     */
    const val VERSION_PATH = "/data/adb/teesim/version"

    /** Upstream's policy rule, applied live and at boot instead of by the module loader. */
    const val POLICY_PATH = "/data/adb/teesim/sepolicy.rule"

    /** Rotating log files the daemon used to keep for the WebUI's Logs panel. */
    const val LOG_DIR = "/data/adb/teesim/log"

    /** The respawn loop; also the handle used to stop it. */
    const val RUN_SCRIPT = "/data/adb/teesim/run.sh"

    /** Boot hook: ksud runs every executable in this directory, module or not. */
    const val BOOT_SCRIPT = "/data/adb/post-fs-data.d/neosu_teesim.sh"

    private const val ASSET_ARCHIVE = "teesim/teesim-arm64.zip.xz"
    private const val STAGE_DIR = "teesim-stage"

    /**
     * One condition, shared by the probe and the start-up wait, so the switch and the toast can
     * never disagree about whether the daemon is up.
     */
    private const val DAEMON_ALIVE_COND =
        "pidof teesim >/dev/null 2>&1 || pgrep -f org.matrix.teesim.App >/dev/null 2>&1"

    /**
     * Enabling starts a respawn loop that first waits for keystore2 and then a fresh ART process,
     * so the daemon is never alive by the time the shell command returns.
     */
    private const val DAEMON_START_TIMEOUT_MS = 6_000L
    private const val DAEMON_POLL_MS = 200L

    /** Directories an earlier build of this manager installed as a module; removed on install. */
    private const val LEGACY_MODULE_DIR = "/data/adb/modules/teesim"
    private const val LEGACY_UPDATE_DIR = "/data/adb/modules_update/teesim"

    /**
     * One fact per line, so [TeesimStatus.fromProbe] never has to guess which output answers which
     * question, and a missing file degrades to an empty value instead of an error.
     */
    private val probeCommands = arrayOf(
        "if [ -x $RUNTIME_DIR/daemon ]; then echo tee:installed=1; else echo tee:installed=0; fi",
        "if [ -f $BOOT_SCRIPT ]; then echo tee:enabled=1; else echo tee:enabled=0; fi",
        // `pidof` matches the short process name; `pgrep -f` covers a kernel where the nice name
        // was truncated, so a live daemon is never reported as stopped.
        "if $DAEMON_ALIVE_COND; then echo tee:running=1; else echo tee:running=0; fi",
        "if [ -f $VERSION_PATH ]; then echo \"tee:version=\$(cat $VERSION_PATH 2>/dev/null)\"; fi",
        "if [ -s $KEYBOX_PATH ]; then echo tee:keybox=1; else echo tee:keybox=0; fi",
        "if [ -f $KEYBOX_PATH ]; then echo \"tee:keyboxbytes=\$(stat -c %s $KEYBOX_PATH)\"; " +
            "else echo tee:keyboxbytes=0; fi",
    )

    /** Current state of the payload, its daemon and the configuration the manager edits. */
    fun status(): TeesimStatus {
        val probe = TeesimStatus.fromProbe(output(*probeCommands))
        if (!probe.installed) return probe
        return probe.copy(config = TeesimConfig.parse(output("cat $CONFIG_PATH 2>/dev/null")))
    }

    /**
     * Stages the payload under [RUNTIME_DIR] and seeds the data directory.
     *
     * Reinstalling leaves the configuration and the keybox alone, so an update never costs the user
     * their profiles. It stops the daemon first: replacing the tree under a running `app_process`
     * leaves it serving from deleted files until it is restarted.
     */
    fun install() {
        stopDaemon()

        val stage = File(ksuApp.cacheDir, STAGE_DIR)
        stage.deleteRecursively()
        check(stage.mkdirs() || stage.isDirectory) { "could not create ${stage.absolutePath}" }
        extract(stage)

        val result = run(
            // A module from the previous build would run a second daemon alongside this one.
            "rm -rf $LEGACY_MODULE_DIR $LEGACY_UPDATE_DIR",
            "mkdir -p $RUNTIME_DIR",
            "rm -rf $RUNTIME_DIR/*",
            "for f in '${stage.absolutePath}'/*; do cp -R \"\$f\" $RUNTIME_DIR/; done",
            // The archive carries only what runs: the WebUI, the module wrapper (module.prop,
            // customize.sh, service.sh, update.json, changelog.md) and the native logd reader are
            // not in it. Without the reader LogTail logs one line at start-up and disables itself,
            // so no thread tails logd and nothing is rotated into $LOG_DIR - which is why the boot
            // hook still deletes that library from a runtime an older build left behind.
            "chown -R 0:0 $RUNTIME_DIR",
            // `X` gives the execute bit to directories only; the real entry points are fixed below.
            "chmod -R u+rwX,go+rX $RUNTIME_DIR",
            "chmod 0755 $RUNTIME_DIR/daemon $RUNTIME_DIR/arm64-v8a/inject " +
                "$RUNTIME_DIR/arm64-v8a/teesim-uds",
            "mkdir -p $DATA_DIR",
            "chmod 0700 $DATA_DIR",
            // Whatever a build with the log reader collected; nothing writes here any more.
            "rm -rf $LOG_DIR",
            // The rule lives beside the config now; the boot script feeds it to ksud.
            "mv -f $RUNTIME_DIR/sepolicy.rule $POLICY_PATH",
            "chmod 0600 $POLICY_PATH",
            "if [ ! -f $CONFIG_PATH ]; then cp $RUNTIME_DIR/config.default.json $CONFIG_PATH; fi",
            "chmod 0600 $CONFIG_PATH",
            // module.prop used to carry the version; it is not shipped any more, so record what was
            // actually staged instead, which lets the page tell a current runtime from an old one.
            "echo '$VERSION' > $VERSION_PATH.tmp",
            "chmod 0644 $VERSION_PATH.tmp",
            "mv -f $VERSION_PATH.tmp $VERSION_PATH",
            // Adopt a keybox the user already had for TrickyStore, like upstream customize.sh.
            "if [ ! -f $KEYBOX_PATH ] && [ -f /data/adb/tricky_store/keybox.xml ]; " +
                "then cp /data/adb/tricky_store/keybox.xml $KEYBOX_PATH; fi",
            "if [ -f $KEYBOX_PATH ]; then chmod 0600 $KEYBOX_PATH; fi",
            // TrickyStore hooks the same keystore path; upstream disables it rather than share it.
            "for ts in /data/adb/modules/tricky_store /data/adb/modules_update/tricky_store; " +
                "do if [ -d \"\$ts\" ] && [ ! -e \"\$ts/disable\" ]; then touch \"\$ts/disable\"; fi; done",
            // Guard against a wrong bundled archive: this daemon only works from that entry point.
            "grep -q 'org.matrix.teesim.App' $RUNTIME_DIR/classes.dex",
        )
        stage.deleteRecursively()
        check(result.isSuccess) { "could not stage the TEESimulator runtime in $RUNTIME_DIR" }
        writeScript(RUN_SCRIPT, runScriptContent)
    }

    /**
     * Turns the payload on or off.
     *
     * Enabling installs the bundled copy when nothing is staged, writes the boot hook, applies the
     * policy rule live and starts the daemon - no reboot, because the rule is patched into the
     * running policy instead of being loaded by the module system at boot.
     */
    fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            run("rm -f $BOOT_SCRIPT")
            stopDaemon()
            return
        }
        if (!status().installed) install()
        // Policy first: a rule that never reached the kernel would leave the hook denied by SELinux,
        // which looks exactly like a broken runtime.
        applyPolicy()
        writeScript(BOOT_SCRIPT, bootScriptContent)
        startDaemon()
        // Give the daemon the time it needs before the caller reads the state back, otherwise every
        // successful enable reports "enabled but not running" for a runtime that is still starting.
        awaitDaemon()
    }

    /** Removes the runtime and the boot hook; the configuration and the keybox stay in [DATA_DIR]. */
    fun uninstall() {
        run("rm -f $BOOT_SCRIPT")
        stopDaemon()
        val result = run(
            "rm -rf $RUNTIME_DIR",
            // admin.token is the daemon's own credential; leaving it behind outlives the runtime
            // that issued it. The version describes the runtime, so it goes with it.
            "rm -f $RUN_SCRIPT $DATA_DIR/teesim-uds $DATA_DIR/admin.token $VERSION_PATH $POLICY_PATH",
            // Log files a build with the log reader left behind.
            "rm -rf $LOG_DIR",
        )
        check(result.isSuccess) { "could not remove $RUNTIME_DIR" }
    }

    /**
     * Writes a keybox over the current one and points the default profile at it.
     *
     * [base64] is the file content, not a path: the daemon runs as root and only ever reads
     * `/data/adb/teesim/keybox.xml`, so the manager hands the bytes over the shell it already has
     * instead of staging them somewhere root has to be trusted to read.
     */
    fun importKeybox(base64: String) {
        require(base64.isNotBlank()) { "empty keybox" }
        val payload = singleLineBase64(base64)
        val result = run(
            "mkdir -p $DATA_DIR",
            "chmod 0700 $DATA_DIR",
            "echo '$payload' | base64 -d > $KEYBOX_PATH.tmp",
            "chmod 0600 $KEYBOX_PATH.tmp",
            "chown 0:0 $KEYBOX_PATH.tmp",
            // The daemon watches this path, so publish the finished file atomically.
            "mv -f $KEYBOX_PATH.tmp $KEYBOX_PATH",
        )
        check(result.isSuccess) { "could not write $KEYBOX_PATH" }
        check(status().keyboxBytes > 0) { "$KEYBOX_PATH is empty" }
        // Importing through the manager means "sign with this file": point the default profile at
        // it, otherwise a profile still naming another keybox would silently ignore the import.
        pointDefaultProfileAtManagerKeybox()
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
        val root = readConfig() ?: error("$CONFIG_PATH is missing or not valid JSON")
        val profile = defaultProfile(root)
            ?: error("$CONFIG_PATH has no ${TeesimConfig.DEFAULT_PROFILE} profile")
        profile.put("apps", JSONArray(packages.distinct().sorted()))
        writeConfig(root)
    }

    /**
     * Makes the `default` profile sign with [KEYBOX_NAME] after an import through the manager.
     *
     * Best effort on a missing profile: the configuration was edited into something the manager
     * does not understand, and the file itself is still there to be fixed up by hand.
     */
    private fun pointDefaultProfileAtManagerKeybox() {
        val root = readConfig() ?: return
        val profile = defaultProfile(root) ?: return
        if (profile.optString("keybox") == KEYBOX_NAME) return
        profile.put("keybox", KEYBOX_NAME)
        writeConfig(root)
    }

    private fun readConfig(): JSONObject? =
        runCatching { JSONObject(output("cat $CONFIG_PATH 2>/dev/null")) }.getOrNull()

    private fun defaultProfile(root: JSONObject): JSONObject? =
        root.optJSONObject("profiles")?.optJSONObject(TeesimConfig.DEFAULT_PROFILE)

    /** Publishes the configuration atomically; the daemon reloads when it changes. */
    private fun writeConfig(root: JSONObject) {
        val encoded = singleLineBase64(
            Base64.encodeToString(
                root.toString(2).toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP,
            ),
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
     * Collapses a base64 payload to one line.
     *
     * [run] splits its input into shell commands on newlines, so a wrapped payload would be executed
     * as several commands and corrupt the file. Whitespace carries no meaning in base64, so this is
     * safe for any encoder's output.
     */
    private fun singleLineBase64(encoded: String): String =
        encoded.filterNot { it.isWhitespace() }.also {
            require(it.isNotEmpty()) { "empty base64 payload" }
        }

    /** Boot hook; its presence is also the persisted "on" state the switch reads. */
    private val bootScriptContent = """
#!/system/bin/sh
# Generated by FinaSU: runs TEESimulator from /data/adb/teesim/runtime without a module.
# Delete this file (or turn the switch off in FinaSU) to stop loading it.
DATA=/data/adb/teesim
# The module loader applies sepolicy.rule for flashed modules only, so apply it here instead.
[ -x /data/adb/ksud ] && /data/adb/ksud sepolicy apply ${'$'}DATA/sepolicy.rule >/dev/null 2>&1
chmod 0700 ${'$'}DATA 2>/dev/null
chmod 0600 ${'$'}DATA/admin.token 2>/dev/null
# A runtime staged by an earlier build still ships the logd reader behind the WebUI's Logs panel.
# Removing it here (before the daemon starts, so nothing has it open) disables the collector for
# runtimes that were staged before this manager learned to drop it.
rm -f ${'$'}DATA/runtime/arm64-v8a/libteesim_logcat.so
rm -rf ${'$'}DATA/log
# The admin client is staged at a fixed path so it does not depend on the runtime location.
for f in ${'$'}DATA/runtime/*/teesim-uds; do
  [ -f "${'$'}f" ] || continue
  cp "${'$'}f" ${'$'}DATA/teesim-uds
  chmod 0700 ${'$'}DATA/teesim-uds
  break
done
if command -v setsid >/dev/null 2>&1; then
  setsid sh ${'$'}DATA/run.sh >/dev/null 2>&1 &
else
  sh ${'$'}DATA/run.sh >/dev/null 2>&1 &
fi
""".trimIndent()

    /** Respawn loop, replacing upstream's `service.sh`. */
    private val runScriptContent = """
#!/system/bin/sh
# Generated by FinaSU: waits for keystore2, then keeps the TEESimulator daemon alive.
DATA=/data/adb/teesim
DIR=${'$'}DATA/runtime
# Nothing to run: a removed or half-staged runtime must not turn into a fork every two seconds for
# the rest of the boot.
[ -x "${'$'}DIR/daemon" ] || exit 1
# keystore2 only exists once the main class starts; there is nothing to hook before that.
while [ -z "${'$'}(pidof keystore2)" ] && [ -z "${'$'}(pidof keystore)" ]; do sleep 1; done
delay=2
while true; do
  started=${'$'}(date +%s)
  "${'$'}DIR/daemon" "${'$'}DIR"
  # A daemon that stayed up was not crash-looping, so the backoff starts over; one that died at
  # once gets a longer gap than a flat two-second retry.
  [ ${'$'}(( ${'$'}(date +%s) - started )) -ge 60 ] && delay=2
  sleep "${'$'}delay"
  [ "${'$'}delay" -lt 30 ] && delay=${'$'}(( delay * 2 ))
done
""".trimIndent()

    private fun writeScript(path: String, content: String) {
        val file = File(ksuApp.cacheDir, File(path).name)
        file.writeText(content)
        // `post-fs-data.d` is not created by ksud's installer - only its OTA path makes one - so a
        // device that has never had a boot hook would fail here with a bare `cp` error.
        val directory = File(path).parentFile?.absolutePath
            ?: error("$path has no parent directory")
        val result = run(
            "mkdir -p '$directory'",
            "cp '${file.absolutePath}' '$path'",
            "chmod 0755 '$path'",
            "chown 0:0 '$path'",
        )
        check(result.isSuccess) { "could not write $path" }
    }

    /**
     * Hands the rule to ksud so it is patched into the running policy.
     *
     * Not a health check: `ksud sepolicy apply` submits the rule statement by statement and only
     * warns when the kernel rejects one, so a zero exit code means "the file was readable", not
     * "every rule landed". That is the behaviour wanted here - the rule names types from other root
     * managers (`magisk`, `su`) that do not exist on every device, and a strict apply would fail
     * wholesale instead of applying what fits. The daemon is the authority on whether the hook works.
     */
    private fun applyPolicy() {
        val result = run("/data/adb/ksud sepolicy apply '$POLICY_PATH'")
        check(result.isSuccess) { "could not hand $POLICY_PATH to ksud" }
    }

    /** Whether a daemon process of this boot is alive; the same condition the probe reports. */
    private fun daemonAlive(): Boolean =
        output("if $DAEMON_ALIVE_COND; then echo 1; else echo 0; fi").trim() == "1"

    /**
     * Waits for the daemon to appear, at most [DAEMON_START_TIMEOUT_MS].
     *
     * Best effort by design: the caller reads [status] afterwards and the page reports what it
     * finds, so a daemon that is genuinely broken still surfaces as "enabled but not running".
     */
    private fun awaitDaemon(timeoutMs: Long = DAEMON_START_TIMEOUT_MS) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!daemonAlive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(DAEMON_POLL_MS)
        }
    }

    /** Starts the respawn loop once; a second copy would run a second daemon. */
    private fun startDaemon() {
        run(
            "if ! pgrep -f '$RUN_SCRIPT' >/dev/null 2>&1; then " +
                "if command -v setsid >/dev/null 2>&1; then setsid sh $RUN_SCRIPT " +
                ">/dev/null 2>&1 & else sh $RUN_SCRIPT >/dev/null 2>&1 & fi; fi",
        )
    }

    /**
     * Stops the daemon the way a reboot would.
     *
     * The respawn loop restarts the daemon every two seconds, so killing the daemon alone leaves it
     * running again a moment later; the loop itself has to go first. Both commands are best effort
     * - a runtime that never started has neither process.
     */
    private fun stopDaemon() {
        run(
            "pkill -f '$RUN_SCRIPT'",
            "kill \$(pidof teesim) 2>/dev/null",
        )
    }

    /**
     * Unpacks the bundled payload into [stage].
     *
     * The asset is a zip inside an XZ stream. About nine tenths of the payload is native code,
     * which is where deflate gives up and LZMA does not: the same entries weigh 5.9MB here against
     * 9.0MB as a plain zip. XZ decodes sequentially, so the reader wraps the stream and the zip
     * entries come straight out of it - nothing is unpacked to disk first. The dictionary is
     * capped at 16MiB when the archive is built, which keeps the decoder's footprint well under
     * what any supported device has to spare.
     */
    private fun extract(stage: File) {
        val root = stage.canonicalPath + File.separator
        ksuApp.assets.open(ASSET_ARCHIVE).use { input ->
            XZInputStream(BufferedInputStream(input)).use { xz ->
                ZipInputStream(xz).use { zip ->
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
