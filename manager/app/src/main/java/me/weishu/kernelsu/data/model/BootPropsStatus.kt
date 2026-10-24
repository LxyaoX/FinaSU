package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

/**
 * One system property the manager rewrites, and how far it may go.
 *
 * All of these are `ro.` properties that init fills in from the bootloader state or from the
 * build signature, which is exactly why a detector can judge a device without asking it
 * anything: `ro.boot.flash.locked`, `ro.boot.verifiedbootstate` and `ro.boot.vbmeta.device_state`
 * describe the lock state, `ro.*.build.tags` describes whether the build is a release one.
 *
 * KernelSU ships `resetprop` inside `ksud`, which writes the property area directly instead of
 * going through `property_service`, so a `ro.` property can still be corrected after init froze
 * it - the same mechanism a Magisk module would use, without the module.
 */
data class BootProp(
    val name: String,
    val value: String,
    /**
     * Create the property when it does not exist at all.
     *
     * Detectors tell "absent" apart from "wrong" - `ro.boot.vbmeta.device_state` is printed as
     * `<unavailable>` rather than `unlocked` - and absent is what a device whose bootloader never
     * reported the fact looks like. Only properties whose absence is itself a giveaway are
     * created; everything else leaves a missing property missing.
     */
    val createIfMissing: Boolean = false,
)

/** A family of properties matched by [pattern] and rewritten to the same [value]. */
data class GlobProp(
    /** Regex over the property name, usable both by `sed` in the boot script and by Kotlin. */
    val pattern: String,
    val value: String,
)

/** When a script may rewrite its properties. */
enum class BootPropsStage(val directory: String) {
    /**
     * Before zygote: nothing has read the properties yet, so they are corrected for every process
     * of the boot.
     */
    EARLY("post-fs-data.d"),

    /**
     * Once boot completed.
     *
     * The lock-state properties are deliberately left alone until then: several OEM HALs
     * (fingerprint, TEE) read them and refuse to work for a device that claims to be locked while
     * it is not, so they are corrected after the risky part of the boot is over.
     */
    LATE("boot-completed.d"),
}

/**
 * State of the boot-property rewrite, as read back from the device.
 *
 * `enabled` is not a preference: it is whether both boot scripts are on disk, i.e. what the next
 * boot will actually do.
 */
@Immutable
data class BootPropsStatus(
    val enabled: Boolean = false,
    /** `ksud` is on disk, so there is something that can write a `ro.` property at all. */
    val ksudAvailable: Boolean = false,
    /** Properties the probe covered: the tracked ones plus every match of the globbed families. */
    val tracked: Int = 0,
    /** Tracked properties that already carry the expected value. */
    val applied: Int = 0,
    /** Tracked properties still deviating, or missing although they should exist. */
    val pending: List<String> = emptyList(),
) {
    val allApplied: Boolean get() = tracked > 0 && pending.isEmpty()
}

/**
 * The property table the switch owns, and the two scripts generated from it.
 *
 * The list follows PlayIntegrityFix (GPL-3.0, KOWX712) - see `module/post-fs-data.sh` and
 * `module/service.sh` upstream - with the recovery-mode rows left out, since a recovery boot never
 * runs these stages here.
 */
object BootProps {
    private const val KSUD = "/data/adb/ksud"

    /** File name used in both stage directories; the same name is what the probe looks for. */
    const val SCRIPT_NAME = "neosu_bootprops.sh"

    private const val PROP_PREFIX = "bp:"
    private const val META_PREFIX = "bpm:"

    /** Debuggable, ADB and signature facts: safe to fix before anything reads them. */
    val early: List<BootProp> = listOf(
        BootProp("ro.debuggable", "0"),
        BootProp("ro.secure", "1"),
        BootProp("ro.adb.secure", "1"),
        BootProp("ro.force.debuggable", "0"),
        // Samsung reports the bootloader warranty bit; non-Samsung devices simply lack it.
        BootProp("ro.boot.warranty_bit", "0"),
        BootProp("ro.vendor.boot.warranty_bit", "0"),
        BootProp("ro.vendor.warranty_bit", "0"),
        BootProp("ro.warranty_bit", "0"),
        // Realme / OnePlus boot-state leftovers.
        BootProp("ro.boot.realmebootstate", "green"),
        BootProp("ro.is_ever_orange", "0"),
    )

