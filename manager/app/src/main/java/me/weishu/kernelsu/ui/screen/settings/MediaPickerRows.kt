package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSliderItem
import me.weishu.kernelsu.ui.component.material.snapToPercent
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

@Composable
internal fun MediaFileRow(filename: String?, icon: ImageVector? = null, onClick: () -> Unit) {
    ArrowPreference(
        title = stringResource(R.string.media_select_file),
        summary = filename ?: stringResource(R.string.media_no_file),
        startAction = icon?.let { { Icon(it, null) } },
        onClick = onClick,
    )
}

@Composable
internal fun ClearMediaRow(onClick: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    ArrowPreference(
        title = stringResource(R.string.media_clear),
        onClick = { confirm = true },
    )
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.media_clear)) },
        text = { Text(stringResource(R.string.media_clear_confirm)) },
        confirmButton = {
            MiuixTextButton(
                text = stringResource(R.string.media_clear),
                onClick = { confirm = false; onClick() },
            )
        },
        dismissButton = {
            MiuixTextButton(
                text = stringResource(android.R.string.cancel),
                onClick = { confirm = false },
            )
        },
    )
}

/**
 * A media setting driven by a slider.
 *
 * A drag updates the setting on every frame, so a volume move is heard while the thumb travels;
 * [onCommit] only persists once the gesture has settled. The value settles on the whole percent the
 * row shows, so the label always names the value that was stored.
 */
@Composable
internal fun MediaSlider(
    title: String,
    value: Float,
    icon: ImageVector? = null,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    SegmentedSliderItem(
        icon = icon,
        title = title,
        value = value,
        valueRange = 0f..1f,
        valueText = { "${(it * 100).roundToInt()}%" },
        onValueChange = { onValueChange(snapToPercent(it)) },
        onValueChangeFinished = { onCommit() },
    )
}
