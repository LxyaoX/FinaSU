package me.weishu.kernelsu.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeesimStatusTest {

    private fun probe(vararg lines: String) = lines.joinToString("\n")

    @Test
    fun `probe reports an enabled module with a keybox and a live daemon`() {
        val status = TeesimStatus.fromProbe(
            probe(
                "tee:installed=1",
                "tee:disabled=0",
                "tee:running=1",
                "tee:keybox=1",
                "tee:keyboxbytes=41234",
                "tee:webui=1",
                "tee:version=v4.0 (75-fcf1738-release)",
            )
        )

        assertTrue(status.installed)
        assertTrue(status.enabled)
        assertTrue(status.daemonRunning)
        assertTrue(status.keyboxPresent)
        assertTrue(status.webUiAvailable)
        assertEquals(41234L, status.keyboxBytes)
        assertEquals("v4.0 (75-fcf1738-release)", status.version)
    }

    @Test
    fun `disable marker and missing keybox are reported separately`() {
        val status = TeesimStatus.fromProbe(
            probe(
                "tee:installed=1",
                "tee:disabled=1",
                "tee:running=0",
                "tee:keybox=0",
                "tee:keyboxbytes=0",
                "tee:webui=1",
            )
        )

        assertTrue(status.installed)
        // A disabled module is not "enabled" even though its directory exists.
        assertFalse(status.enabled)
        assertFalse(status.keyboxPresent)
        assertEquals("", status.version)
    }

    @Test
    fun `a probe without the installed fact reads as not installed`() {
        val status = TeesimStatus.fromProbe("")
        assertFalse(status.installed)
        assertFalse(status.enabled)
        assertEquals(0L, status.keyboxBytes)
    }

    @Test
    fun `lines that are not part of the probe are ignored`() {
        val status = TeesimStatus.fromProbe(
            probe(
                "some unrelated output",
                "tee:installed=1",
                "tee:garbage",
                "tee:version=",
            )
        )

        assertTrue(status.installed)
        assertEquals("", status.version)
    }

    @Test
    fun `config keeps the default profile apart from the others`() {
        val config = TeesimConfig.parse(
            """
            {
              "version": 1,
              "profiles": {
                "default": { "keybox": "keybox.xml", "apps": ["com.b.app", "com.a.app"] },
                "banking": { "keybox": "bank.xml", "apps": ["com.bank.app"] }
              }
            }
            """.trimIndent()
        )

        assertFalse(config.broken)
        assertEquals(listOf("com.a.app", "com.b.app"), config.defaultApps)
        assertEquals(listOf("com.bank.app"), config.otherProfileApps)
        assertEquals(2, config.profileCount)
    }

    @Test
    fun `config tolerates duplicate and blank entries`() {
        val config = TeesimConfig.parse(
            """{"profiles":{"default":{"apps":["com.a.app","com.a.app","","  "]}}}"""
        )

        assertEquals(listOf("com.a.app"), config.defaultApps)
    }

    @Test
    fun `unreadable config is marked broken instead of empty`() {
        assertTrue(TeesimConfig.parse(null).broken)
        assertTrue(TeesimConfig.parse("").broken)
        assertTrue(TeesimConfig.parse("not json").broken)
        // Valid JSON without profiles is just as unusable for the picker.
        assertTrue(TeesimConfig.parse("""{"version":1}""").broken)
    }
}
