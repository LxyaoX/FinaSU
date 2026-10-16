package me.weishu.kernelsu.data.modulestore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * A repository client for the KernelSU module registry.
 *
 * The registry publishes its catalogue at [KERNELSU_MODULES_PATH], but the downloadable releases
 * only live in a per-module detail document. The list is therefore enriched with each module's
 * detail so callers see the releases without a second round trip; a module whose detail cannot be
 * read keeps the catalogue metadata and is simply offered without a download.
 */
class KernelSuStoreSource(
    private val baseUrl: String = KERNELSU_REGISTRY_URL,
    private val http: RepoHttp = RepoHttp,
) : ModuleStoreSource {

    override suspend fun list(query: String?): Result<List<StoreModule>> = withContext(Dispatchers.IO) {
        val base = normalizeBase(baseUrl)
            ?: return@withContext Result.failure(RepoHttpException("invalid registry url: $baseUrl"))
        http.get(base + modulesPath(base))
            .mapCatching { body -> enrich(base, parseKernelSuModules(body)) }
            .map { modules -> modules.filterByQuery(query) }
    }

    override suspend fun detail(moduleId: String): Result<StoreModule> = withContext(Dispatchers.IO) {
        val base = normalizeBase(baseUrl)
            ?: return@withContext Result.failure(RepoHttpException("invalid registry url: $baseUrl"))
        http.get(base + KERNELSU_MODULE_DETAIL_PREFIX + moduleId + ".json")
            .mapCatching { body ->
                parseKernelSuDetail(body) ?: throw RepoHttpException("module not found: $moduleId")
            }
    }

    /** Reads the module list path from the registry index, falling back to the fixed path. */
    private suspend fun modulesPath(base: String): String =
        http.get(base + KERNELSU_INDEX_PATH)
            .mapCatching { parseKernelSuModulesPath(it) }
            .getOrDefault(KERNELSU_MODULES_PATH)

    /** Replaces every module with its detail document, keeping the catalogue entry on failure. */
    private suspend fun enrich(base: String, modules: List<StoreModule>): List<StoreModule> =
        coroutineScope {
            modules.map { module ->
                async {
                    http.get(base + KERNELSU_MODULE_DETAIL_PREFIX + module.id + ".json")
                        .mapCatching { parseKernelSuDetail(it) }
                        .getOrNull() ?: module
                }
            }.awaitAll()
        }

    private fun normalizeBase(raw: String): String? {
        val trimmed = raw.trim()
        if (!trimmed.isHttpUrl()) return null
        return trimmed.trimEnd('/') + "/"
    }
}
