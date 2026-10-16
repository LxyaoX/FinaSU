package me.weishu.kernelsu.data.model

/**
 * Overall layout variant of the main pages.
 *
 * [M3E] is the default: Material 3 Expressive large titles, with a collapsible
 * inline search bar on the pages that own a search.
 * [TRADITIONAL] keeps the historical look: compact top bars everywhere.
 */
object LayoutVariant {
    // Stored as "default" so preferences written before the swap keep resolving
    // to the traditional look.
    const val TRADITIONAL = "default"
    const val M3E = "m3e"

    fun fromValue(value: String?): String = when (value) {
        TRADITIONAL -> TRADITIONAL
        else -> M3E
    }

    val supported: List<String> = listOf(M3E, TRADITIONAL)
}
