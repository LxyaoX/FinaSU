package me.weishu.kernelsu.data.modulestore

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class DownloadIntegrityTest {
    private val bytes = "verified module".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val expected = ExpectedFile(bytes.size.toLong(), hash)

    @Test
    fun `copy verifies the exact install file and removes mismatches`() {
        val file = File.createTempFile("install-test", ".zip")
        try {
            copyForInstall(bytes.inputStream(), file, expected)
            assertArrayEquals(bytes, file.readBytes())
            try {
                copyForInstall(ByteArray(bytes.size).inputStream(), file, expected)
                fail("Accepted modified bytes")
            } catch (_: IOException) {
                assertFalse(file.exists())
            }
        } finally {
            file.delete()
        }
    }

    @Test(expected = IOException::class)
    fun `stream rejects oversized downloads immediately`() {
        DownloadVerifier(expected).update(ByteArray(bytes.size + 1), bytes.size + 1)
    }

    @Test(expected = IOException::class)
    fun `stream rejects truncated downloads`() {
        DownloadVerifier(expected).finish()
    }

    @Test
    fun `expectations only resolve inside the private store`() {
        val root = File(System.getProperty("java.io.tmpdir"), "verified-test")
        assertEquals(expected, VerifiedDownloads.expectedFor(root, File(root, "$hash-${bytes.size}/module.zip")))
        assertNull(VerifiedDownloads.expectedFor(root, File(root, "../$hash-${bytes.size}/module.zip")))
    }
}
