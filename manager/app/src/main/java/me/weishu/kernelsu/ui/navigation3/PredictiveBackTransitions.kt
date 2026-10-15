package me.weishu.kernelsu.ui.navigation3

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlin.math.roundToInt

/**
 * The three androidx.navigation3 transition slots for one predictive back style.
 *
 * The display hands the same slot a plain pop and a gesture driven pop, so [popTransitionSpec] and
 * [predictivePopTransitionSpec] are built from the same [ContentTransform]: the gesture seeks that
 * transform and, on release, the same transform is played out to its end. A different pair would
 * make the page jump the moment the finger lifts.
 */
@Immutable
class PredictiveBackTransitions<T : Any>(
    val transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform,
    val popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform,
    val predictivePopTransitionSpec:
        AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform,
    /**
     * Whether the gesture has to be intercepted above the display. Only the "no preview" style
     * needs it: selecting it means nothing may move before the gesture completes.
     */
    val interceptsPredictiveBack: Boolean = false,
)

/** Matches the "programmatic" curve BakaSU uses for its predictive back styles. */
private val PredictiveBackEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private const val SCALE_DURATION_MILLIS = 250
private const val AOSP_DURATION_MILLIS = 450
private const val CLASSIC_DURATION_MILLIS = 200

/** Scale the leaving page settles on, so it reads as a sheet sliding over the page below. */
private const val SCALE_LEAVING_TARGET = 0.85f

/** Scale the cross-activity style keeps on both pages, as AOSP does with its window insets. */
private const val AOSP_MIN_SCALE = 0.9f

/** Scale the classic style settles on while it fades out. */
private const val CLASSIC_LEAVING_TARGET = 0.9f

/** How much of the width the revealed page drifts by in the cross-activity style. */
private const val DRIFT_DIVISOR = 12f

/**
 * Builds the navigation3 transitions for [animation]. [exitDirection] only resolves which way a
 * sliding page travels, and is ignored by the styles that never slide sideways.
 */
@Composable
fun <T : Any> rememberPredictiveBackTransitions(
    animation: PredictiveBackAnimation,
    exitDirection: PredictiveBackExitDirection,
): PredictiveBackTransitions<T> {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(animation, exitDirection, rtl) {
        predictiveBackTransitions(animation, exitDirection, rtl)
    }
}

private fun <T : Any> predictiveBackTransitions(
    animation: PredictiveBackAnimation,
    exitDirection: PredictiveBackExitDirection,
    rtl: Boolean,
): PredictiveBackTransitions<T> = when (animation) {
    PredictiveBackAnimation.Default -> PredictiveBackTransitions(
        transitionSpec = defaultTransitionSpec(),
        popTransitionSpec = defaultPopTransitionSpec(),
        predictivePopTransitionSpec = defaultPredictivePopTransitionSpec(),
    )

    PredictiveBackAnimation.None -> {
        val ordinaryPop = defaultPopTransitionSpec<T>()
        PredictiveBackTransitions(
            // Unused while the gesture is intercepted; kept as the ordinary pop so a display that
            // is asked for a preview anyway still animates instead of jumping out of the stack.
            transitionSpec = defaultTransitionSpec(),
            popTransitionSpec = ordinaryPop,
            predictivePopTransitionSpec = { ordinaryPop.invoke(this) },
            interceptsPredictiveBack = PredictiveBackAnimation.None.previewsNothing,
        )
    }

    PredictiveBackAnimation.Scale -> slidingTransitions(exitDirection, rtl) { sign ->
        ContentTransform(
            // The page below stays where it is; the sheet slides over it.
            targetContentEnter = EnterTransition.None,
            initialContentExit = slideOutHorizontally(
                animationSpec = tween(SCALE_DURATION_MILLIS, easing = PredictiveBackEasing),
                targetOffsetX = { (it * sign).roundToInt() },
            ) + scaleOut(
                animationSpec = tween(SCALE_DURATION_MILLIS, easing = PredictiveBackEasing),
                targetScale = SCALE_LEAVING_TARGET,
                transformOrigin = leavingOrigin(sign),
            ),
        )
    }

    PredictiveBackAnimation.Aosp -> slidingTransitions(exitDirection, rtl) { sign ->
        ContentTransform(
            targetContentEnter = scaleIn(
                animationSpec = tween(AOSP_DURATION_MILLIS, easing = PredictiveBackEasing),
                initialScale = AOSP_MIN_SCALE,
            ) + slideInHorizontally(
                animationSpec = tween(AOSP_DURATION_MILLIS, easing = PredictiveBackEasing),
                initialOffsetX = { (-sign * it / DRIFT_DIVISOR).roundToInt() },
            ),
            initialContentExit = slideOutHorizontally(
                animationSpec = tween(AOSP_DURATION_MILLIS, easing = PredictiveBackEasing),
                targetOffsetX = { (it * sign).roundToInt() },
            ) + scaleOut(
                animationSpec = tween(AOSP_DURATION_MILLIS, easing = PredictiveBackEasing),
                targetScale = AOSP_MIN_SCALE,
                transformOrigin = leavingOrigin(sign),
            ),
        )
    }

    PredictiveBackAnimation.KernelSUClassic -> {
        // The classic look has no sideways exit; the revealed page is the one that travels.
        val revealSign = if (rtl) 1f else -1f
        val transform = ContentTransform(
            targetContentEnter = slideInHorizontally(
                animationSpec = tween(CLASSIC_DURATION_MILLIS, easing = PredictiveBackEasing),
                initialOffsetX = { (revealSign * it).roundToInt() },
            ),
            initialContentExit = scaleOut(
                animationSpec = tween(CLASSIC_DURATION_MILLIS, easing = PredictiveBackEasing),
                targetScale = CLASSIC_LEAVING_TARGET,
                transformOrigin = TransformOrigin.Center,
            ) + fadeOut(
                animationSpec = tween(CLASSIC_DURATION_MILLIS, easing = PredictiveBackEasing),
            ),
        )
        PredictiveBackTransitions(
            transitionSpec = defaultTransitionSpec(),
            popTransitionSpec = { transform },
            predictivePopTransitionSpec = { transform },
        )
    }
}

