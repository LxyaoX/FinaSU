package me.weishu.kernelsu.data.model

/**
 * The home page has one layout, so this is no longer a choice - it is the token the wallpaper
 * surface registry keys on, and the value a `.fpt` package may still carry.
 *
 * Themes written for the layouts this manager no longer draws keep their `homeLayoutStyle` key; it
 * is read as [HOME] and has no effect.
 */
object HomeLayoutStyle {
    /** The single-column home this manager draws. */
    const val HOME = "circle"

    /** Alias kept for stored configurations and theme packages. */
    const val DEFAULT = HOME

    /** Every stored or imported token resolves to [HOME]. */
    fun fromValue(value: String?): String = HOME
}
