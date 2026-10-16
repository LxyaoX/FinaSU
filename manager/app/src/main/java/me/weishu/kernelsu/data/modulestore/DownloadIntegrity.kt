package me.weishu.kernelsu.data.modulestore

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

data class ExpectedFile(val size: Long, val sha256: String) {
    init {
        require(size > 0 && Regex("^[0-9a-f]{64}$").matches(sha256))
    }
}

class DownloadVerifier(private val expected: ExpectedFile) {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var bytes = 0L

    fun update(buffer: ByteArray, length: Int) {
        if (length.toLong() > expected.size - bytes) throw IOException("Download exceeds expected size")
        bytes += length
        digest.update(buffer, 0, length)
    }

    fun finish() {
        if (bytes != expected.size) throw IOException("Download size mismatch")
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (hash != expected.sha256) throw IOException("Download SHA-256 mismatch")
    }
}

object VerifiedDownloads {
    private val directoryPattern = Regex("^([0-9a-f]{64})-([1-9][0-9]*)$")

    fun expectedFor(root: File, file: File): ExpectedFile? {
        val parent = file.canonicalFile.parentFile ?: return null
        if (parent.parentFile != root.canonicalFile) return null
        val match = directoryPattern.matchEntire(parent.name) ?: return null
        val size = match.groupValues[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return ExpectedFile(size, match.groupValues[1])
    }

    fun download(
        root: File,
        expected: ExpectedFile,
        name: String,
        input: InputStream,
        checkActive: () -> Unit,
        onProgress: (Long) -> Unit,
    ): File {
        val directory = File(root, "${expected.sha256}-${expected.size}")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create download directory" }
        val safeName = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').take(120).ifEmpty { "module.zip" }
        val target = File(directory, safeName)
        val partial = File.createTempFile("download-", ".part", directory)
        try {
            val verifier = DownloadVerifier(expected)
            var bytes = 0L
            FileOutputStream(partial).use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    verifier.update(buffer, count)
                    output.write(buffer, 0, count)
                    bytes += count
                    onProgress(bytes)
                }
                output.flush()
                output.fd.sync()
            }
            verifier.finish()
            check(partial.renameTo(target)) { "Cannot publish verified download" }
            return target
        } finally {
            partial.delete()
        }
    }
}

fun copyForInstall(input: InputStream, destination: File, expected: ExpectedFile?) {
    try {
        val verifier = expected?.let(::DownloadVerifier)
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                verifier?.update(buffer, count)
                output.write(buffer, 0, count)
            }
            output.flush()
            output.fd.sync()
        }
        verifier?.finish()
    } catch (error: Throwable) {
        destination.delete()
        throw error
    }
}
