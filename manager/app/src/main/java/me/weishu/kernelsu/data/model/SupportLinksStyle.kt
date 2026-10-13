package me.weishu.kernelsu.data.model

/**
 * Tokens persisted under the `support_links_style` preference key.
 *
 * The bottom card of the home screen can present three shapes; the token decides which one, so the
 * preference maps onto the same card across restarts. Unknown or legacy tokens fall back to
 * [FINASU] instead of failing. [supported] is the single source of truth the settings dropdown
 * iterates, so a new shape is added by extending this object alone.
 */
object SupportLinksStyle {
    /** The FinaSU introduction card, the default. */
    const val FINASU = "finasu"

    /** The upstream KernelSU pair: a donation row and a KernelSU documentation row. */
    const val LEGACY = "kernelsu"

    /** Draw nothing at the bottom of the home screen. */
    const val HIDDEN = "hidden"

    /** Style used when the preference is missing or holds an unsupported token. */
    const val DEFAULT = FINASU

    /** Normalise a stored value to a supported token. */
    fun fromValue(value: String?): String = when (value) {
        LEGACY -> LEGACY
        HIDDEN -> HIDDEN
        FINASU -> FINASU
        else -> DEFAULT
    }

    /** Every supported token, in display order. */
    val supported: List<String> = listOf(FINASU, LEGACY, HIDDEN)
}
