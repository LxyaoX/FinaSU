package me.weishu.kernelsu.ui.component.material

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults

/**
 * A rounded card.
 *
 * It is a miuix card whatever it is filled with - neutral surface or a semantic container - so the
 * whole app shares one card shape and one press behaviour.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    contentColor: Color = contentColorFor(containerColor),
    shape: Shape = MaterialTheme.shapes.large,
    /**
     * Corner radius for the miuix card. miuix takes a radius rather than a [Shape], so this has to be
     * passed alongside [shape]; it should be the radius that shape was built with (24dp for
     * `MaterialTheme.shapes.large`, 20dp for [me.weishu.kernelsu.ui.theme.FolkShape.Corner20]).
     */
    cornerRadius: Dp = 24.dp,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    navOriginRadius: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val originColor = containerColor.takeIf { it.alpha > 0f }
        ?: MaterialTheme.colorScheme.surfaceContainer
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

    // One card component for every fill: miuix draws the neutral surface and the semantic
    // containers alike, so a warning card and a settings group agree on shape and press behaviour
    // instead of one of them being a Material card among miuix ones.
    //
    // The neutral fill is miuix's own card colour, not Material's surfaceBright: a SegmentedColumn
    // group is a bare miuix card, and the settings hub puts one of these panels directly above one
    // of those. Asking Material for the fill made the two panels a shade apart in the same column,
    // which read as a rendering fault rather than as grouping.
    val fill = if (containerColor == MaterialTheme.colorScheme.surfaceBright) {
        CardDefaults.defaultColors()
    } else {
        CardDefaults.defaultColors(color = containerColor, contentColor = contentColor)
    }
    MiuixCard(
        modifier = modifier.then(originModifier).then(clickModifier),
        cornerRadius = cornerRadius,
        colors = fill,
    ) {
        // miuix's card publishes miuix's own content colour; Material's `Text`/`Icon` read
        // Material's local, so the card's colour is published again for them.
        CompositionLocalProvider(LocalContentColor provides fill.contentColor) {
            content()
        }
    }
}