    /** Lock state. Applied after boot completed, see [BootPropsStage.LATE]. */
    val late: List<BootProp> = listOf(
        BootProp("ro.boot.flash.locked", "1"),
        BootProp("ro.boot.verifiedbootstate", "green"),
        // The one property an unlocked device often does not have at all.
        BootProp("ro.boot.vbmeta.device_state", "locked", createIfMissing = true),
        BootProp("ro.boot.veritymode", "enforcing"),
        BootProp("vendor.boot.verifiedbootstate", "green"),
        BootProp("vendor.boot.vbmeta.device_state", "locked"),
        BootProp("ro.secureboot.lockstate", "locked"),
        BootProp("ro.boot.realme.lockstate", "1"),
        BootProp("sys.oem_unlock_allowed", "0"),
    )

    /**
     * Build signature facts, which live under different prefixes per partition
     * (`ro.build.tags`, `ro.vendor.build.tags`, `ro.system.build.tags`, ...), so they are matched
     * rather than listed.
     */
    val earlyGlobs: List<GlobProp> = listOf(
        GlobProp("""ro\..*build\.tags""", "release-keys"),
        GlobProp("""ro\..*build\.type""", "user"),
    )

    fun propsOf(stage: BootPropsStage): List<BootProp> = when (stage) {
        BootPropsStage.EARLY -> early
        BootPropsStage.LATE -> late
    }

    fun globsOf(stage: BootPropsStage): List<GlobProp> = when (stage) {
        BootPropsStage.EARLY -> earlyGlobs
        BootPropsStage.LATE -> emptyList()
    }

    /** Every literal property name, in the order the scripts set them. */
    fun names(): List<String> = early.map { it.name } + late.map { it.name }

    /** The value [name] should end up with, or null when nothing tracks it. */
    fun expected(name: String): String? =
        (early + late).firstOrNull { it.name == name }?.value
            ?: earlyGlobs.firstOrNull { Regex("^${it.pattern}$").matches(name) }?.value

    /** Whether [name] is created when the firmware never reported it. */
    fun createsIfMissing(name: String): Boolean =
        (early + late).any { it.name == name && it.createIfMissing }

