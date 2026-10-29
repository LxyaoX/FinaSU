package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

/**
 * The switches `pif.prop` carries.
 *
 * [key] is the spelling the file uses; the module reads them case-insensitively but writes them
 * exactly like this, so rewriting a line keeps the file readable for both.
 */
enum class PifOption(val key: String) {
    SPOOF_BUILD("spoofBuild"),
    SPOOF_PROPS("spoofProps"),
    SPOOF_PROVIDER("spoofProvider"),
    SPOOF_SIGNATURE("spoofSignature"),
    SPOOF_VENDING_BUILD("spoofVendingBuild"),
    SPOOF_VENDING_SDK("spoofVendingSdk"),
}

/**
 * The identity the module presents, plus its switches.
 *
 * Only the fields the page shows are modelled; the file may carry more (`DEBUG`, vendor-specific
 * extras) and those lines are preserved verbatim by [PifProp.withOption] when a switch is flipped.
 */
@Immutable
data class PifConfig(
    val fingerprint: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val securityPatch: String = "",
    val options: Map<PifOption, Boolean> = emptyMap(),
) {
    fun isOn(option: PifOption): Boolean = options[option] ?: false

    companion object {
        /** Parses `KEY=VALUE` lines, ignoring comments and blanks. */
        fun parse(text: String): PifConfig {
            var fingerprint = ""
            var manufacturer = ""
            var model = ""
            var securityPatch = ""
            val options = mutableMapOf<PifOption, Boolean>()
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val split = line.indexOf('=')
                if (split <= 0) continue
                val key = line.substring(0, split).trim()
                val value = line.substring(split + 1).trim()
                val option = PifOption.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
                when {
                    option != null -> options[option] = value.equals("true", ignoreCase = true)
                    key.equals("FINGERPRINT", ignoreCase = true) -> fingerprint = value
                    key.equals("MANUFACTURER", ignoreCase = true) -> manufacturer = value
                    key.equals("MODEL", ignoreCase = true) -> model = value
                    key.equals("SECURITY_PATCH", ignoreCase = true) -> securityPatch = value
                }
            }
            return PifConfig(fingerprint, manufacturer, model, securityPatch, options)
        }
    }
}

/**
 * Line-level edits of `pif.prop`.
 *
 * The file is upstream's: it carries comments and keys this manager does not model, and the module
 * reads it on every boot. Rewriting it from a parsed model would drop both, so a switch flip only
 * replaces the one line it owns.
 */
object PifProp {
    fun withOption(text: String, option: PifOption, value: Boolean): String {
        val replacement = "${option.key}=$value"
        // The file arrives from `cat`, so it ends in a newline and `lines()` would hand back a
        // final empty element; joining that back would add a blank line on every flip.
        val lines = text.trimEnd('\n').lines().toMutableList()
        val index = lines.indexOfFirst { line ->
            val trimmed = line.trim()
            val split = trimmed.indexOf('=')
            split > 0 && trimmed.substring(0, split).trim().equals(option.key, ignoreCase = true)
        }
        if (index >= 0) {
            lines[index] = replacement
        } else {
            lines += replacement
        }
        return lines.joinToString("\n", postfix = "\n")
    }
}
