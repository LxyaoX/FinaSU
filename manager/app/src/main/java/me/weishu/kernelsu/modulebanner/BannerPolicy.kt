package me.weishu.kernelsu.modulebanner

/**
 * One effective policy shared by settings, loading and card rendering.
 *
 * Unlike FolkPatch, the custom-banner switch gates every source so disabling it also hides cached
 * API and module images. Nested choices remain intact to restore the previous configuration.
 */
data class BannerPolicy(
    val enabled: Boolean,
    val folkEnabled: Boolean,
    val apiModeEnabled: Boolean,
    val apiSource: String,
) {
    /** Master switch for module banners, and the custom-banner switch both silence every source. */
    val active: Boolean get() = enabled && folkEnabled

    /** The user-picked image, and the pick/clear rows of the long-press dialog. */
    val localActive: Boolean get() = active

    /** The API or local-directory source, which API mode owns once custom banners are on. */
    val apiActive: Boolean get() = active && apiModeEnabled

    /** True when the api source is usable as well. */
    val apiConfigured: Boolean get() = apiActive && apiSource.isNotEmpty()
}
