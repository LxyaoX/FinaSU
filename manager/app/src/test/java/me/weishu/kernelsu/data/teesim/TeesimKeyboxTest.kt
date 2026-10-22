package me.weishu.kernelsu.data.teesim

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeesimKeyboxTest {

    private val certificate = "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----"

    @Test
    fun `accepts the AndroidAttestation wrapper`() {
        val text = """
            <?xml version="1.0"?>
            <AndroidAttestation>
              <Keybox DeviceID="gen">
                <Key algorithm="ecdsa">
                  <Certificate>$certificate</Certificate>
                </Key>
                <Key algorithm="rsa">
                  <Certificate>$certificate</Certificate>
                </Key>
              </Keybox>
            </AndroidAttestation>
        """.trimIndent()

        assertTrue(TeesimKeybox.isUsable(text))
    }

    @Test
    fun `accepts a bare Keybox root`() {
        // Published keyboxes come in both shapes; the daemon resolves `Keybox` at the root or
        // under `AndroidAttestation`, so rejecting this one blocked a file the module accepts.
        val text = """
            <Keybox DeviceID="rkp">
              <Key algorithm="rsa">
                <Certificate>$certificate</Certificate>
              </Key>
            </Keybox>
        """.trimIndent()

        assertTrue(TeesimKeybox.isUsable(text))
    }

    @Test
    fun `accepts other spellings of the algorithm attribute`() {
        val text = """
            <Keybox>
              <Key Algorithm='EC'>
                <Certificate>$certificate</Certificate>
              </Key>
            </Keybox>
        """.trimIndent()

        assertTrue(TeesimKeybox.isUsable(text))
    }

    @Test
    fun `rejects a file with no key block`() {
        assertFalse(TeesimKeybox.isUsable("<Keybox><Certificate>$certificate</Certificate></Keybox>"))
    }

    @Test
    fun `rejects a file with no certificate`() {
        assertFalse(TeesimKeybox.isUsable("""<Keybox><Key algorithm="rsa">nothing</Key></Keybox>"""))
    }

    @Test
    fun `rejects unrelated xml`() {
        assertFalse(TeesimKeybox.isUsable("""<resources><string name="x">y</string></resources>"""))
        assertFalse(TeesimKeybox.isUsable(""))
    }
}
