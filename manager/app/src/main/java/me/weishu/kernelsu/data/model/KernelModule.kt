package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class KernelModuleParameter(
    val name: String,
    val valueType: String?,
)

@Immutable
data class KernelModule(
    val id: String,
    val internalName: String?,
    val displayName: String,
    val version: String,
    val authors: List<String>,
    val description: String,
    val license: String,
    val vermagic: String,
    val architecture: String,
    val depends: List<String>,
    val parameters: List<KernelModuleParameter>,
    val diagnostics: List<String>,
    val fileSize: Long,
    val sha256: String,
    val autoload: Boolean,
    val disabled: Boolean,
    val effectiveAutoload: Boolean,
    val loaded: LoadState,
    val stateError: String?,
    val params: String,
    val loadable: Boolean,
    val protected: Boolean,
    val error: String?,
) {
    val author: String
        get() = authors.joinToString(", ")

    val name: String
        get() = displayName.ifBlank { internalName ?: id }
}

@Immutable
enum class LoadState {
    LOADED,
    NOT_LOADED,
    UNKNOWN,
}

@Immutable
data class KernelModuleLastResult(
    val bootId: String,
    val operation: String,
    val outcome: String,
    val message: String,
)

@Immutable
data class KernelModuleCollection(
    val modules: List<KernelModule>,
    val safeMode: Boolean,
)
