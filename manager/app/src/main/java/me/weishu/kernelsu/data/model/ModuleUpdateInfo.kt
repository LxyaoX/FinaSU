package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class ModuleUpdateInfo(
    val downloadUrl: String,
    val version: String,
    val changelog: String,
    val expectedSize: Long? = null,
    val expectedSha256: String? = null,
) {
    companion object {
        val Empty = ModuleUpdateInfo("", "", "")
    }
}
