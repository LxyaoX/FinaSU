package me.weishu.kernelsu.ui.screen.install

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import java.io.File

@Parcelize
internal sealed class InstallMethod : Parcelable {
    data class AnyKernel(
        val uri: Uri? = null,
        @get:StringRes override val label: Int = R.string.install_anykernel,
        override val summary: String?,
        val displayName: String? = null,
        val sizeBytes: Long? = null,
        val inactiveSlot: Boolean = false,
    ) : InstallMethod()

    data class SelectFile(
        val uri: Uri? = null,
        @get:StringRes override val label: Int = R.string.select_file,
        override val summary: String?
    ) : InstallMethod()

    data class DownloadFile(
        val url: String? = null,
        val partition: String? = null,
        @get:StringRes override val label: Int = R.string.download_file,
        override val summary: String?
    ) : InstallMethod()

    data object DirectInstall : InstallMethod() {
        override val label: Int
            get() = R.string.direct_install
    }

    data object DirectInstallToInactiveSlot : InstallMethod() {
        override val label: Int
            get() = R.string.install_inactive_slot
    }

    abstract val label: Int

    @IgnoredOnParcel
    open val summary: String? = null
}

internal data class AnyKernelFileInfo(
    val displayName: String?,
    val sizeBytes: Long?,
)

internal suspend fun readAnyKernelFileInfo(context: Context, uri: Uri): AnyKernelFileInfo =
    withContext(Dispatchers.IO) {
        if (uri.scheme == "file") {
            val file = uri.path?.let(::File)
            return@withContext AnyKernelFileInfo(
                displayName = file?.name,
                sizeBytes = file?.takeIf(File::exists)?.length(),
            )
        }

        var displayName: String? = null
        var sizeBytes: Long? = null
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                        displayName = cursor.getString(nameIndex)?.takeIf(String::isNotBlank)
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex).takeIf { it >= 0 }
                    }
                }
            }
        } catch (_: Throwable) {
        }
        AnyKernelFileInfo(
            displayName = displayName ?: uri.lastPathSegment,
            sizeBytes = sizeBytes,
        )
    }

fun isKoFile(context: Context, uri: Uri): Boolean {
    val seg = uri.lastPathSegment ?: ""
    if (seg.endsWith(".ko", ignoreCase = true)) return true

    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                name?.endsWith(".ko", ignoreCase = true) == true
            } else {
                false
            }
        } ?: false
    } catch (_: Throwable) {
        false
    }
}