/**
 * Builds the leaving/revealed pair for the styles that only differ by the direction the page
 * leaves in, once per direction so the specs handed to the display stay stable across pops.
 */
private fun <T : Any> slidingTransitions(
    exitDirection: PredictiveBackExitDirection,
    rtl: Boolean,
    transformFor: (sign: Float) -> ContentTransform,
): PredictiveBackTransitions<T> {
    val rightward = transformFor(1f)
    val leftward = transformFor(-1f)
    fun pick(swipeEdge: Int?): ContentTransform =
        if (exitSign(exitDirection, swipeEdge, rtl) > 0f) rightward else leftward

    return PredictiveBackTransitions(
        transitionSpec = defaultTransitionSpec(),
        popTransitionSpec = { pick(null) },
        predictivePopTransitionSpec = { swipeEdge -> pick(swipeEdge) },
    )
}

/**
 * Sign the leaving page travels in: positive to the right, negative to the left. A drag from the
 * left edge pushes the page to the right, and a plain pop falls back to the layout direction.
 */
internal fun exitSign(
    exitDirection: PredictiveBackExitDirection,
    swipeEdge: Int?,
    rtl: Boolean,
): Float = when (exitDirection) {
    PredictiveBackExitDirection.AlwaysRight -> 1f
    PredictiveBackExitDirection.AlwaysLeft -> -1f
    PredictiveBackExitDirection.FollowGesture -> when (swipeEdge) {
        NavigationEvent.EDGE_LEFT -> 1f
        NavigationEvent.EDGE_RIGHT -> -1f
        else -> if (rtl) -1f else 1f
    }
}

/** Keeps the shrunk page pinned against the edge it leaves by, like a sheet sliding off. */
private fun leavingOrigin(sign: Float): TransformOrigin =
    TransformOrigin(pivotFractionX = if (sign > 0f) 0.8f else 0.2f, pivotFractionY = 0.5f)

/**
 * Registers an enabled back handler *inside* the display, so a back gesture is completed here
 * instead of driving androidx.navigation3's predictive seek. The display then animates the pop
 * that follows the back stack change like any other pop, which is what the "no preview" style
 * asks for.
 */
@Composable
fun PredictiveBackGestureInterceptor(
    enabled: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val latestOnBack by rememberUpdatedState(onBack)
    val state = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = state,
        isBackEnabled = enabled,
        onBackCompleted = { latestOnBack() },
    )
    content()
}

/**
 * Entry decorator that wraps every rendered entry in [PredictiveBackGestureInterceptor] when
 * [interceptPredictiveBack] is set. The wrapper has to sit inside the display for its back handler
 * to outrank the display's own one, and decorating keeps that inside the entry content rather than
 * repeating the wrapper around every `entry<...>` in the route table.
 *
 * [isBackEnabled] is read while the entry content composes, so it follows the back stack without
 * the caller having to hand in a fresh decorator on every change.
 */
@Composable
fun <T : Any> rememberPredictiveBackEntryDecorator(
    interceptPredictiveBack: Boolean,
    isBackEnabled: () -> Boolean,
    onBack: () -> Unit,
): NavEntryDecorator<T> {
    val latestIsBackEnabled by rememberUpdatedState(isBackEnabled)
    val latestOnBack by rememberUpdatedState(onBack)
    return remember(interceptPredictiveBack) {
        NavEntryDecorator<T> { entry: NavEntry<T> ->
            if (interceptPredictiveBack) {
                PredictiveBackGestureInterceptor(
                    enabled = latestIsBackEnabled(),
                    onBack = { latestOnBack() },
                ) {
                    entry.Content()
                }
            } else {
                entry.Content()
            }
        }
    }
}