    /**
     * One round trip that reports both scripts and the current value of everything they touch.
     *
     * Properties the device does not have print an empty value: `resetprop NAME` exits non-zero
     * and says "not found" on stderr, which the `2>/dev/null` drops.
     */
    val probeScript: String = buildString {
        appendLine("KSUD=$KSUD")
        appendLine("""if [ -x "${'$'}KSUD" ]; then echo ${META_PREFIX}ksud=1; else echo ${META_PREFIX}ksud=0; fi""")
        for (stage in BootPropsStage.entries) {
            val path = "/data/adb/${stage.directory}/$SCRIPT_NAME"
            val key = stage.name.lowercase()
            appendLine("""if [ -e $path ]; then echo ${META_PREFIX}$key=1; else echo ${META_PREFIX}$key=0; fi""")
        }
        for (name in names()) {
            appendLine("""printf '${PROP_PREFIX}%s=%s\n' $name "${'$'}("${'$'}KSUD" resetprop $name 2>/dev/null)"""")
        }
        for (glob in earlyGlobs) {
            appendLine(
                """for p in $("${'$'}KSUD" resetprop 2>/dev/null | sed -n 's/^\[\(${glob.pattern}\)\]:.*/\1/p'); do"""
            )
            appendLine("""  printf '${PROP_PREFIX}%s=%s\n' "${'$'}p" "${'$'}("${'$'}KSUD" resetprop "${'$'}p" 2>/dev/null)"""")
            appendLine("done")
        }
    }

    /**
     * The boot script for [stage].
     *
     * Two helpers, because the two cases are genuinely different: a property that exists but says
     * the wrong thing is overwritten, while one the firmware never set stays absent unless the
     * table asks for it explicitly - inventing values for hardware facts the device does not have
     * is how a spoof starts tripping over itself.
     */
    fun bootScript(stage: BootPropsStage): String = buildString {
        appendLine("#!/system/bin/sh")
        appendLine("# Generated by KernelSU: rewrites the system properties detectors read to judge the")
        appendLine("# boot state. Turning the switch off in KernelSU deletes this file; the properties")
        appendLine("# then keep their rewritten values until the next reboot.")
        appendLine("KSUD=$KSUD")
        appendLine("""[ -x "${'$'}KSUD" ] || exit 0""")
        appendLine()
        appendLine("# set_if_diff <name> <value>: correct an existing property that deviates.")
        appendLine("# A property this device never had is left absent.")
        appendLine("set_if_diff() {")
        appendLine("""  cur=$("${'$'}KSUD" resetprop "${'$'}1" 2>/dev/null)""")
        appendLine("""  if [ -n "${'$'}cur" ] && [ "${'$'}cur" != "${'$'}2" ]; then""")
        appendLine("""    "${'$'}KSUD" resetprop -n "${'$'}1" "${'$'}2" >/dev/null 2>&1""")
        appendLine("  fi")
        appendLine("  return 0")
        appendLine("}")
        appendLine()
        appendLine("# set_always <name> <value>: also create the property when it is missing.")
        appendLine("set_always() {")
        appendLine("""  if [ "$("${'$'}KSUD" resetprop "${'$'}1" 2>/dev/null)" != "${'$'}2" ]; then""")
        appendLine("""    "${'$'}KSUD" resetprop -n "${'$'}1" "${'$'}2" >/dev/null 2>&1""")
        appendLine("  fi")
        appendLine("  return 0")
        appendLine("}")
        appendLine()
        for (prop in propsOf(stage)) {
            val setter = if (prop.createIfMissing) "set_always" else "set_if_diff"
            appendLine("$setter ${prop.name} ${prop.value}")
        }
        for (glob in globsOf(stage)) {
            if (propsOf(stage).isNotEmpty()) appendLine()
            appendLine("# ${glob.value} for every partition that carries the property.")
            appendLine(
                """for p in $("${'$'}KSUD" resetprop 2>/dev/null | sed -n 's/^\[\(${glob.pattern}\)\]:.*/\1/p'); do"""
            )
            appendLine("""  set_if_diff "${'$'}p" ${glob.value}""")
            appendLine("done")
        }
    }

    /** Reads [probeScript]'s output back. */
    fun statusFrom(output: String): BootPropsStatus {
        val values = LinkedHashMap<String, String>()
        var ksud = false
        var scripts = 0
        for (line in output.lineSequence()) {
            val isMeta = line.startsWith(META_PREFIX)
            if (!isMeta && !line.startsWith(PROP_PREFIX)) continue
            val body = line.removePrefix(if (isMeta) META_PREFIX else PROP_PREFIX)
            val separator = body.indexOf('=')
            if (separator <= 0) continue
            val key = body.substring(0, separator)
            val value = body.substring(separator + 1).trim()
            if (isMeta) {
                if (key == "ksud") ksud = value == "1" else if (value == "1") scripts++
            } else {
                values[key] = value
            }
        }

        val pending = values.entries
            .mapNotNull { (name, current) ->
                val want = expected(name) ?: return@mapNotNull null
                // An absent property only counts when the stage is supposed to create it: the
                // other rows deliberately leave what the firmware never reported absent, and
                // reporting that as "not fixed yet" would leave the list permanently red.
                val missingButIntended = current.isEmpty() && !createsIfMissing(name)
                if (current == want || missingButIntended) null else name
            }
            .toList()
        return BootPropsStatus(
            enabled = scripts == BootPropsStage.entries.size,
            ksudAvailable = ksud,
            tracked = values.size,
            applied = values.size - pending.size,
            pending = pending,
        )
    }
}
