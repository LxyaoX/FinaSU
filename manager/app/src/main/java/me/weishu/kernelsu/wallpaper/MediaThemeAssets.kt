package me.weishu.kernelsu.wallpaper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.media.VisualMediaConfig
import org.json.JSONObject
import java.io.File

/** FP's JSON filename is also its ZIP entry name; these assets therefore use the group API. */
internal class VisualMediaThemeAsset(private val video: Boolean) : ThemedAsset {
    override val base = if (video) "video_background" else "title_image"
    override fun currentFile(context: Context): File? = VisualMediaConfig.file(context, video)
    override fun extension(file: File): String = if (video) file.extension else super.extension(file)
    override fun writeConfig(json: JSONObject) = VisualMediaConfig.writeConfig(json)
    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        val enabled = json.optBoolean(if (video) "isVideoBackgroundEnabled" else "isAdvancedTitleStyleEnabled")
        if (enabled && file != null) VisualMediaConfig.replace(context, video, file)
        else if (!video && !enabled) VisualMediaConfig.replace(context, false, null)
        withContext(Dispatchers.Main) {
            VisualMediaConfig.applySettings(json)
            VisualMediaConfig.save(context)
        }
    }
    override suspend fun reset(context: Context) {
        VisualMediaConfig.clear(context, video)
        withContext(Dispatchers.Main) {
            if (video) VisualMediaConfig.videoVolume = 0f else {
                VisualMediaConfig.titleDayOpacity = 1f
                VisualMediaConfig.titleNightOpacity = 1f
                VisualMediaConfig.titleDim = 0f
                VisualMediaConfig.titleOffsetX = 0f
            }
            VisualMediaConfig.save(context)
        }
    }
}
