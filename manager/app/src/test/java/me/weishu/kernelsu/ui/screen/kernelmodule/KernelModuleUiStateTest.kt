package me.weishu.kernelsu.ui.screen.kernelmodule

import me.weishu.kernelsu.data.model.KernelModule
import me.weishu.kernelsu.data.model.LoadState
import org.junit.Assert.assertEquals
import org.junit.Test

class KernelModuleUiStateTest {
    @Test
    fun searchesIdentityAuthorsAndDescriptionThenSortsByName() {
        val beta = module("beta", "Beta", "Alice", "GPU helper")
        val alpha = module("alpha", "Alpha", "Bob", "Storage helper")
        val state = KernelModuleUiState(modules = listOf(beta, alpha))

        assertEquals(listOf("alpha", "beta"), state.visibleModules.map { it.id })
        assertEquals(
            listOf("beta"),
            state.copy(searchText = "alice").visibleModules.map { it.id },
        )
        assertEquals(
            listOf("alpha"),
            state.copy(searchText = "storage").visibleModules.map { it.id },
        )
    }

    private fun module(
        id: String,
        name: String,
        author: String,
        description: String,
    ) = KernelModule(
        id = id,
        internalName = id,
        displayName = name,
        version = "1",
        authors = listOf(author),
        description = description,
        license = "GPL",
        vermagic = "test",
        architecture = "aarch64",
        depends = emptyList(),
        parameters = emptyList(),
        diagnostics = emptyList(),
        fileSize = 1,
        sha256 = "hash",
        autoload = false,
        disabled = false,
        effectiveAutoload = false,
        loaded = LoadState.NOT_LOADED,
        stateError = null,
        params = "",
        loadable = true,
        protected = false,
        error = null,
    )
}
