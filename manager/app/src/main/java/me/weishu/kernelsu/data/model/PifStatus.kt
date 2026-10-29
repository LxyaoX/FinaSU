package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

/** What is on disk for the bundled PlayIntegrityFix module, and what it would do. */
@Immutable
data class PifStatus(
    val installed: Boolean = false,
    /** False while ksud sees the `disable` marker; the module stays on disk but is not loaded. */
    val enabled: Boolean = false,
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
            var version = ""
            for (raw in probe.lineSequence()) {
                val line = raw.trim()
                when {
                    line == "installed=1" -> installed = true
                    line == "disabled=1" -> disabled = true
                    line.startsWith("version=") -> version = line.removePrefix("version=").trim()
                }
            }
            return PifStatus(
                installed = installed,
                enabled = installed && !disabled,
                version = version,
                config = if (installed) PifConfig.parse(configText) else PifConfig(),
            )
        }
    }
}
