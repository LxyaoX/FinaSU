package me.weishu.kernelsu.data.backup

/** Secret-store key under which the WebDAV password is kept. */
const val WEBDAV_PASSWORD_SECRET_KEY = "webdav_password"

/**
 * Small key/value store for secrets that must be read back later (for example a WebDAV password).
 *
 * Values here are reversible: callers need the plaintext at request time, so implementations must
 * encrypt at rest rather than hashing.
 */
interface SecretStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)

    fun has(key: String): Boolean = get(key) != null
}
