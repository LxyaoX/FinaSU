package me.weishu.kernelsu.ui.screen.settings

import me.weishu.kernelsu.data.model.FolkZygiskStatus
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolkZygiskRecoveryVisibilityTest {
    @Test fun enabledBlockedStatusShowsRecovery() {
        val state = SettingsUiState(
            folkZygiskStatus = status(enabled = true, startupBlocked = true),
        )
        assertTrue(showFolkZygiskRecovery(state))
    }

    @Test fun healthyEnabledStatusDoesNotShowRecovery() {
        val state = SettingsUiState(
            folkZygiskStatus = status(enabled = true, startupBlocked = false),
        )
        assertFalse(showFolkZygiskRecovery(state))
    }

    private fun status(enabled: Boolean, startupBlocked: Boolean): FolkZygiskStatus =
        FolkZygiskStatus.fromJson(
            JSONObject(
                """
                {
                  "schema_version": 1,
                  "enabled": $enabled,
                  "engine_available": true,
                  "enable_blockers": ${if (startupBlocked) "[\"startup_protection\"]" else "[]"},
                  "external_providers": [],
                  "injection_state": "unverified",
                  "reboot_required": null,
                  "runtime": null,
                  "startup_failures": ${if (startupBlocked) 3 else 0},
                  "startup_blocked": $startupBlocked
                }
                """.trimIndent(),
            ),
        )
}
