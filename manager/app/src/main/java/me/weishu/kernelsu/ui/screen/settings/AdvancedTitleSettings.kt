package me.weishu.kernelsu.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.media.VisualMediaConfig
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedSliderItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem

/**
 * The advanced home title image and its opacity/dim/offset controls.
 *
 * It is an independent layout feature (the header of the home screen), so it sits next to the other
 * layout entries in the appearance category, not with the background media. The video background,
 * which *is* part of the background, lives on the wallpaper page instead.
 */
@Composable
fun AdvancedTitleSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val success = withContext(Dispatchers.IO) { VisualMediaConfig.select(context, uri, false) }
            if (!success) Toast.makeText(context, R.string.media_import_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val enabled = VisualMediaConfig.titleEnabled
    val title = stringResource(R.string.media_title_image)
    val filename = VisualMediaConfig.titleFilename
    SegmentedColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), title = title) {
        item {
            SegmentedSwitchItem(title = title, checked = enabled) {
                VisualMediaConfig.titleEnabled = it
                VisualMediaConfig.save(context)
            }
        }
        item { MediaFileRow(filename) { picker.launch("image/*") } }
        item(visible = enabled) {
            MediaSlider(stringResource(R.string.media_day_opacity), VisualMediaConfig.titleDayOpacity) {
                VisualMediaConfig.titleDayOpacity = it; VisualMediaConfig.save(context)
            }
        }
        item(visible = enabled) {
            MediaSlider(stringResource(R.string.media_night_opacity), VisualMediaConfig.titleNightOpacity) {
                VisualMediaConfig.titleNightOpacity = it; VisualMediaConfig.save(context)
            }
        }
        item(visible = enabled) {
            MediaSlider(stringResource(R.string.media_dim), VisualMediaConfig.titleDim) {
                VisualMediaConfig.titleDim = it; VisualMediaConfig.save(context)
            }
        }
        item(visible = enabled) {
            SegmentedSliderItem(title = stringResource(R.string.media_title_offset), value = VisualMediaConfig.titleOffsetX,
                valueRange = -2f..2f, valueText = { "${(it * 100).toInt()} dp" }, onValueChangeFinished = {
                    VisualMediaConfig.titleOffsetX = it; VisualMediaConfig.save(context)
                })
        }
        item(visible = filename != null) { ClearMediaRow { scope.launch { VisualMediaConfig.clear(context, false) } } }
    }
}
