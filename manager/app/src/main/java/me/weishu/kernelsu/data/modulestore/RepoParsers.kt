package me.weishu.kernelsu.data.modulestore

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Repository used when the cluster index is unavailable or empty. */
const val DEFAULT_REPOSITORY_URL = "https://gr.dergoogler.com/gmr/"

/** Built-in fallback repository entry. */
val DEFAULT_REPOSITORY = StoreRepository(
    name = "GMR",
    url = DEFAULT_REPOSITORY_URL,
)

/** Base URL of the built-in KernelSU module registry. */
const val KERNELSU_REGISTRY_URL = "https://modules.kernelsu.org/"

/** Registry index that advertises the module list document. */
const val KERNELSU_INDEX_PATH = "api/v1/index.json"

/** Module list document used when the registry index cannot be read. */
const val KERNELSU_MODULES_PATH = "api/v1/modules.json"

/** Directory holding one detail document per module. */
const val KERNELSU_MODULE_DETAIL_PREFIX = "api/v1/modules/"

/** Returns `true` when the receiver is an absolute http(s) URL. */
internal fun String.isHttpUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

/**
 * Resolves the module list endpoint for a repository base.
 *
 * A base that already points at a `.json` document is used verbatim; any other base gets the
 * standard `<base>/json/modules.json` suffix.
 */
fun resolveRepoModulesUrl(repoBase: String): String {
    val base = repoBase.trim()
    return if (base.endsWith(".json", ignoreCase = true)) {
        base
    } else {
        base.trimEnd('/') + "/json/modules.json"
    }
}

/** Normalises a repository base to an absolute directory URL ending in `/`. */
fun resolveRepoBaseUrl(repoBase: String): String {
    val base = repoBase.trim()
    val directory = if (base.endsWith(".json", ignoreCase = true)) {
        base.substringBeforeLast('/')
    } else {
        base
    }
    return directory.trimEnd('/') + "/"
}

/** Resolves a module download URL, leaving absolute URLs untouched. */
fun resolveDownloadUrl(repoBase: String, zipUrl: String): String {
    val raw = zipUrl.trim()
    if (raw.isHttpUrl()) return raw
    return resolveRepoBaseUrl(repoBase) + raw.trimStart('/')
}

/**
 * Parses the official index (a top-level JSON array) into [StoreModule]s.
 *
 * Entries whose `url` is not http(s) are dropped. The index has no version history, so each module
 * carries a single synthesised release.
 */
fun parseOfficialModules(json: String, language: String): List<StoreModule> {
    val array = JSONArray(json)
    val modules = ArrayList<StoreModule>(array.length())
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val url = item.optString("url", "").trim()
        if (!url.isHttpUrl()) continue
        val name = item.optString("name", "").trim()
        if (name.isEmpty()) continue
        val version = item.optString("version", "")
        modules += StoreModule(
            id = name,
            name = name,
            version = version,
            versionCode = 0L,
            author = "",
            description = selectDescription(item, language),
            releases = listOf(StoreRelease(version = version, versionCode = 0L, downloadUrl = url)),
            parameter = if (item.has("parameter")) item.optInt("parameter", 0) == 1 else null,
        )
    }
    return modules
}

/**
 * Resolves the module list document path advertised by the registry index.
 *
 */
fun parseKernelSuModulesPath(json: String): String =
    registryDocument(json).getString("modules").also { require(isRegistryPath(it)) }

internal fun registryDocument(json: String): JSONObject = JSONObject(json).also {
    require(it.opt("schemaVersion") == 1) { "Unsupported registry schema" }
    require(Regex("^[0-9a-f]{16,64}$").matches(it.getString("buildId"))) { "Invalid registry build" }
}

internal fun isRegistryPath(path: String): Boolean =
    path.startsWith("api/v1/") && path.endsWith(".json") &&
        path.split('/').all { Regex("^[A-Za-z0-9._-]+$").matches(it) && it != "." && it != ".." }

