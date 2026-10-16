package me.weishu.kernelsu.data.modulestore

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ksuApp
import org.json.JSONObject

class KernelSuStoreSource(
    private val baseUrl: String = KERNELSU_REGISTRY_URL,
    private val http: RepoHttp = RepoHttp,
) : ModuleStoreSource {
    companion object {
        private val mutex = Mutex()
    }
    private var snapshot: JSONObject? = null

    private fun base(): String {
        require(baseUrl.startsWith("https://")) { "Invalid registry URL" }
        return baseUrl.trimEnd('/') + "/"
    }

    private fun cacheFile() = File(ksuApp.filesDir, "module_registry/${baseUrl.hashCode()}.json")

    private suspend fun catalogue(refresh: Boolean = false): JSONObject = mutex.withLock {
        try {
            repeat(2) { attempt ->
                val indexText = http.get(base() + KERNELSU_INDEX_PATH, refresh = refresh || attempt > 0).getOrThrow()
                val index = registryDocument(indexText)
                val listText = http.get(base() + parseKernelSuModulesPath(indexText), refresh = refresh || attempt > 0).getOrThrow()
                val list = registryDocument(listText)
                parseKernelSuModules(listText)
                if (index.getString("buildId") == list.getString("buildId")) {
                    snapshot = list
                    runCatching {
                        val file = cacheFile()
                        file.parentFile?.mkdirs()
                        val temporary = File(file.parentFile, "${file.name}.tmp")
                        temporary.writeText(listText)
                        check(temporary.renameTo(file))
                    }
                    return@withLock list
                }
            }
            error("Registry build mismatch")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            snapshot ?: runCatching {
                val text = cacheFile().readText()
                parseKernelSuModules(text)
                registryDocument(text)
            }.getOrNull()?.also { snapshot = it } ?: throw error
        }
    }

    override suspend fun list(query: String?): Result<List<StoreModule>> = withContext(Dispatchers.IO) {
        try {
            Result.success(parseKernelSuModules(catalogue().toString()).filterByQuery(query))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun detail(moduleId: String): Result<StoreModule> = withContext(Dispatchers.IO) {
        try {
            repeat(2) { attempt ->
                val list = catalogue(refresh = attempt > 0)
                val summary = parseKernelSuModules(list.toString()).firstOrNull { it.id == moduleId }
                    ?: error("Module not in registry: $moduleId")
                val text = http.get(base() + requireNotNull(summary.detailPath), refresh = attempt > 0).getOrThrow()
                val document = registryDocument(text)
                val module = parseKernelSuDetail(text) ?: error("Unsupported registry entry")
                require(module.id == moduleId) { "Registry detail ID mismatch" }
                if (document.getString("buildId") == list.getString("buildId")) {
                    return@withContext Result.success(module)
                }
            }
            error("Registry detail build mismatch")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
