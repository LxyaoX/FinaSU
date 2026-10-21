package me.weishu.kernelsu.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.model.KernelModule
import me.weishu.kernelsu.data.model.KernelModuleCollection
import me.weishu.kernelsu.data.model.KernelModuleLastResult
import me.weishu.kernelsu.data.model.KernelModuleParameter
import me.weishu.kernelsu.data.model.LoadState
import me.weishu.kernelsu.ui.util.execKsudResult
import org.json.JSONArray
import org.json.JSONObject

class KernelModuleRepositoryImpl : KernelModuleRepository {
    override suspend fun getModules(): Result<KernelModuleCollection> = withContext(Dispatchers.IO) {
        runCatching {
            parseKernelModuleResponse(runJson("kmod list"))
        }
    }

    override suspend fun install(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { runJson("kmod install ${quote(path)}") }.map { Unit }
    }

    override suspend fun remove(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { runJson("kmod remove ${quote(id)}") }.map { Unit }
    }

    override suspend fun enable(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { runJson("kmod enable ${quote(id)}") }.map { Unit }
    }

    override suspend fun disable(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { runJson("kmod disable ${quote(id)}") }.map { Unit }
    }

    override suspend fun setAutoload(id: String, enabled: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                runJson("kmod autoload ${quote(id)} ${if (enabled) "on" else "off"}")
            }.map { Unit }
        }

    override suspend fun setParams(id: String, params: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                runJson("kmod params ${quote(id)} ${quote(params)}")
            }.map { Unit }
        }

    override suspend fun load(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { runJson("kmod load ${quote(id)}") }.map { Unit }
    }

    override suspend fun getLog(id: String): Result<KernelModuleLastResult?> =
        withContext(Dispatchers.IO) {
            runCatching {
                parseKernelModuleLog(runJson("kmod log ${quote(id)}"))
            }
        }

    private suspend fun runJson(arguments: String): String {
        val result = execKsudResult(arguments)
        if (!result.isSuccess) {
            val jsonError = result.out
                .asReversed()
                .firstOrNull { it.trimStart().startsWith('{') }
                ?.let { line -> runCatching { JSONObject(line).optString("error") }.getOrNull() }
                ?.takeIf { it.isNotBlank() }
            val reason = jsonError ?: result.err.joinToString("\n").trim()
            throw KernelModuleCommandException(
                result.code,
                reason.ifBlank { "kernel module command failed with code ${result.code}" },
            )
        }
        return result.out
            .asReversed()
            .firstOrNull { it.trimStart().startsWith('{') }
            ?.trim()
            ?: error("ksud returned no JSON for $arguments")
    }
}

private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

internal fun parseKernelModuleList(raw: String): List<KernelModule> =
    JSONObject(raw).getJSONArray("modules").toKernelModules()

internal fun parseKernelModuleResponse(raw: String): KernelModuleCollection {
    val output = JSONObject(raw)
    return KernelModuleCollection(
        modules = output.getJSONArray("modules").toKernelModules(),
        safeMode = output.optBoolean("safe_mode"),
    )
}

internal fun parseKernelModuleLog(raw: String): KernelModuleLastResult? {
    val output = JSONObject(raw)
    return if (output.isNull("last_result")) {
        null
    } else {
        output.getJSONObject("last_result").toLastResult()
    }
}

private fun JSONArray.toKernelModules(): List<KernelModule> =
    (0 until length()).map { index -> getJSONObject(index).toKernelModule() }

private fun JSONObject.toKernelModule(): KernelModule = KernelModule(
    id = getString("id"),
    internalName = optString("internal_name").takeIf { it.isNotBlank() },
    displayName = optString("display_name"),
    version = optString("version"),
    authors = optJSONArray("authors").toStringList(),
    description = optString("description"),
    license = optString("license"),
    vermagic = optString("vermagic"),
    architecture = optString("architecture"),
    depends = optJSONArray("depends").toStringList(),
    parameters = optJSONArray("parameters").toParameters(),
    diagnostics = optJSONArray("diagnostics").toStringList(),
    fileSize = optLong("file_size"),
    sha256 = optString("sha256"),
    autoload = optBoolean("autoload"),
    disabled = optBoolean("disabled"),
    effectiveAutoload = optBoolean("effective_autoload"),
    loaded = when (optString("loaded")) {
        "loaded" -> LoadState.LOADED
        "not_loaded" -> LoadState.NOT_LOADED
        else -> LoadState.UNKNOWN
    },
    stateError = optString("state_error").takeIf { it.isNotBlank() },
    params = optString("params"),
    loadable = optBoolean("loadable"),
    protected = optBoolean("protected"),
    error = optString("error").takeIf { it.isNotBlank() },
)

private fun JSONObject.toLastResult(): KernelModuleLastResult = KernelModuleLastResult(
    bootId = optString("boot_id"),
    operation = optString("operation"),
    outcome = optString("outcome"),
    message = optString("message"),
)

private fun JSONArray?.toParameters(): List<KernelModuleParameter> {
    if (this == null) return emptyList()
    return (0 until length()).map { index ->
        val parameter = getJSONObject(index)
        KernelModuleParameter(
            name = parameter.optString("name"),
            valueType = parameter.optString("value_type").takeIf { it.isNotBlank() },
        )
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { index -> optString(index) }
}