private fun isRegistryModule(item: JSONObject): Boolean =
    item.optString("kind", "module") == "module" &&
        item.optString("id").length in 2..64 &&
        Regex("^[a-zA-Z][a-zA-Z0-9._-]+$").matches(item.optString("id"))

/**
 * Parses the KernelSU registry module list into [StoreModule]s.
 *
 * The list document carries only catalogue metadata, so every entry is returned without releases;
 * the per-module detail document holds the downloadable versions.
 */
fun parseKernelSuModules(json: String): List<StoreModule> {
    val array = registryDocument(json).getJSONArray("modules")
    val modules = ArrayList<StoreModule>(array.length())
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val id = item.optString("id", "").trim()
        if (!isRegistryModule(item) || !isRegistryPath(item.optString("detail"))) continue
        val stable = item.optJSONObject("stable")
        if (stable == null || stable.optLong("versionCode") < 1 || modules.any { it.id.equals(id, true) }) continue
        modules += StoreModule(
            id = id,
            name = item.optString("name", "").trim().ifEmpty { id },
            version = stable.optString("version", ""),
            versionCode = stable.optLong("versionCode", 0L),
            author = kernelSuAuthors(item),
            description = item.optString("summary", "").trim(),
            detailPath = item.getString("detail"),
        )
    }
    return modules
}

/** Parses a KernelSU module detail document, returning `null` when it carries no id. */
fun parseKernelSuDetail(json: String): StoreModule? {
    val item = registryDocument(json)
    val id = item.optString("id", "").trim()
    if (!isRegistryModule(item)) return null
    val stable = item.optJSONObject("stable")
    val releases = parseKernelSuReleases(item.optJSONArray("releases"))
    return StoreModule(
        id = id,
        name = item.optString("name", "").trim().ifEmpty { id },
        version = stable?.optString("version", "").orEmpty().ifEmpty { releases.firstOrNull()?.version.orEmpty() },
        versionCode = stable?.optLong("versionCode", 0L) ?: 0L,
        author = kernelSuAuthors(item),
        description = item.optString("summary", "").trim(),
        license = item.optString("license", "").trim().ifEmpty { null },
        homepage = item.optString("homepage", "").trim().ifEmpty { null },
        source = item.optJSONObject("source")?.optString("url", "")?.trim()?.ifEmpty { null },
        support = item.optString("support", "").trim().ifEmpty { null },
        releases = releases,
    )
}

private fun parseKernelSuReleases(array: JSONArray?): List<StoreRelease> {
    if (array == null) return emptyList()
    val releases = ArrayList<StoreRelease>(array.length())
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val zip = item.optJSONObject("zip") ?: continue
        val url = zip.optString("url", "").trim()
        val size = zip.optLong("size", 0)
        val hash = zip.optString("sha256", "")
        val parsedUrl = url.toHttpUrlOrNull()
        if (parsedUrl == null || parsedUrl.scheme != "https" || parsedUrl.host != "github.com" ||
            parsedUrl.username.isNotEmpty() || parsedUrl.password.isNotEmpty() || parsedUrl.port != 443 ||
            !Regex("^/[^/]+/[^/]+/releases/download/[^/]+/[^/]+$").matches(parsedUrl.encodedPath) ||
            size <= 0 || !Regex("^[0-9a-f]{64}$").matches(hash) || item.opt("immutable") != true ||
            item.optLong("versionCode") < 1
        ) continue
        releases += StoreRelease(
            version = item.optString("version", "").trim(),
            versionCode = item.optLong("versionCode", 0L),
            downloadUrl = url,
            changelog = stripHtml(item.optString("changelogHtml", "")),
            timestamp = parseIsoSeconds(item.optString("publishedAt", "")),
            expectedSize = size,
            expectedSha256 = hash,
        )
    }
    return releases
}

/** Joins the registry's structured author list into a single display string. */
private fun kernelSuAuthors(item: JSONObject): String {
    val array = item.optJSONArray("authors") ?: return ""
    return (0 until array.length())
        .mapNotNull { array.optJSONObject(it)?.optString("name", "")?.trim()?.takeIf { name -> name.isNotEmpty() } }
        .joinToString(", ")
}

