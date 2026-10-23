package me.weishu.kernelsu.data.netisolate

import android.content.pm.ApplicationInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.weishu.kernelsu.data.uts.UtsTargetApp
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.RootShell
import me.weishu.kernelsu.ui.viewmodel.SuperUserViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

const val NETISOLATE_MAX_UIDS = 256

enum class NetIsolateMode {
    Standard,
    Loopback,
    Strict,
}

data class NetIsolatePolicy(
    val uid: Int,
    val strictSocket: Boolean,
    val allowLoopback: Boolean,
)

data class NetIsolateStatus(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val compatSupported: Boolean = false,
    val policySupported: Boolean = false,
    val strictSocketSupported: Boolean = false,
    val strictSocket: Boolean = false,
    val allowLoopbackSupported: Boolean = false,
    val allowLoopback: Boolean = false,
    val uids: List<Int> = emptyList(),
    val policies: List<NetIsolatePolicy> = emptyList(),
) {
    companion object {
        fun parse(text: String): NetIsolateStatus {
            val json = JSONObject(text)
            val array = json.optJSONArray("uids") ?: JSONArray()
            val uids = List(array.length()) { index -> array.getInt(index) }
            val strictSocket = json.optBoolean("strict_socket")
            val allowLoopback = json.optBoolean("allow_loopback")
            val policyArray = json.optJSONArray("policies")
            val policies = if (policyArray != null) {
                List(policyArray.length()) { index ->
                    val policy = policyArray.getJSONObject(index)
                    NetIsolatePolicy(
                        uid = policy.getInt("uid"),
                        strictSocket = policy.optBoolean("strict_socket"),
                        allowLoopback = policy.optBoolean("allow_loopback"),
                    )
                }
            } else {
                uids.map { uid ->
                    NetIsolatePolicy(uid, strictSocket, allowLoopback)
                }
            }
            return NetIsolateStatus(
                supported = json.optBoolean("supported"),
                enabled = json.optBoolean("enabled"),
                compatSupported = json.optBoolean("compat_supported"),
                policySupported = json.optBoolean("policy_supported"),
                strictSocketSupported = json.optBoolean("strict_socket_supported"),
                strictSocket = strictSocket,
                allowLoopbackSupported = json.optBoolean("allow_loopback_supported"),
                allowLoopback = allowLoopback,
                uids = uids,
                policies = policies,
            )
        }
    }
}

fun NetIsolateStatus.mode(): NetIsolateMode = when {
    strictSocket -> NetIsolateMode.Strict
    allowLoopback -> NetIsolateMode.Loopback
    else -> NetIsolateMode.Standard
}

fun NetIsolateStatus.supportsMode(mode: NetIsolateMode): Boolean =
    supported && when (mode) {
        NetIsolateMode.Standard -> true
        NetIsolateMode.Loopback -> allowLoopbackSupported
        NetIsolateMode.Strict -> strictSocketSupported
    }

data class NetIsolateEvent(
    val timestampNanos: Long,
    val uid: Int,
    val operation: String,
    val family: String,
    val address: String,
    val port: Int,
)

data class NetIsolateEvents(
    val supported: Boolean = false,
    val capacity: Int = 0,
    val dropped: Long = 0,
    val events: List<NetIsolateEvent> = emptyList(),
) {
    companion object {
        fun parse(text: String): NetIsolateEvents {
            val json = JSONObject(text)
            val array = json.optJSONArray("events") ?: JSONArray()
            val events = List(array.length()) { index ->
                val event = array.getJSONObject(index)
                NetIsolateEvent(
                    timestampNanos = event.optLong("timestamp_ns"),
                    uid = event.optInt("uid"),
                    operation = event.optString("operation"),
                    family = event.optString("family"),
                    address = event.optString("address"),
                    port = event.optInt("port"),
                )
            }
            return NetIsolateEvents(
                supported = json.optBoolean("supported"),
                capacity = json.optInt("capacity"),
                dropped = json.optLong("dropped"),
                events = events,
            )
        }
    }
}

interface NetIsolateDataSource {
    suspend fun status(): NetIsolateStatus
    suspend fun events(): NetIsolateEvents
    suspend fun loadApps(): List<UtsTargetApp>
    suspend fun save(
        enabled: Boolean,
        uids: List<Int>,
        strictSocket: Boolean,
        allowLoopback: Boolean,
    )
    suspend fun savePolicies(enabled: Boolean, policies: List<NetIsolatePolicy>)
    suspend fun disable()
    suspend fun clear()
    suspend fun clearEvents()
}

