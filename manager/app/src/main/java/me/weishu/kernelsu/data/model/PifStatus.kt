package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

/** What is on disk for the bundled PlayIntegrityFix payload, and what it would do. */
@Immutable
data class PifStatus(
    val installed: Boolean = false,
    /** False while ksud sees the `disable` marker; the payload stays on disk but is not loaded. */
    val enabled: Boolean = false,
    /**
     * Whether the module path points at the built-in payload. False with [installed] true and
     * [external] false means the payload is on disk but nothing links it, which is a state worth
     * showing rather than hiding: zygiskd loads nothing in it.
     */
    val linked: Boolean = false,
    /**
     * Whether `/data/adb/modules/playintegrityfix` is a real directory - an upstream install owns
     * the path, so the built-in payload cannot be linked until it is taken over.
     */
    val external: Boolean = false,
    val version: String = "",
    val config: PifConfig = PifConfig(),
) {
    companion object {
        /**
         * Builds the state from one probe.
         *
         * Every fact is optional: a missing `module.prop` means "not installed" and nothing else is
         * read, so a half-removed tree degrades to that single answer instead of guessing.
         */
        fun fromProbe(probe: String, configText: String = ""): PifStatus {
            var installed = false
            var disabled = false
            var linked = false
            var external = false
            var version = ""
            for (raw in probe.lineSequence()) {
                val line = raw.trim()
                when {
                    line == "installed=1" -> installed = true
                    line == "disabled=1" -> disabled = true
                    line == "linked=1" -> linked = true
                    line == "external=1" -> external = true
                    line.startsWith("version=") -> version = line.removePrefix("version=").trim()
                }
            }
            return PifStatus(
                installed = installed,
                enabled = installed && !disabled,
                linked = linked,
                external = external,
                version = version,
                config = if (installed) PifConfig.parse(configText) else PifConfig(),
            )
        }
    }
}
