package me.weishu.kernelsu.ui.component.material

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.navigation3.navOrigin
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

/**
 * A rounded card that participates in the unified transparency layering controller.
 *
 * When wallpaper mode is off this is a plain [Card] with [containerColor]. When it is on, the card is
 * drawn through [FolkWallpaperSurface] with [wallpaperRole], so its fill shares one alpha with every
 * other panel instead of staying opaque.
 *
 * The default is derived from the fill: a plain neutral surface ([MaterialTheme.colorScheme.surfaceBright])
 * joins the wallpaper layering, while a card that carries a semantic container colour
 * (errorContainer/secondaryContainer/...) keeps that colour untouched. Pass `wallpaperRole` explicitly
 * to override (for example `null` for the theme preview miniature).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    contentColor: Color = contentColorFor(containerColor),
    shape: Shape = MaterialTheme.shapes.large,
    wallpaperRole: WallpaperSurfaceRole? =
        if (containerColor == MaterialTheme.colorScheme.surfaceBright) WallpaperSurfaceRole.Group else null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    navOriginRadius: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val wallpaperTokens = LocalFolkWallpaperTokens.current
    val originColor = if (wallpaperTokens != null && wallpaperRole != null) {
        wallpaperTokens.material(wallpaperRole).fill
    } else {
        containerColor.takeIf { it.alpha > 0f }
            ?: MaterialTheme.colorScheme.surfaceContainer
    }
    val originModifier = if (onClick != null) {
        Modifier.navOrigin(
            cornerRadius = navOriginRadius,
            color = originColor,
            interactionSource = interactionSource,
        )
    } else {
        Modifier
    }

    val clickModifier = when {
        onLongClick != null -> Modifier
            .clip(shape)
            .folkPressScale(interactionSource, enabled)
            .combinedClickable(
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick?.invoke()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onLongClick()
                },
                interactionSource = interactionSource,
                indication = null,
            )

        onClick != null -> Modifier
            .clip(shape)
            .folkPressScale(interactionSource, enabled)
            .combinedClickable(
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                interactionSource = interactionSource,
                indication = null,
            )

        else -> Modifier
    }

    if (wallpaperRole != null) {
        FolkWallpaperSurface(
            role = wallpaperRole,
            shape = shape,
            modifier = modifier.then(originModifier).then(clickModifier),
            fallbackColor = containerColor,
            fallbackContentColor = contentColor,
        ) {
            content()
        }
    } else if (containerColor == MaterialTheme.colorScheme.surfaceBright) {
        // The neutral card is the one miuix should draw: it is what the home and settings surfaces
        // are made of. A card that carries a semantic colour (errorContainer, secondaryContainer…)
        // keeps Material's tinted card, because miuix would paint it as a plain surface.
        MiuixCard(modifier = modifier.then(originModifier).then(clickModifier)) {
            // miuix's card publishes miuix's own content colour; Material's `Text`/`Icon` read
            // Material's local, so the card's colour is published again for them.
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    } else {
        MaterialCard(
            modifier = modifier.then(originModifier).then(clickModifier),
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            shape = shape,
        ) {
            content()
        }
    }
}
