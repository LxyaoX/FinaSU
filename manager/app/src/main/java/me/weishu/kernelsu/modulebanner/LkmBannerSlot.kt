package me.weishu.kernelsu.modulebanner

/** Banner and custom-info family for managed Linux kernel modules. */
object LkmBannerSlot : ModuleBannerSlot {
    override val key = "lkm"
    override val bannerDirName = "lkm_banners"
    override val customInfoDirName = "lkm_custom_info"
    override val moduleRoots = listOf("/data/adb/ksu/lkm")
    override val supportsModulePropBanner = false
}
