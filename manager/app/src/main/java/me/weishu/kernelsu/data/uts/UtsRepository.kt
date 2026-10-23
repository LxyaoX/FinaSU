package me.weishu.kernelsu.data.uts

import android.content.pm.PackageInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.RootShell
import me.weishu.kernelsu.ui.viewmodel.SuperUserViewModel
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UtsFields(
    val sysname: String = "",
    val nodename: String = "",
    val release: String = "",
    val version: String = "",
    val machine: String = "",
    val domainname: String = "",
    val mask: Int = 0,
) {
    fun fieldMask(): Int = mask

    fun withField(index: Int, value: String): UtsFields {
        val bit = 1 shl index
        val nextMask = if (value.isNotEmpty()) mask or bit else mask and bit.inv()
        return when (index) {
            0 -> copy(sysname = value, mask = nextMask)
            1 -> copy(nodename = value, mask = nextMask)
            2 -> copy(release = value, mask = nextMask)
            3 -> copy(version = value, mask = nextMask)
            4 -> copy(machine = value, mask = nextMask)
            else -> copy(domainname = value, mask = nextMask)
        }
    }
}

enum class UtsViewPhase {
    Unsupported,
    Error,
    Blocked,
    Observed,
    Enabled,
    Configured,
    NotConfigured,
    ;

    companion object {
        fun fromWire(value: String): UtsViewPhase = when (value) {
            "unsupported" -> Unsupported
            "error" -> Error
            "blocked" -> Blocked
            "observed" -> Observed
            "enabled" -> Enabled
            "configured" -> Configured
            else -> NotConfigured
        }
    }
}

enum class UtsViewMode {
    Selected,
    Global,
    ;

    companion object {
        fun fromWire(value: String): UtsViewMode =
            if (value == "global") Global else Selected
    }
}

data class UtsTarget(
    val packageName: String,
    val uid: Int,
)

data class UtsTargetApp(
    val label: String,
    val packageName: String,
    val uid: Int,
    val isSystem: Boolean,
    val packageInfo: PackageInfo?,
    val missing: Boolean = false,
)

data class UtsViewObservation(
    val generation: Int = 0,
    val matched: Int = 0,
    val discarded: Int = 0,
    val lastPid: Int = 0,
    val lastUid: Int = 0,
    val skipNotApp: Int = 0,
    val skipIsolated: Int = 0,
    val skipNotTarget: Int = 0,
    val skipNotZygote: Int = 0,
    val skipPrivateNamespace: Int = 0,
    val unshareFailed: Int = 0,
    val mergeMissingNamespace: Int = 0,
)

