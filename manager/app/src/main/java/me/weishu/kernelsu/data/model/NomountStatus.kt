package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Driver state reported by `ksud nomount status --json`.
 *
 * The backend distinguishes three states on purpose: a module that loaded but
 * does not answer must never be reported as ready.
 */
enum class NomountDriverState(val token: String) {
    ABSENT("absent"),
    OUTDATED("outdated"),
    READY("ready");

    companion object {
        fun fromToken(token: String?): NomountDriverState? =
            entries.firstOrNull { it.token == token }
    }
}

/** Decoded `ksud nomount status --json` payload (`schema_version = 1`). */
@Immutable
data class NomountStatus(
    val driverState: NomountDriverState,
    val driverVersion: String?,
    val apiOk: Boolean,
    val rulesExpected: Int,
    val rulesApplied: Int,
    val uidCount: Int,
    val isolatedBlocked: Boolean,
    val error: String?,
) {
    /** True when the driver answers and the requested rules are all in place. */
    val injected: Boolean
        get() = apiOk && rulesApplied >= rulesExpected && rulesExpected > 0

    companion object {
        const val SCHEMA_VERSION = 1

        /**
         * @throws IllegalStateException when the payload is not a NoMount status
         * this app understands; callers treat that as a read failure rather than
         * inventing a state.
         */
        fun fromJson(json: JSONObject): NomountStatus {
            check(json.optInt("schema_version", -1) == SCHEMA_VERSION) {
                "unsupported nomount schema_version ${json.optInt("schema_version", -1)}"
            }
            val state = NomountDriverState.fromToken(json.optStringOrNull("driver_state"))
                ?: error("nomount status has no valid driver_state")
            return NomountStatus(
                driverState = state,
                driverVersion = json.optStringOrNull("driver_version"),
                apiOk = json.optBoolean("api_ok", false),
                rulesExpected = json.optInt("rules_expected", 0),
                rulesApplied = json.optInt("rules_applied", 0),
                uidCount = json.optInt("uid_count", 0),
                isolatedBlocked = json.optBoolean("isolated_blocked", false),
                error = json.optStringOrNull("error"),
            )
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }
