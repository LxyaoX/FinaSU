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

data class NetIsolateStatus(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val compatSupported: Boolean = false,
    val uids: List<Int> = emptyList(),
) {
    companion object {
        fun parse(text: String): NetIsolateStatus {
            val json = JSONObject(text)
            val array = json.optJSONArray("uids") ?: JSONArray()
            return NetIsolateStatus(
                supported = json.optBoolean("supported"),
                enabled = json.optBoolean("enabled"),
                compatSupported = json.optBoolean("compat_supported"),
                uids = List(array.length()) { index -> array.getInt(index) },
            )
        }
    }
}

interface NetIsolateDataSource {
    suspend fun status(): NetIsolateStatus
    suspend fun loadApps(): List<UtsTargetApp>
    suspend fun save(enabled: Boolean, uids: List<Int>)
    suspend fun disable()
    suspend fun clear()
}

class NetIsolateRepository(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NetIsolateDataSource {
    private suspend fun command(action: String, input: String? = null): String =
        withContext(dispatcher) {
            require(action in setOf("status", "set", "disable", "clear"))
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

    override suspend fun save(enabled: Boolean, uids: List<Int>) {
        val array = JSONArray()
        uids.distinct().sorted().forEach(array::put)
        command(
            "set",
            JSONObject()
                .put("enabled", enabled)
                .put("uids", array)
                .toString(),
        )
    }

    override suspend fun disable() {
        command("disable")
    }

    override suspend fun clear() {
        command("clear")
    }
}
