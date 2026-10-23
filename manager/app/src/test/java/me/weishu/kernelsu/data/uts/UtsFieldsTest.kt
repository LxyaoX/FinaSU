package me.weishu.kernelsu.data.uts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtsFieldsTest {
    @Test
    fun `editing one field preserves untouched mask bits`() {
        val fields = UtsFields(
            sysname = "Linux",
            nodename = "hidden",
            release = "",
            mask = 0b000011,
        )

        val changed = fields.withField(2, "6.1.75-android14")

        assertEquals("hidden", changed.nodename)
        assertEquals(0b000111, changed.fieldMask())
    }

    @Test
    fun `clearing an edited field clears only that bit`() {
        val fields = UtsFields(
            release = "6.1.75-android14",
            version = "#1 SMP",
            mask = 0b001100,
        )

        val changed = fields.withField(2, "")

        assertEquals(0b001000, changed.fieldMask())
        assertEquals("#1 SMP", changed.version)
    }

    @Test
    fun `status keeps the configured mask independent of text emptiness`() {
        val status = UtsViewStatus.parse(
            """{
                "supported":true,
                "enabled":false,
                "active":false,
                "field_mask":4,
                "fields":{
                    "sysname":"",
                    "nodename":"present-but-unselected",
                    "release":"",
                    "version":"",
                    "machine":"",
                    "domainname":""
                },
                "source":"none",
                "provider":{"uname_applied":false,"uname_scheduled":false},
                "config_error":null
            }""",
        )

        assertEquals(4, status.fields.fieldMask())
        assertEquals("present-but-unselected", status.fields.nodename)
        assertEquals(UtsViewMode.Selected, status.mode)
        assertTrue(status.targets.isEmpty())
    }

    @Test
    fun `status requires an observed match to claim effect`() {
        val status = UtsViewStatus.parse(
            """{
                "state":"enabled",
                "supported":true,
                "enabled":true,
                "active":true,
                "field_mask":4,
                "fields":{
                    "sysname":"Linux",
                    "nodename":"",
                    "release":"6.1.75-android14",
                    "version":"",
                    "machine":"",
                    "domainname":""
                },
                "source":"uts",
                "configuration":{"present":true,"valid":true,"enabled":true,"field_mask":4},
                "observation":{
                    "generation":7,
                    "matched":0,
                    "discarded":0,
                    "last_pid":0,
                    "last_uid":0,
                    "skip_not_app":0,
                    "skip_isolated":0,
                    "skip_not_target":2,
                    "skip_not_zygote":0,
                    "skip_private_ns":0,
                    "unshare_failed":0,
                    "merge_missing_ns":0
                },
                "provider":{"uname_applied":false,"uname_scheduled":false,"uname_uncertain":false},
                "config_error":null
            }""",
        )

        assertEquals(UtsViewPhase.Enabled, status.phase)
        assertEquals(0, status.observation.matched)
        assertEquals(7, status.observation.generation)
        assertEquals(2, status.observation.skipNotTarget)
        assertTrue(status.hasPersistedConfig)
    }

    @Test
    fun `global mode does not require app matches`() {
        val status = UtsViewStatus.parse(
            """{
                "state":"enabled",
                "supported":true,
                "enabled":true,
                "active":true,
                "mode":"global",
                "field_mask":4,
                "fields":{
                    "sysname":"",
                    "nodename":"",
                    "release":"6.1.75-android14",
                    "version":"",
                    "machine":"",
                    "domainname":""
                },
                "source":"uts",
                "observation":{"matched":0},
                "provider":{"uname_applied":false,"uname_scheduled":false,"uname_uncertain":false},
                "config_error":null
            }""",
        )

        assertEquals(UtsViewMode.Global, status.mode)
        assertEquals(UtsViewPhase.Enabled, status.phase)
        assertTrue(status.active)
    }

    @Test
    fun `status parses selected targets`() {
        val status = UtsViewStatus.parse(
            """{
                "supported":true,
                "enabled":true,
                "active":true,
                "mode":"selected",
                "field_mask":4,
                "fields":{},
                "targets":[{"package":"com.example.app","uid":10123}],
                "provider":{"uname_applied":false,"uname_scheduled":false,"uname_uncertain":false},
                "config_error":null
            }""",
        )

        assertEquals(UtsViewMode.Selected, status.mode)
        assertEquals(listOf(UtsTarget("com.example.app", 10123)), status.targets)
    }

    @Test
    fun `provider and runtime failures remain visible`() {
        val status = UtsViewStatus.parse(
            """{
                "state":"error",
                "supported":true,
                "enabled":true,
                "active":false,
                "field_mask":4,
                "fields":{},
                "source":"unknown",
                "provider":{"uname_applied":false,"uname_scheduled":false,"uname_uncertain":true},
                "provider_error":"SUSFS state is unreadable",
                "runtime_error":"UTS restore failed",
                "config_error":null,
                "kernel_error":null
            }""",
        )

        assertEquals("unknown", status.provider)
        assertEquals(UtsViewPhase.Error, status.phase)
        assertEquals("SUSFS state is unreadable", status.providerError)
        assertEquals("UTS restore failed", status.runtimeError)
    }

    @Test
    fun `null provider and runtime errors remain absent`() {
        val status = UtsViewStatus.parse(
            """{
                "supported":true,
                "enabled":true,
                "active":true,
                "field_mask":4,
                "fields":{},
                "provider":{"uname_applied":false,"uname_scheduled":false,"uname_uncertain":false},
                "provider_error":null,
                "runtime_error":null,
                "config_error":null,
                "kernel_error":null
            }""",
        )

        assertNull(status.providerError)
        assertNull(status.runtimeError)
    }
}
