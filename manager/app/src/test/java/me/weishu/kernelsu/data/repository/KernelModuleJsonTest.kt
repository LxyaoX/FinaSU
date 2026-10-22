package me.weishu.kernelsu.data.repository

import me.weishu.kernelsu.data.model.LoadState
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelModuleJsonTest {
    @Test
    fun parsesStableStateAndMetadata() {
        val response = parseKernelModuleResponse(
            """
            {
              "schema_version": 1,
              "safe_mode": false,
              "modules": [{
                "id": "demo",
                "internal_name": "demo",
                "display_name": "Demo",
                "version": "2",
                "authors": ["A", "B"],
                "description": "Example",
                "license": "GPL",
                "vermagic": "test",
                "architecture": "aarch64",
                "depends": ["dep"],
                "parameters": [{"name": "mode", "value_type": "int"}],
                "diagnostics": [],
                "file_size": 123,
                "sha256": "abcdef",
                "autoload": true,
                "disabled": false,
                "effective_autoload": true,
                "loaded": "not_loaded",
                "params": "mode=1",
                "loadable": true,
                "protected": false
              }]
            }
            """.trimIndent(),
        )

        assertFalse(response.safeMode)
        assertEquals(1, response.modules.size)
        val module = response.modules.single()
        assertEquals("A, B", module.author)
        assertEquals("mode=1", module.params)
        assertEquals(LoadState.NOT_LOADED, module.loaded)
        assertTrue(module.effectiveAutoload)
        assertFalse(module.disabled)
    }

    @Test
    fun unknownRuntimeStateIsNotFoldedIntoNotLoaded() {
        val module = parseKernelModuleList(
            """
            {"modules":[{
              "id":"x","display_name":"X","authors":[],"depends":[],"parameters":[],
              "diagnostics":[],"loaded":"unknown","state_error":"cannot read",
              "autoload":false,"disabled":false,"effective_autoload":false,
              "loadable":false,"protected":false
            }]}
            """.trimIndent(),
        ).single()
        assertEquals(LoadState.UNKNOWN, module.loaded)
        assertEquals("cannot read", module.stateError)
    }

    @Test
    fun parsesMissingLastResultAndRejectsMalformedJson() {
        assertNull(parseKernelModuleLog("""{"schema_version":1,"last_result":null}"""))
        assertThrows(JSONException::class.java) {
            parseKernelModuleList("{not json")
        }
    }
}
