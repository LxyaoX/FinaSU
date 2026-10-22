package me.weishu.kernelsu.data.teesim

/**
 * Cheap structural check for a keybox the user picked in the manager.
 *
 * The daemon is the authority on what it can sign with, so this only rejects files that cannot be
 * a keybox at all — a false rejection here blocks a file the module would happily accept. Two
 * shapes are in circulation and both are valid: upstream's own WebUI accepts either.
 *
 *     <AndroidAttestation><Keybox DeviceID="...">...</Keybox></AndroidAttestation>
 *     <Keybox DeviceID="...">...</Keybox>
 *
 * The algorithm spelling also varies between published keyboxes ("ecdsa", "EC", "ec"), so the key
 * blocks are only required to declare *an* algorithm; whether the chains are usable is reported by
 * the daemon.
 */
object TeesimKeybox {

    /** `<Key ... algorithm=...>` at the start of a block; `<Keybox` does not match. */
    private val KEY_BLOCK = Regex("""<Key\b[^>]*\balgorithm\s*=""", RegexOption.IGNORE_CASE)

    fun isUsable(text: String): Boolean {
        if (!text.contains("<AndroidAttestation") && !text.contains("<Keybox")) return false
        if (!KEY_BLOCK.containsMatchIn(text)) return false
        return text.contains("CERTIFICATE")
    }
}
