package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * State of the TEESimulator module the manager carries in `assets/teesim`.
 *
 * TEESimulator (GPL-3.0) answers key attestation for the selected apps from a reference KeyMint
 * trusted application that runs inside the real keystore daemon, signing the chain with a
 * user-supplied keybox; keys of every other app still go to the real hardware. Upstream ships it
 * as a flashable module, so "bundled" here means the manager installs that module itself into
 * `/data/adb/modules/teesim` and the settings page owns the switch.
 */
@Immutable
data class TeesimStatus(
    val installed: Boolean = false,
    /** The module is installed and carries no `disable` marker. */
    val enabled: Boolean = false,
    /** `version=` of the installed `module.prop`, empty when nothing is installed. */
    val version: String = "",
    /** A process named `teesim` (the control daemon) is alive in this boot. */
    val daemonRunning: Boolean = false,
    val keyboxPresent: Boolean = false,
    val keyboxBytes: Long = 0L,
    /** The installed module carries a `webroot`, so its WebUI can be opened. */
    val webUiAvailable: Boolean = false,
    val config: TeesimConfig = TeesimConfig(),
) {
    /** Everything the daemon needs is on disk, so the next boot loads the hook. */
    val ready: Boolean get() = installed && enabled && keyboxPresent

    /** Packages the simulator serves, i.e. the `default` profile. */
    val targetApps: List<String> get() = config.defaultApps

    /** Packages owned by other profiles, which the manager does not edit. */
    val otherApps: List<String> get() = config.otherProfileApps

    companion object {
        private const val PROBE_PREFIX = "tee:"

        /**
         * Parses the `tee:key=value` probe printed by `TeesimInstaller`. A missing key is treated
         * as its empty value, so a partially readable module directory degrades instead of
         * throwing.
         */
        fun fromProbe(output: String): TeesimStatus {
            val values = output.lineSequence()
                .filter { it.startsWith(PROBE_PREFIX) }
                .mapNotNull { line ->
                    val body = line.removePrefix(PROBE_PREFIX)
                    val separator = body.indexOf('=')
                    if (separator <= 0) null
                    else body.substring(0, separator) to body.substring(separator + 1).trim()
                }
                .toMap()

            val installed = values["installed"] == "1"
            return TeesimStatus(
                installed = installed,
                enabled = installed && values["disabled"] != "1",
                version = values["version"].orEmpty(),
                daemonRunning = values["running"] == "1",
                keyboxPresent = values["keybox"] == "1",
                keyboxBytes = values["keyboxbytes"]?.toLongOrNull() ?: 0L,
                webUiAvailable = values["webui"] == "1",
            )
        }
    }
}

/**
 * The slice of `/data/adb/teesim/config.json` the manager edits.
 *
 * Upstream groups apps into named profiles, each with its own keybox and device identity. The
 * settings page deliberately owns only the `default` profile: apps assigned elsewhere are reported
 * separately so the picker cannot silently move them into a profile whose keybox the user chose
 * for a reason.
 */
@Immutable
data class TeesimConfig(
    /** Packages whose attestation the simulator answers, i.e. the `default` profile. */
    val defaultApps: List<String> = emptyList(),
    /** Packages owned by other profiles; the picker leaves them alone. */
    val otherProfileApps: List<String> = emptyList(),
    val profileCount: Int = 0,
    /** `config.json` is missing or does not parse, so the app list cannot be trusted. */
    val broken: Boolean = false,
) {
    companion object {
        const val DEFAULT_PROFILE = "default"

        fun parse(json: String?): TeesimConfig {
            if (json.isNullOrBlank()) return TeesimConfig(broken = true)
            val profiles = runCatching { JSONObject(json).optJSONObject("profiles") }.getOrNull()
                ?: return TeesimConfig(broken = true)

            val default = LinkedHashSet<String>()
            val others = LinkedHashSet<String>()
            var count = 0
            val names = profiles.keys()
            while (names.hasNext()) {
                val name = names.next()
                val profile = profiles.optJSONObject(name) ?: continue
                count++
                val apps = profile.optJSONArray("apps") ?: continue
                val sink = if (name == DEFAULT_PROFILE) default else others
                for (index in 0 until apps.length()) {
                    val packageName = apps.optString(index).trim()
                    if (packageName.isNotEmpty()) sink.add(packageName)
                }
            }
            return TeesimConfig(
                defaultApps = default.sorted(),
                otherProfileApps = others.sorted(),
                profileCount = count,
            )
        }
    }
}
