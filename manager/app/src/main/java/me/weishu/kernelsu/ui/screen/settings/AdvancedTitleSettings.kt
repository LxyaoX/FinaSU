package me.weishu.kernelsu.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
 *
 * The section title lives on the surrounding layout column, so this renders as a second card under
 * it and takes its spacing from [modifier].
 */
@Composable
fun AdvancedTitleSettings(modifier: Modifier = Modifier) {
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
    SegmentedColumn(modifier = modifier) {
        item {
            SegmentedSwitchItem(
                icon = Icons.Rounded.Image,
                title = title,
                summary = stringResource(R.string.media_title_image_summary),
                checked = enabled,
                onCheckedChange = {
                    VisualMediaConfig.titleEnabled = it
                    VisualMediaConfig.save(context)
                },
            )
        }
        item { MediaFileRow(filename, Icons.Rounded.FileOpen) { picker.launch("image/*") } }
        item(visible = enabled) {
            MediaSlider(
                title = stringResource(R.string.media_day_opacity),
                value = VisualMediaConfig.titleDayOpacity,
                icon = Icons.Rounded.LightMode,
                onValueChange = { VisualMediaConfig.titleDayOpacity = it },
                onCommit = { VisualMediaConfig.save(context) },
            )
        }
        item(visible = enabled) {
            MediaSlider(
                title = stringResource(R.string.media_night_opacity),
                value = VisualMediaConfig.titleNightOpacity,
                icon = Icons.Rounded.DarkMode,
                onValueChange = { VisualMediaConfig.titleNightOpacity = it },
                onCommit = { VisualMediaConfig.save(context) },
            )
        }
        item(visible = enabled) {
            MediaSlider(
                title = stringResource(R.string.media_dim),
                value = VisualMediaConfig.titleDim,
                icon = Icons.Rounded.Contrast,
                onValueChange = { VisualMediaConfig.titleDim = it },
                onCommit = { VisualMediaConfig.save(context) },
            )
        }
        item(visible = enabled) {
            SegmentedSliderItem(
                icon = Icons.Rounded.SwapHoriz,
                title = stringResource(R.string.media_title_offset),
                value = VisualMediaConfig.titleOffsetX,
                valueRange = -2f..2f,
                valueText = { "${(it * 100).toInt()} dp" },
                onValueChange = { VisualMediaConfig.titleOffsetX = it },
                onValueChangeFinished = { VisualMediaConfig.save(context) },
            )
        }
        item(visible = filename != null) { ClearMediaRow { scope.launch { VisualMediaConfig.clear(context, false) } } }
    }

}
