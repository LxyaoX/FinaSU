package me.weishu.kernelsu.data.model

import org.json.JSONObject

/** One `zygiskd` process the engine is running, per ABI. */
data class YukiZygiskDaemon(
    val abi: Int,
    val pid: Int,
)

/** The settings document the engine reads at startup (`yzconfig.json`). */
data class YukiZygiskConfig(
    val yukilinker: Boolean = true,
    val anonymousMemory: Boolean = true,
    val earlyLoad: Boolean = false,
    val dmesgLog: Boolean = false,
    val crashProtection: Boolean = true,
    val denylistMode: Int = 0,
)

/**
 * What `ksud yukizygisk status` reports: whether the payload is installed, which daemons are alive,
 * and the current settings.
 */
data class YukiZygiskStatus(
    val installed: Boolean,
    val daemons: List<YukiZygiskDaemon>,
    val config: YukiZygiskConfig,
) {
    val running: Boolean get() = daemons.isNotEmpty()

    companion object {
        /** Parse the JSON document, or null when ksud printed something that is not one. */
        fun fromJson(text: String): YukiZygiskStatus? = runCatching {
            val root = JSONObject(text)
            val config = root.optJSONObject("config").orEmpty()
            val daemons = buildList {
                val array = root.optJSONArray("daemons") ?: return@buildList
                for (index in 0 until array.length()) {
                    val entry = array.optJSONObject(index) ?: continue
                    add(
                        YukiZygiskDaemon(
                            abi = entry.optInt("abi", 0),
                            pid = entry.optInt("pid", 0),
                        )
                    )
                }
            }
            YukiZygiskStatus(
                installed = root.optBoolean("installed", false),
                daemons = daemons,
                config = YukiZygiskConfig(
                    yukilinker = config.optBoolean("yukilinker", true),
                    anonymousMemory = config.optBoolean("anonymous_memory", true),
                    earlyLoad = config.optBoolean("early_load", false),
                    dmesgLog = config.optBoolean("dmesg_log", false),
                    crashProtection = config.optBoolean("crash_protection", true),
                    denylistMode = config.optInt("denylist_mode", 0),
                ),
            )
        }.getOrNull()
    }
}

private fun JSONObject?.orEmpty(): JSONObject = this ?: JSONObject()
