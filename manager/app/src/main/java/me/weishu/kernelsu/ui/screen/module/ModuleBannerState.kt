package me.weishu.kernelsu.ui.screen.module

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.modulebanner.ApmBannerSlot
import me.weishu.kernelsu.modulebanner.BannerApiClient
import me.weishu.kernelsu.modulebanner.BannerPolicy
import me.weishu.kernelsu.modulebanner.CustomModuleInfo
import me.weishu.kernelsu.modulebanner.ModuleBannerConfig
import me.weishu.kernelsu.modulebanner.ModuleBannerResolver
import me.weishu.kernelsu.modulebanner.ModuleBannerSlot
import me.weishu.kernelsu.modulebanner.ModuleBannerStorage
import me.weishu.kernelsu.modulebanner.ModuleCustomInfoStorage

/** A banner image: in-memory bytes for a local source, or a URL Coil should fetch itself. */
@Immutable
data class ModuleBannerVisual(
    val model: Any,
    val isRemote: Boolean = false,
)

@Immutable
data class ModuleCardVisual(
    val banner: ModuleBannerVisual? = null,
    val customInfo: CustomModuleInfo? = null,
)

/**
 * Resolves everything the module card needs from a slot: the banner image (walking the configured
 * source chain) and the user's metadata overrides. Re-runs whenever a banner setting or the reload
 * key changes, so the list repaints on its own.
 */
@Composable
fun rememberModuleCardVisual(
    moduleId: String,
    slot: ModuleBannerSlot = ApmBannerSlot,
): ModuleCardVisual {
    val policy = ModuleBannerConfig.policy
    val reloadKey = ModuleBannerConfig.reloadKey
    val context = LocalContext.current

    // produceState keys restart work but retain its previous value. A composition key also resets
    // the holder, so rapid off/on, source edits and recycled cards cannot show an old banner.
    val state = key(moduleId, slot, context, policy, reloadKey) {
        produceState(initialValue = ModuleCardVisual()) {
            value = withContext(Dispatchers.IO) {
                ModuleCardVisual(
                    banner = resolveBanner(context, slot, moduleId, policy),
                    customInfo = ModuleCustomInfoStorage(slot, context).read(moduleId),
                )
            }
        }
    }
    return if (policy.active) state.value else state.value.copy(banner = null)
}

private suspend fun resolveBanner(
    context: Context,
    slot: ModuleBannerSlot,
    moduleId: String,
    policy: BannerPolicy,
): ModuleBannerVisual? {
    if (!policy.active) return null

    if (policy.apiActive && policy.apiSource.isNotEmpty()) {
        BannerApiClient.load(context, moduleId, policy.apiSource)?.let { return ModuleBannerVisual(it) }
    }
    ModuleBannerStorage(slot, context).read(moduleId)?.let { return ModuleBannerVisual(it) }
    if (slot.supportsModulePropBanner) {
        val declared = ModuleBannerResolver.readModulePropBanner(slot, moduleId)
        if (!declared.isNullOrBlank()) {
            if (ModuleBannerResolver.isRemoteSource(declared)) {
                return ModuleBannerVisual(declared, isRemote = true)
            }
            ModuleBannerResolver.readBannerFileAt(slot, moduleId, declared)?.let {
                return ModuleBannerVisual(it)
            }
        }
    }
    ModuleBannerResolver.readModuleFileBanner(slot, moduleId)?.let { return ModuleBannerVisual(it) }
    return null
}
