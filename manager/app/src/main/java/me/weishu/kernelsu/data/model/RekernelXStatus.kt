package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

/**
 * State of the ReKernel-X tombstone driver NeoSU ships inside the manager.
 *
 * The `.ko` files live in `assets/rekernel_x` (ReKernel-X 1.6, GPL-2.0) so the driver no longer
 * needs to be flashed as a separate module: the manager stages the file matching the running
 * kernel under `/data/adb/rekernel_x` and drops a `post-fs-data.d` script that loads it at boot.
 */
@Immutable
data class RekernelXStatus(
    /** `/sys/module/rekernel_x` exists, so the driver is loaded in this boot. */
    val loaded: Boolean = false,
    /** The boot script is in place, i.e. the next reboot loads the driver. */
    val autoLoad: Boolean = false,
    /** Driver file staged under `/data/adb/rekernel_x`, empty when none was staged yet. */
    val stagedDriver: String = "",
    /** `uname -r`, shown when no bundled build matches the kernel. */
    val kernelRelease: String = "",
    /** `android<ver>-<core>` of the running kernel, empty when it cannot be derived. */
    val kmi: String = "",
    /** Bundled driver that matches [kmi], empty when the kernel is unsupported. */
    val bundledDriver: String = "",
    /** A flashed ReKernel-X module owns the driver; the bundled build stays unused. */
    val externalModule: Boolean = false,
) {
    /** The switch reflects the boot decision, not the momentary module state. */
    val enabled: Boolean get() = autoLoad

    val kmiSupported: Boolean get() = bundledDriver.isNotEmpty()

    companion object {
        private const val PROBE_PREFIX = "rkx:"

        /**
         * `android<ver>-<core>` for a `uname -r` release, mirroring the upstream `customize.sh`.
         *
         * Release names look like `6.1.75-android14-11-gabcdef` (GKI) or `5.10.101-android12-9`
         * (android12-5.10). Non-GKI names fall back to the core-version table.
         */
        fun kmiOf(release: String): String {
            val trimmed = release.trim()
            if (trimmed.isEmpty()) return ""
            val core = trimmed.substringBefore('-').substringBeforeLast('.')
            if (core.count { it == '.' } != 1) return ""
            val android = if (trimmed.contains("-android")) {
                trimmed.substringAfter("-android").substringBefore('-')
            } else {
                when (core) {
                    "5.10" -> "12"
                    "5.15" -> "13"
                    "6.1" -> "14"
                    "6.6" -> "15"
                    "6.12" -> "16"
                    "6.18" -> "17"
                    else -> ""
                }
            }
            if (android.isEmpty() || android.toIntOrNull() == null) return ""
            return "android$android-$core"
        }

        fun fromProbe(output: String): RekernelXStatus {
            val values = output.lineSequence()
                .filter { it.startsWith(PROBE_PREFIX) }
                .mapNotNull { line ->
                    val body = line.removePrefix(PROBE_PREFIX)
                    val separator = body.indexOf('=')
                    if (separator <= 0) null
                    else body.substring(0, separator) to body.substring(separator + 1).trim()
                }
                .toMap()

            val release = values["release"].orEmpty()
            val kmi = kmiOf(release)
            return RekernelXStatus(
                loaded = values["loaded"] == "1",
                autoLoad = values["autoload"] == "1",
                stagedDriver = values["driver"].orEmpty(),
                kernelRelease = release,
                kmi = kmi,
                externalModule = values["module"] == "1",
            )
        }
    }
}
