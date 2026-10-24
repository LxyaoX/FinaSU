package me.weishu.kernelsu.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.model.Module
import me.weishu.kernelsu.data.model.ModuleUpdateInfo
import me.weishu.kernelsu.data.model.SuiStatus
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.isNetworkAvailable
import me.weishu.kernelsu.ui.util.listModules
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import me.weishu.kernelsu.data.modulestore.KernelSuStoreSource

class ModuleRepositoryImpl : ModuleRepository {

    companion object {
    }

    override suspend fun getModules(): Result<List<Module>> = withContext(Dispatchers.IO) {
        runCatching {
            val result = listModules()
            val array = JSONArray(result)
            (0 until array.length())
                .asSequence()
                .map { array.getJSONObject(it) }
                .map { obj ->
                    Module(
                        id = obj.getString("id"),
                        name = obj.optString("name"),
                        author = obj.optString("author", "Unknown"),
                        version = obj.optString("version", "Unknown"),
                        versionCode = obj.optInt("versionCode", 0),
                        description = obj.optString("description"),
                        enabled = obj.getBoolean("enabled"),
                        update = obj.optBoolean("update"),
                        remove = obj.getBoolean("remove"),
                        updateJson = obj.optString("updateJson"),
                        hasWebUi = obj.optBoolean("web"),
                        hasActionScript = obj.optBoolean("action"),
                        zygiskRequired = obj.optBoolean("zygiskRequired"),
                        metamodule = (obj.optInt("metamodule") != 0) || obj.optBoolean("metamodule"),
                        actionIconPath = obj.optString("actionIcon").takeIf { it.isNotBlank() },
                        webUiIconPath = obj.optString("webuiIcon").takeIf { it.isNotBlank() },
                        preinit = obj.optString("preinit"),
                    )
                }
                // The Sui payload is a module on disk but is owned by the settings entry.
                .filterNot { it.id == SuiStatus.MODULE_ID }
                .toList()
        }
    }

    override suspend fun checkUpdate(module: Module): Result<ModuleUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isNetworkAvailable(ksuApp)) {
                return@runCatching ModuleUpdateInfo.Empty
            }
            if (module.updateJson.isEmpty() || module.remove || module.update || !module.enabled) {
                return@runCatching ModuleUpdateInfo.Empty
            }

            val url = module.updateJson
            if (url.toHttpUrlOrNull()?.host == "modules.kernelsu.org") {
                val detail = KernelSuStoreSource().detail(module.id).getOrThrow()
                val release = detail.latestRelease
                return@runCatching if (release != null && release.versionCode > module.versionCode) {
                    ModuleUpdateInfo(release.downloadUrl, sanitizeVersionString(release.version), "", release.expectedSize, release.expectedSha256)
                } else ModuleUpdateInfo.Empty
            }
            val response = ksuApp.okhttpClient.newCall(
                Request.Builder().url(url).build()
            ).execute()

            val result = response.use {
                if (it.isSuccessful) it.body.string() else ""
            }

            if (result.isEmpty()) {
                return@runCatching ModuleUpdateInfo.Empty
            }

            val updateJson = JSONObject(result)
            var version = updateJson.optString("version", "")
            version = sanitizeVersionString(version)
            val versionCode = updateJson.optInt("versionCode", 0)
            val zipUrl = updateJson.optString("zipUrl", "")
            val changelog = updateJson.optString("changelog", "")

            if (versionCode <= module.versionCode || zipUrl.isEmpty()) {
                ModuleUpdateInfo.Empty
            } else {
                ModuleUpdateInfo(zipUrl, version, changelog)
            }
        }
    }
}

/** Keeps only characters that are safe in a version token used for display and file names. */
private fun sanitizeVersionString(version: String): String =
    version.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
