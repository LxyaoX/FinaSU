package me.weishu.kernelsu.ui.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.weishu.kernelsu.ui.theme.FolkMotion

/**
 * Named transitions, so the same kind of change reads the same wherever it happens. The timings
 * themselves live in [FolkMotion]; what is kept here is how they are combined.
 */
object FolkTransitions {

    /** How far a control shrinks while it leaves its slot, and grows back on the way in. */
    private const val SlotScale = 0.7f

    /**
     * A control taking up, or giving back, room in a row of controls. The slot resizes so the
     * controls beside it slide over instead of jumping, while the control itself scales and fades.
     */
    val SlotEnter: EnterTransition = fadeIn(FolkMotion.SlotFade) +
        scaleIn(initialScale = SlotScale, animationSpec = FolkMotion.SlotScale) +
        expandHorizontally(animationSpec = FolkMotion.SlotSize, expandFrom = Alignment.End)

    val SlotExit: ExitTransition = fadeOut(FolkMotion.SlotFade) +
        scaleOut(targetScale = SlotScale, animationSpec = FolkMotion.SlotScale) +
        shrinkHorizontally(animationSpec = FolkMotion.SlotSize, shrinkTowards = Alignment.End)
}

/**
 * Shows [content] as a slot that opens and closes around it, rather than appearing and vanishing in
 * place. Meant for a control that comes and goes among its neighbours — an app bar action, a chip, a
 * trailing button.
 */
@Composable
fun FolkAnimatedSlot(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = FolkTransitions.SlotEnter,
    exit: ExitTransition = FolkTransitions.SlotExit,
    label: String = "FolkAnimatedSlot",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        label = label,
        content = content,
    )
}
