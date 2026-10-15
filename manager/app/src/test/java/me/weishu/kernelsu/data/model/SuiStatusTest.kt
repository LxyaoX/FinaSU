package me.weishu.kernelsu.data.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SuiStatusTest {
    private fun payload() = JSONObject(
        """{
            "schema_version":1,"state":"enabled_pending_reboot","reason":"","detail":"",
            "expectation":true,"installed":true,"payload_version":"13.5.4.3",
            "payload_current":true,"module_disabled":false,"reboot_required":true,
            "conflicts":[],"zygisk_enabled":true,"engine_available":true,
            "injection_state":"unverified","external_providers":[],
            "root_pids":[1],"shell_pids":[2],"daemon_healthy":true
        }""",
    )

    @Test fun pendingRebootIsSelectedButNotVerified() {
        val status = SuiStatus.fromJson(payload())
        assertTrue(status.selected)
        assertFalse(status.verified)
        assertTrue(status.rebootRequired)
        assertFalse(status.needsDiagnostics)
    }

    @Test fun externalProviderIsNeverReportedAsVerified() {
        val json = payload()
            .put("state", "enabled_external_unverified")
            .put("external_providers", JSONArray(listOf("zygisknext")))
        val status = SuiStatus.fromJson(json)
        assertTrue(status.selected)
        assertFalse(status.verified)
        assertTrue(status.needsDiagnostics)
        assertEquals(listOf("zygisknext"), status.externalProviders)
    }

    @Test fun malformedFieldsFailInsteadOfDefaulting() {
        for ((key, value) in listOf(
            "schema_version" to 2,
            "state" to "running",
            "installed" to "true",
            "conflicts" to "none",
            "zygisk_enabled" to 1,
        )) {
            assertThrows(Exception::class.java) {
                SuiStatus.fromJson(payload().put(key, value))
            }
        }
        val missing = payload().apply { remove("daemon_healthy") }
        assertThrows(Exception::class.java) { SuiStatus.fromJson(missing) }
    }
}