data class UtsViewStatus(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val active: Boolean = false,
    val mode: UtsViewMode = UtsViewMode.Selected,
    val fieldMask: Int = 0,
    val fields: UtsFields = UtsFields(),
    val targets: List<UtsTarget> = emptyList(),
    val source: String = "none",
    val unameApplied: Boolean = false,
    val unameScheduled: Boolean = false,
    val unameUncertain: Boolean = false,
    val phase: UtsViewPhase = UtsViewPhase.NotConfigured,
    val configurationPresent: Boolean = false,
    val observation: UtsViewObservation = UtsViewObservation(),
    val configError: String? = null,
    val kernelError: String? = null,
    val providerError: String? = null,
    val runtimeError: String? = null,
) {
    val provider: String
        get() = when {
            active -> "uts"
            source == "unknown" -> "unknown"
            source == "susfs" -> "susfs"
            unameApplied -> "susfs"
            else -> "none"
        }

    val hasPersistedConfig: Boolean
        get() = configurationPresent || fieldMask != 0 || configError != null || active

    companion object {
        fun parse(text: String): UtsViewStatus {
            val json = JSONObject(text)
            val fieldsJson = json.getJSONObject("fields")
            val providerJson = json.getJSONObject("provider")
            val observationJson = json.optJSONObject("observation")
            val configurationJson = json.optJSONObject("configuration")
            val fieldMask = json.getInt("field_mask")
            val supported = json.getBoolean("supported")
            val enabled = json.getBoolean("enabled")
            val active = json.getBoolean("active")
            val mode = UtsViewMode.fromWire(json.optString("mode", "selected"))
            val targets = json.optJSONArray("targets")?.let { array ->
                List(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    UtsTarget(
                        packageName = item.getString("package"),
                        uid = item.getInt("uid"),
                    )
                }
            }.orEmpty()
            val unameApplied = providerJson.optBoolean("uname_applied")
            val unameScheduled = providerJson.optBoolean("uname_scheduled")
            val unameUncertain = providerJson.optBoolean("uname_uncertain")
            val configError =
                if (json.isNull("config_error")) null else json.getString("config_error")
            val kernelError =
                if (json.isNull("kernel_error")) {
                    null
                } else {
                    json.optString("kernel_error").ifEmpty { null }
                }
            val providerError =
                if (json.isNull("provider_error")) {
                    null
                } else {
                    json.optString("provider_error").ifEmpty { null }
                }
            val runtimeError =
                if (json.isNull("runtime_error")) {
                    null
                } else {
                    json.optString("runtime_error").ifEmpty { null }
                }
            val phase = if (json.has("state")) {
                UtsViewPhase.fromWire(json.getString("state"))
            } else {
                when {
                    !supported -> UtsViewPhase.Unsupported
                    configError != null || kernelError != null ||
                        providerError != null || runtimeError != null -> UtsViewPhase.Error
                    enabled && unameApplied ->
                        UtsViewPhase.Blocked
                    active && (observationJson?.optInt("matched", 0) ?: 0) > 0 ->
                        UtsViewPhase.Observed
                    active -> UtsViewPhase.Enabled
                    fieldMask != 0 -> UtsViewPhase.Configured
                    else -> UtsViewPhase.NotConfigured
                }
            }
            return UtsViewStatus(
                supported = supported,
                enabled = enabled,
                active = active,
                mode = mode,
                fieldMask = fieldMask,
                fields = UtsFields(
                    sysname = fieldsJson.optString("sysname"),
                    nodename = fieldsJson.optString("nodename"),
                    release = fieldsJson.optString("release"),
                    version = fieldsJson.optString("version"),
                    machine = fieldsJson.optString("machine"),
                    domainname = fieldsJson.optString("domainname"),
                    mask = fieldMask,
                ),
                targets = targets,
                source = json.optString("source", "none"),
                unameApplied = unameApplied,
                unameScheduled = unameScheduled,
                unameUncertain = unameUncertain,
                phase = phase,
                configurationPresent =
                    configurationJson?.optBoolean("present")
                        ?: (fieldMask != 0 || configError != null),
                observation = UtsViewObservation(
                    generation = observationJson?.optInt("generation") ?: 0,
                    matched = observationJson?.optInt("matched") ?: 0,
                    discarded = observationJson?.optInt("discarded") ?: 0,
                    lastPid = observationJson?.optInt("last_pid") ?: 0,
                    lastUid = observationJson?.optInt("last_uid") ?: 0,
                    skipNotApp = observationJson?.optInt("skip_not_app") ?: 0,
                    skipIsolated = observationJson?.optInt("skip_isolated") ?: 0,
                    skipNotTarget = observationJson?.optInt("skip_not_target") ?: 0,
                    skipNotZygote = observationJson?.optInt("skip_not_zygote") ?: 0,
                    skipPrivateNamespace = observationJson?.optInt("skip_private_ns") ?: 0,
                    unshareFailed = observationJson?.optInt("unshare_failed") ?: 0,
                    mergeMissingNamespace = observationJson?.optInt("merge_missing_ns") ?: 0,
                ),
                configError = configError,
                kernelError = kernelError,
                providerError = providerError,
                runtimeError = runtimeError,
            )
        }
    }
}

