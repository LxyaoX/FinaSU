package me.weishu.kernelsu.modulebanner

/** One effective policy shared by settings, loading and card rendering. Child choices are retained. */
data class BannerPolicy(
    val enabled: Boolean,
    val folkEnabled: Boolean,
    val apiModeEnabled: Boolean,
    val apiSource: String,
) {
    val active: Boolean get() = enabled && folkEnabled
    val apiActive: Boolean get() = active && apiModeEnabled
}
