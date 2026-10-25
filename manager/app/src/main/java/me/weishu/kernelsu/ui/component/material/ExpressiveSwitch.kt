package me.weishu.kernelsu.ui.component.material

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Switch

/**
 * A switch drawn by miuix.
 *
 * It used to take a thumb icon, its own colours and an interaction source from Material's switch;
 * miuix's draws its own knob and has none of those, so the wrapper is down to the state and the
 * callback it needs.
 */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}