/** Converts an ISO-8601 instant into epoch seconds, which is what the store date formatter expects. */
private fun parseIsoSeconds(value: String): Double =
    if (value.isBlank()) 0.0 else runCatching { Instant.parse(value).epochSecond.toDouble() }.getOrDefault(0.0)

/** Reduces an HTML changelog to plain text. */
private fun stripHtml(html: String): String =
    html.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()

/** Parses the cluster index (a top-level JSON array) into [StoreRepository]s. */
fun parseRepositories(json: String): List<StoreRepository> {
    val array = JSONArray(json)
    val repositories = ArrayList<StoreRepository>(array.length())
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val url = item.optString("url", "").trim()
        if (url.isEmpty()) continue
        repositories += StoreRepository(
            name = item.optString("name", ""),
            url = url,
            description = item.optString("description", ""),
            modulesCount = item.optInt("modules_count", 0),
            cover = item.optString("cover", ""),
        )
    }
    return repositories
}

/**
 * Prepends [DEFAULT_REPOSITORY] when it is missing. The fetched list itself is never de-duplicated.
 */
fun withDefaultRepository(repositories: List<StoreRepository>): List<StoreRepository> =
    if (repositories.any { it.url == DEFAULT_REPOSITORY_URL }) {
        repositories
    } else {
        listOf(DEFAULT_REPOSITORY) + repositories
    }

/** Parses a Magisk-format module list document and resolves relative download URLs. */
fun parseMagiskModules(json: String, repoBase: String): List<StoreModule> {
    val root = JSONObject(json)
    val array = root.optJSONArray("modules") ?: return emptyList()
    val modules = ArrayList<StoreModule>(array.length())
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val name = item.optString("name", "").trim()
        val id = item.optString("id", "").trim().ifEmpty { name }
        if (id.isEmpty()) continue
        val track = item.optJSONObject("track")
        val releases = parseMagiskReleases(item.optJSONArray("versions"), repoBase)
        val versionCode = item.optLong("versionCode", 0L)
        val latest = releases.firstOrNull { it.versionCode == versionCode }
            ?: releases.maxByOrNull { it.versionCode }
        modules += StoreModule(
            id = id,
            name = name.ifEmpty { id },
            version = item.optString("version", "").ifEmpty { latest?.version.orEmpty() },
            versionCode = versionCode,
            author = item.optString("author", ""),
            description = item.optString("description", ""),
            license = trackValue(track, item, "license"),
            homepage = trackValue(track, item, "homepage"),
            source = trackValue(track, item, "source"),
            support = trackValue(track, item, "support"),
            releases = releases,
        )
    }
    return modules
}

private fun parseMagiskReleases(versions: JSONArray?, repoBase: String): List<StoreRelease> {
    if (versions == null) return emptyList()
    val releases = ArrayList<StoreRelease>(versions.length())
    for (index in 0 until versions.length()) {
        val version = versions.optJSONObject(index) ?: continue
        val zipUrl = version.optString("zipUrl", "").trim()
        if (zipUrl.isEmpty()) continue
        releases += StoreRelease(
            version = version.optString("version", ""),
            versionCode = version.optLong("versionCode", 0L),
            downloadUrl = resolveDownloadUrl(repoBase, zipUrl),
            changelog = version.optString("changelog", ""),
            timestamp = version.optDouble("timestamp", 0.0),
        )
    }
    return releases
}

/** Prefers a non-blank value from the nested `track` object over the module-level fallback. */
private fun trackValue(track: JSONObject?, module: JSONObject, key: String): String? {
    val tracked = track?.optString(key, "")?.takeIf { it.isNotBlank() }
    if (tracked != null) return tracked
    return module.optString(key, "").takeIf { it.isNotBlank() }
}

/** Selects the description for [language], preferring `description_en` for non-Chinese locales. */
internal fun selectDescription(item: JSONObject, language: String): String {
    val localized = item.optString("description", "")
    val english = item.optString("description_en", "")
    return if (language == "zh") {
        localized.ifBlank { english }
    } else {
        english.ifBlank { localized }
    }
}
