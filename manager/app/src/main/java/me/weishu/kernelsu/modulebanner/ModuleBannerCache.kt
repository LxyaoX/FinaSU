package me.weishu.kernelsu.modulebanner

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.sync.Semaphore

/**
 * What a card resolved the last time it asked.
 *
 * A module's own banner (`module.prop`'s `banner=` or a file in the module directory) can only be
 * read through a root shell, which is far too slow to repeat every time a banner switch flips.
 * FolkPatch keeps the previous answer in its module ViewModel for the same reason: the card then
 * keeps painting while the new configuration resolves, so toggling a switch never blanks the list.
 *
 * The entry records which switch owns it, so a cached image is never shown against the switch that
 * turned it off, and re-reading stays a pure memory lookup for the initial frame.
 */
object ModuleBannerCache {

    /** Which switch owns a resolved banner. */
    enum class Origin { Api, UserPick, Module }

    /** A banner as a card needs it: bytes for a local image, or a URL Coil fetches itself. */
    data class Entry(
        val origin: Origin,
        val bytes: ByteArray? = null,
        val url: String? = null,
        /** Only meaningful for [Origin.Module], whose source file can change. */
        val reloadKey: Int = -1,
    ) {
        val hasImage: Boolean get() = bytes != null || !url.isNullOrEmpty()

        /** Whether [policy] still allows showing this entry. */
        fun allowedBy(policy: BannerPolicy): Boolean = hasImage && when {
            !policy.active -> false
            origin == Origin.Api -> policy.apiActive
            origin == Origin.UserPick -> policy.localActive
            else -> true
        }
    }

    /** Bounds how many cards hit the root shell at the same time. */
    val resolvePermits = Semaphore(4)

    private val entries = mutableStateMapOf<String, Entry>()

    /** The last banner resolved for this card, without touching the filesystem. */
    fun entry(slot: ModuleBannerSlot, moduleId: String): Entry? = entries[key(slot, moduleId)]

    /**
     * The cached answer for what the module itself ships, as read under [reloadKey]. An entry without
     * an image is still an answer: it records that the module ships no banner of its own.
     */
    fun moduleEntry(slot: ModuleBannerSlot, moduleId: String, reloadKey: Int): Entry? =
        entry(slot, moduleId)?.takeIf { it.origin == Origin.Module && it.reloadKey == reloadKey }

    fun put(slot: ModuleBannerSlot, moduleId: String, entry: Entry) {
        entries[key(slot, moduleId)] = entry
    }

    fun remove(slot: ModuleBannerSlot, moduleId: String) {
        entries.remove(key(slot, moduleId))
    }

    /** Drops entries of modules that are no longer installed. */
    fun prune(slot: ModuleBannerSlot, validIds: Set<String>) {
        val prefix = "${slot.key}:"
        entries.keys
            .filter { it.startsWith(prefix) && it.removePrefix(prefix) !in validIds }
            .forEach { entries.remove(it) }
    }

    private fun key(slot: ModuleBannerSlot, moduleId: String) = "${slot.key}:$moduleId"
}
