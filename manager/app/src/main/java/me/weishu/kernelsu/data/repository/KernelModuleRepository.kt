package me.weishu.kernelsu.data.repository

import me.weishu.kernelsu.data.model.KernelModule
import me.weishu.kernelsu.data.model.KernelModuleCollection
import me.weishu.kernelsu.data.model.KernelModuleLastResult

interface KernelModuleRepository {
    suspend fun getModules(): Result<KernelModuleCollection>

    suspend fun install(path: String): Result<Unit>

    suspend fun remove(id: String): Result<Unit>

    suspend fun enable(id: String): Result<Unit>

    suspend fun disable(id: String): Result<Unit>

    suspend fun setAutoload(id: String, enabled: Boolean): Result<Unit>

    suspend fun setParams(id: String, params: String): Result<Unit>

    suspend fun load(id: String): Result<Unit>

    suspend fun getLog(id: String): Result<KernelModuleLastResult?>
}

class KernelModuleCommandException(val code: Int, message: String) : Exception(message)
