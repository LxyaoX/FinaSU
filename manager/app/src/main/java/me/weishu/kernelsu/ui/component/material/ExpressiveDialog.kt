package me.weishu.kernelsu.ui.component.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The dialog every screen in this manager shows, drawn the way miuix draws one: a rounded surface
 * on the miuix background colour, the icon above the title, and the buttons sharing one row at the
 * bottom - dismiss on the left, confirm on the right - instead of stacked on top of each other.
 *
 * The signature is the one this app already used - slots for the icon, the title, the body and the
 * two buttons - so a caller keeps its own composables and only inherits the frame. Colours reach
 * them through `LocalContentColor`, which is why a plain `Text` in the title slot comes out in the
 * title colour without being told to.
 */
@Composable
fun ExpressiveDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = MiuixTheme.colorScheme.background,
    iconContentColor: Color = MiuixTheme.colorScheme.primary,
    titleContentColor: Color = MiuixTheme.colorScheme.onBackground,
    textContentColor: Color = MiuixTheme.colorScheme.onSurfaceSecondary,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    // Wallpaper tokens are cleared so nothing in here asks a wallpaper for a colour: a dialog tinted
    // by the home background stops being readable the moment that background is dark.
    CompositionLocalProvider(LocalFolkWallpaperTokens provides null) {
        Dialog(onDismissRequest = onDismissRequest, properties = properties) {
            Surface(
                modifier = modifier,
                shape = shape,
                color = containerColor,
                contentColor = titleContentColor,
                tonalElevation = tonalElevation,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    if (icon != null) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                                icon()
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (title != null) {
                        CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                            title()
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (text != null) {
                        CompositionLocalProvider(LocalContentColor provides textContentColor) {
                            text()
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (dismissButton != null) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                                propagateMinConstraints = true,
                            ) {
                                CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                                    dismissButton()
                                }
                            }
                        }
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                            propagateMinConstraints = true,
                        ) {
                            CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.primary) {
                                confirmButton()
                            }
                        }
                    }
                }
            }
        }
    }
}
