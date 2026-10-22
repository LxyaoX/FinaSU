package me.weishu.kernelsu.data.bootprops

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import java.io.File
import me.weishu.kernelsu.data.model.BootProps
import me.weishu.kernelsu.data.model.BootPropsStage
import me.weishu.kernelsu.data.model.BootPropsStatus
import me.weishu.kernelsu.ksuApp

/**
 * Rewrites the boot and build properties a detector judges the device by.
 *
 * Two scripts, both in stage directories `ksud` already executes for every install:
 *
 * - `/data/adb/post-fs-data.d/neosu_bootprops.sh`, before zygote, for the debuggable/ADB/signature
 *   facts;
 * - `/data/adb/boot-completed.d/neosu_bootprops.sh`, once boot completed, for the lock state, so
 *   OEM HALs that read it during boot are not lied to.
 *
 * Turning the switch on also runs both immediately: `resetprop -n` writes the property area that
 * every process has mapped, so the new values are visible to running apps without a reboot.
 * Turning it off deletes the scripts; the values already written stay until the next reboot,
 * because the real ones are gone (init only sets them once, from the bootloader).
 */
object BootPropsInstaller {
    private const val ADB_DIR = "/data/adb"

    fun status(): BootPropsStatus {
        val stdout = ArrayList<String>()
        Shell.getShell().newJob().add(BootProps.probeScript).to(stdout).exec()
        return BootProps.statusFrom(stdout.joinToString("\n"))
    }

    /** Writes both stage scripts and applies them right away. */
    fun enable(current: BootPropsStatus) {
        check(current.ksudAvailable) { "ksud is missing from $ADB_DIR, so ro. properties cannot be written" }
        BootPropsStage.entries.forEach(::write)
        apply()
    }

    /** Deletes both scripts; the already rewritten values survive until the next reboot. */
    fun disable() {
        val script = BootPropsStage.entries.joinToString(" && ") {
            "rm -f $ADB_DIR/${it.directory}/${BootProps.SCRIPT_NAME}"
        }
        check(ShellUtils.fastCmdResult(script)) { "could not delete the boot property scripts" }
    }

    /** Runs both stages now, so the switch has an effect without waiting for a reboot. */
    fun apply() {
        val script = BootPropsStage.entries.joinToString("; ") {
            "sh $ADB_DIR/${it.directory}/${BootProps.SCRIPT_NAME} >/dev/null 2>&1"
        }
        ShellUtils.fastCmd(script)
    }

    private fun write(stage: BootPropsStage) {
        val directory = "$ADB_DIR/${stage.directory}"
        val target = "$directory/${BootProps.SCRIPT_NAME}"
        val cached = File(ksuApp.cacheDir, "neosu_bootprops_${stage.name.lowercase()}.sh")
        cached.writeText(BootProps.bootScript(stage))
        check(
            ShellUtils.fastCmdResult(
                "mkdir -p '$directory' && " +
                    "cp '${cached.absolutePath}' '$target' && " +
                    "chmod 0755 '$target' && " +
                    "chown 0:0 '$target'"
            )
        ) { "could not write $target" }
    }
}
