package me.weishu.kernelsu.ui.screen.kernelmodule

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

private const val MAX_KERNEL_MODULE_BYTES = 64L * 1024 * 1024

/** Copies a picked .ko into app cache so ksud can install it by a stable path. */
internal fun stageKernelModule(context: Context, uri: Uri): String {
    val displayName = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        } else {
            null
        }
    }
    val target = File(
        File(context.cacheDir, "kernel-module-${UUID.randomUUID()}"),
        sanitizeStagedModuleName(displayName, uri.lastPathSegment),
    )
    target.parentFile?.mkdirs()
    val input = context.contentResolver.openInputStream(uri)
        ?: error("Cannot open the selected kernel module")
    input.use { source ->
        target.delete()
        target.outputStream().use { sink ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_KERNEL_MODULE_BYTES) {
                    "Kernel module exceeds 64 MiB"
                }
                sink.write(buffer, 0, read)
            }
        }
    }
    return target.absolutePath
}

internal fun sanitizeStagedModuleName(displayName: String?, lastPathSegment: String?): String {
    val candidate = (displayName ?: lastPathSegment)
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.replace("\u0000", "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it != "." && it != ".." }
    return candidate ?: "kernel-module.ko"
}
