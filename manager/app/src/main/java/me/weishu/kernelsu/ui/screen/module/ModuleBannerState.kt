package me.weishu.kernelsu.ui.screen.module

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.modulebanner.ApmBannerSlot
import me.weishu.kernelsu.modulebanner.BannerApiClient
import me.weishu.kernelsu.modulebanner.BannerPolicy
import me.weishu.kernelsu.modulebanner.CustomModuleInfo
import me.weishu.kernelsu.modulebanner.ModuleBannerCache
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

    // produceState restarts on a key change but keeps its previous value, and a card starts from what
    // it resolved last time. Flipping a switch therefore repaints the list instead of emptying it,
    // and no card has to wait for a root shell to show something again. The module id travels with
    // the value so a slot that ever gets reused can never paint another module's banner.
    val state = produceState(
        initialValue = moduleId to ModuleCardVisual(banner = cachedBanner(slot, moduleId, policy)),
        moduleId,
        slot,
        context,
        policy,
        reloadKey,
    ) {
        value = moduleId to withContext(Dispatchers.IO) { resolveCard(context, slot, moduleId, policy, reloadKey) }
    }
    val (owner, visual) = state.value
    return if (owner == moduleId) visual else ModuleCardVisual(banner = cachedBanner(slot, moduleId, policy))
}

/** The banner this card resolved last, as long as the current policy still allows it. */
private fun cachedBanner(
    slot: ModuleBannerSlot,
    moduleId: String,
    policy: BannerPolicy,
): ModuleBannerVisual? {
    val entry = ModuleBannerCache.entry(slot, moduleId) ?: return null
    if (!entry.allowedBy(policy)) return null
    entry.bytes?.let { return ModuleBannerVisual(it) }
    return entry.url?.let { ModuleBannerVisual(it, isRemote = true) }
}

private suspend fun resolveCard(
    context: Context,
    slot: ModuleBannerSlot,
    moduleId: String,
    policy: BannerPolicy,
    reloadKey: Int,
): ModuleCardVisual {
    // The metadata overrides are not a banner source, so they are read either way.
    val customInfo = runCatching { ModuleCustomInfoStorage(slot, context).read(moduleId) }.getOrNull()
    return ModuleCardVisual(
        banner = resolveBanner(context, slot, moduleId, policy, reloadKey),
        customInfo = customInfo,
    )
}

private suspend fun resolveBanner(
    context: Context,
    slot: ModuleBannerSlot,
    moduleId: String,
    policy: BannerPolicy,
    reloadKey: Int,
): ModuleBannerVisual? {
    // Master switch: every source is silent. The cached entry stays, so turning it back on is instant.
    if (!policy.active) {
        return null
    }

    return try {
        // The API switch owns the API source; it is not gated by the custom-banner switch.
        val api = if (policy.apiConfigured) {
            ModuleBannerCache.resolvePermits.withPermit {
                BannerApiClient.load(context, moduleId, policy.apiSource)
            }
        } else {
            null
        }
        if (api != null) {
            ModuleBannerCache.put(slot, moduleId, ModuleBannerCache.Entry(ModuleBannerCache.Origin.Api, bytes = api))
            return ModuleBannerVisual(api)
        }

        // The custom-banner switch owns the user-picked image.
        val picked = if (policy.localActive) {
            ModuleBannerCache.resolvePermits.withPermit { ModuleBannerStorage(slot, context).read(moduleId) }
        } else {
            null
        }
        if (picked != null) {
            ModuleBannerCache.put(slot, moduleId, ModuleBannerCache.Entry(ModuleBannerCache.Origin.UserPick, bytes = picked))
            return ModuleBannerVisual(picked)
        }

        // Whatever the module itself ships is part of the module's own banner, so the top switches
        // silence it too, and this shell-backed read is the part worth caching.
        ModuleBannerCache.moduleEntry(slot, moduleId, reloadKey)?.let {
            return it.toVisual()
        }

        val resolved = ModuleBannerCache.resolvePermits.withPermit { readModuleOwnBanner(slot, moduleId) }
        ModuleBannerCache.put(slot, moduleId, resolved.toEntry(reloadKey))
        resolved
    } catch (_: Exception) {
        // A failing root shell or an unreadable file must never kill the producer: the card would
        // stay blank and no amount of switch flipping would bring the banner back.
        cachedBanner(slot, moduleId, policy)
    }
}

private fun ModuleBannerCache.Entry.toVisual(): ModuleBannerVisual? {
    bytes?.let { return ModuleBannerVisual(it) }
    return url?.let { ModuleBannerVisual(it, isRemote = true) }
}

/** An imageless entry is meaningful: it records that the module ships no banner of its own. */
private fun ModuleBannerVisual?.toEntry(reloadKey: Int): ModuleBannerCache.Entry = when {
    this == null -> ModuleBannerCache.Entry(ModuleBannerCache.Origin.Module, reloadKey = reloadKey)
    isRemote -> ModuleBannerCache.Entry(ModuleBannerCache.Origin.Module, url = model as String, reloadKey = reloadKey)
    else -> ModuleBannerCache.Entry(ModuleBannerCache.Origin.Module, bytes = model as ByteArray, reloadKey = reloadKey)
}

private fun readModuleOwnBanner(slot: ModuleBannerSlot, moduleId: String): ModuleBannerVisual? {
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