class NetIsolateRepository(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NetIsolateDataSource {
    private suspend fun command(action: String, input: String? = null): String =
        withContext(dispatcher) {
            require(action in setOf("status", "set", "disable", "clear", "events", "events-clear"))
            require(input == null || input.toByteArray().size <= 64 * 1024)
            val shell = try {
                withTimeout(15_000L) { RootShell.awaitRoot() }
            } catch (error: TimeoutCancellationException) {
                throw IllegalStateException(
                    "Root shell is unavailable; retry after authorization",
                    error,
                )
            }
            val path = ksuApp.applicationInfo.nativeLibraryDir + "/libksud.so"
            fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
            val output = mutableListOf<String>()
            val errors = mutableListOf<String>()
            val invocation = "${quote(path)} netisolate $action"
            val job = shell.newJob().add(
                if (input == null) invocation else "printf '%s' ${quote(input)} | $invocation",
            )
            val future = job.to(output, errors).enqueue()
            val result = try {
                future.get(20, TimeUnit.SECONDS)
            } finally {
                if (!future.isDone) future.cancel(false)
            }
            check(result.isSuccess) {
                errors.joinToString("\n").ifEmpty {
                    "Network isolation command failed (${result.code})"
                }
            }
            output.joinToString("\n")
        }

    override suspend fun status(): NetIsolateStatus = NetIsolateStatus.parse(command("status"))

    override suspend fun events(): NetIsolateEvents = NetIsolateEvents.parse(command("events"))

    override suspend fun loadApps(): List<UtsTargetApp> = withContext(dispatcher) {
        val viewModel = SuperUserViewModel()
        viewModel.fetchAppList()
        viewModel.uiState.value.error?.let { error ->
            throw IllegalStateException(error.message ?: "Failed to load installed apps", error)
        }
        val managerPackage = ksuApp.packageName
        val managerUid = ksuApp.applicationInfo.uid
        SuperUserViewModel.apps
            .filter { app ->
                app.uid >= 10000 &&
                    app.uid != managerUid &&
                    app.packageName != managerPackage &&
                    !app.isWebViewZygote &&
                    app.packageName.isNotBlank()
            }
            .map { app ->
                UtsTargetApp(
                    label = app.label,
                    packageName = app.packageName,
                    uid = app.uid,
                    isSystem = app.packageInfo.applicationInfo?.flags?.and(
                        ApplicationInfo.FLAG_SYSTEM,
                    ) != 0,
                    packageInfo = app.packageInfo,
                )
            }
            .distinctBy { it.uid }
            .sortedBy { it.label.lowercase() }
    }

    override suspend fun save(
        enabled: Boolean,
        uids: List<Int>,
        strictSocket: Boolean,
        allowLoopback: Boolean,
    ) {
        savePolicies(
            enabled,
            uids.distinct().sorted().map { uid ->
                NetIsolatePolicy(uid, strictSocket, allowLoopback)
            },
        )
    }

    override suspend fun savePolicies(enabled: Boolean, policies: List<NetIsolatePolicy>) {
        val sorted = policies.sortedBy { it.uid }
        require(sorted.map(NetIsolatePolicy::uid).distinct().size == sorted.size) {
            "Network isolation policy UIDs must be unique"
        }
        val array = JSONArray()
        sorted.forEach { array.put(it.uid) }
        val policyArray = JSONArray()
        sorted.forEach { policy ->
            policyArray.put(
                JSONObject()
                    .put("uid", policy.uid)
                    .put("strict_socket", policy.strictSocket)
                    .put("allow_loopback", policy.allowLoopback),
            )
        }
        val strictSocket = sorted.isNotEmpty() &&
            sorted.all(NetIsolatePolicy::strictSocket)
        val allowLoopback = sorted.isNotEmpty() &&
            sorted.all(NetIsolatePolicy::allowLoopback)
        command(
            "set",
            JSONObject()
                .put("enabled", enabled)
                .put("uids", array)
                .put("strict_socket", strictSocket)
                .put("allow_loopback", allowLoopback)
                .put("policies", policyArray)
                .toString(),
        )
    }

    override suspend fun disable() {
        command("disable")
    }

    override suspend fun clear() {
        command("clear")
    }

    override suspend fun clearEvents() {
        command("events-clear")
    }
}