interface UtsDataSource {
    suspend fun status(): UtsViewStatus
    suspend fun loadApps(): List<UtsTargetApp>
    suspend fun save(
        fields: UtsFields,
        enabled: Boolean,
        mode: UtsViewMode,
        targets: List<UtsTarget>,
    )
    suspend fun enable()
    suspend fun disable()
    suspend fun clear()
    suspend fun takeOver(
        fields: UtsFields,
        mode: UtsViewMode,
        targets: List<UtsTarget>,
    )
}

/** Only trusted fixed subcommands; template JSON is passed over stdin. */
class UtsRepository(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : UtsDataSource {
    private suspend fun command(action: String, input: String? = null): String = withContext(dispatcher) {
        require(action in setOf("status", "set", "enable", "disable", "clear", "take-over"))
        require(input == null || input.toByteArray().size <= 64 * 1024)
        val shell = try {
            withTimeout(15_000L) { RootShell.awaitRoot() }
        } catch (error: TimeoutCancellationException) {
            throw IllegalStateException("Root shell is unavailable; retry after authorization", error)
        }
        val path = ksuApp.applicationInfo.nativeLibraryDir + "/libksud.so"
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
        val output = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val invocation = "${quote(path)} uts-view $action"
        val job = shell.newJob().add(if (input == null) invocation else "printf '%s' ${quote(input)} | $invocation")
        val future = job.to(output, errors).enqueue()
        val result = try {
            future.get(20, TimeUnit.SECONDS)
        } finally {
            if (!future.isDone) future.cancel(false)
        }
        check(result.isSuccess) {
            errors.joinToString("\n").ifEmpty { "UTS View command failed (${result.code})" }
        }
        output.joinToString("\n")
    }

    override suspend fun status(): UtsViewStatus = UtsViewStatus.parse(command("status"))

    override suspend fun loadApps(): List<UtsTargetApp> = withContext(dispatcher) {
        val viewModel = SuperUserViewModel()
        viewModel.fetchAppList()
        viewModel.uiState.value.error?.let { error ->
            throw IllegalStateException(error.message ?: "Failed to load installed apps", error)
        }
        SuperUserViewModel.apps
            .filter { app ->
                app.uid >= 10000 &&
                    !app.isWebViewZygote &&
                    app.packageName.isNotBlank()
            }
            .map { app ->
                UtsTargetApp(
                    label = app.label,
                    packageName = app.packageName,
                    uid = app.uid,
                    isSystem = app.packageInfo.applicationInfo?.flags?.and(
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM,
                    ) != 0,
                    packageInfo = app.packageInfo,
                )
            }
            .distinctBy { it.uid }
            .sortedBy { it.label.lowercase() }
    }

    override suspend fun save(
        fields: UtsFields,
        enabled: Boolean,
        mode: UtsViewMode,
        targets: List<UtsTarget>,
    ) {
        command("set", configJson(fields, enabled, mode, targets))
    }

    override suspend fun enable() {
        command("enable")
    }

    override suspend fun disable() {
        command("disable")
    }

    override suspend fun clear() {
        command("clear")
    }

    override suspend fun takeOver(
        fields: UtsFields,
        mode: UtsViewMode,
        targets: List<UtsTarget>,
    ) {
        command("take-over", configJson(fields, true, mode, targets))
    }

    private fun configJson(
        fields: UtsFields,
        enabled: Boolean,
        mode: UtsViewMode,
        targets: List<UtsTarget>,
    ): String {
        val targetArray = org.json.JSONArray()
        targets.forEach { target ->
            targetArray.put(
                JSONObject()
                    .put("package", target.packageName)
                    .put("uid", target.uid),
            )
        }
        return JSONObject()
            .put("schema", 1)
            .put("enabled", enabled)
            .put("mode", if (mode == UtsViewMode.Global) "global" else "selected")
            .put("field_mask", fields.fieldMask())
            .put("sysname", fields.sysname)
            .put("nodename", fields.nodename)
            .put("release", fields.release)
            .put("version", fields.version)
            .put("machine", fields.machine)
            .put("domainname", fields.domainname)
            .put("targets", targetArray)
            .toString()
    }
}
