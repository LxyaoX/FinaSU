package me.weishu.kernelsu.ui.navigation3

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.collect
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Immutable
data class NavOrigin(
    val rect: Rect,
    val cornerRadius: Float,
    val color: Color,
    val bitmap: ImageBitmap? = null,
    val originId: Any? = null,
)

class NavOriginState {
    private var pendingOrigin: NavOrigin? = null
    private var pendingAt: Long = 0L
    private var predictiveKey = mutableStateOf<Any?>(null)
    private var predictiveFraction = mutableFloatStateOf(1f)
    val origins = mutableStateMapOf<Any, NavOrigin>()

    var predictiveActive by mutableStateOf(false)
        private set

    internal fun markPending(origin: NavOrigin) {
        pendingOrigin = origin
        pendingAt = SystemClock.uptimeMillis()
    }

    fun recordOrigin(origin: NavOrigin) {
        markPending(origin)
    }

    internal fun clearPending() {
        pendingOrigin = null
    }

    internal fun consumePendingIfFresh(): NavOrigin? {
        val now = SystemClock.uptimeMillis()
        val origin = pendingOrigin
        pendingOrigin = null
        return origin?.takeIf { now - pendingAt <= ORIGIN_TIMEOUT_MS }
    }

    internal fun hasExpandableOrigin(): Boolean =
        pendingOrigin != null && SystemClock.uptimeMillis() - pendingAt <= ORIGIN_TIMEOUT_MS

    internal fun hasCachedBitmapOrigin(key: Any?): Boolean =
        key != null && origins[key]?.bitmap != null

    internal fun hasCachedOrigin(key: Any?): Boolean =
        key != null && origins.containsKey(key)

    internal fun putOrigin(key: Any, origin: NavOrigin) {
        origins[key] = origin
        while (origins.size > MAX_CACHED_ORIGINS) {
            val oldest = origins.keys.firstOrNull { it != key } ?: break
            origins.remove(oldest)
        }
    }

    internal fun removeOrigin(key: Any?) {
        if (key != null) {
            origins.remove(key)
        }
    }

    internal fun updatePredictive(active: Boolean, key: Any?, fraction: Float) {
        if (active) {
            predictiveKey.value = key
            predictiveFraction.floatValue = fraction.coerceIn(0f, 1f)
        }
        predictiveActive = active
    }

    internal fun predictiveFraction(key: Any?): Float? {
        if (predictiveKey.value != key) return null
        return predictiveFraction.floatValue.takeIf { it < 0.999f }
    }

    internal fun clearPredictive(key: Any?) {
        if (predictiveKey.value == key) {
            predictiveKey.value = null
            predictiveFraction.floatValue = 1f
        }
    }

    private companion object {
        const val ORIGIN_TIMEOUT_MS = 1_200L
        const val MAX_CACHED_ORIGINS = 4
    }
}

val LocalNavOriginState = staticCompositionLocalOf<NavOriginState> {
    NavOriginState()
}

@Composable
fun rememberNavOriginState(): NavOriginState = remember { NavOriginState() }

@Composable
fun rememberNavContainerTransformDecorator(
    state: NavOriginState,
): NavEntryDecorator<NavKey> = remember(state) {
    NavEntryDecorator<NavKey> { entry ->
        NavContainerTransformEntry(state, entry)
    }
}

@Composable
fun Modifier.navOrigin(
    cornerRadius: Dp = 22.dp,
    color: Color? = null,
    captureBitmap: Boolean = false,
    originId: Any? = null,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val state = LocalNavOriginState.current
    val density = LocalDensity.current
    val view = LocalView.current
    val resolvedColor = color ?: MaterialTheme.colorScheme.surfaceContainer
    val geometry = remember { NavOriginGeometry() }
    val currentRadius by rememberUpdatedState(with(density) { cornerRadius.toPx() })
    val currentColor by rememberUpdatedState(resolvedColor)
    val currentCapture by rememberUpdatedState(captureBitmap)
    val currentId by rememberUpdatedState(originId)
    val pressSource = interactionSource ?: remember { MutableInteractionSource() }

    fun markOrigin() {
        if (geometry.root == Rect.Zero) return
        state.markPending(
            NavOrigin(
                rect = geometry.root,
                cornerRadius = currentRadius,
                color = currentColor,
                bitmap = if (currentCapture) {
                    captureCardBitmap(view, geometry.root)
                } else {
                    null
                },
                originId = currentId,
            ),
        )
    }

    LaunchedEffect(pressSource, interactionSource) {
        if (interactionSource == null) return@LaunchedEffect
        pressSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                markOrigin()
            }
        }
    }

    return this
        .onGloballyPositioned { coordinates ->
            geometry.root = coordinates.boundsInRoot()
            geometry.window = coordinates.boundsInWindow()
        }
        .then(
            if (interactionSource == null) {
                Modifier.pointerInput(state, view) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            if (event.type == PointerEventType.Press) {
                                markOrigin()
                            }
                        }
                    }
                }
            } else {
                Modifier
            }
        )
}

/**
 * Captures only the pressed card, never the window. The caller bounds are in window
 * coordinates, so the card is translated into [view]'s local coordinate space before drawing.
 * The bitmap is capped in size because it is held for the duration of the pop transition.
 */
private fun captureCardBitmap(
    view: android.view.View,
    bounds: Rect,
): ImageBitmap? {
    if (!bounds.isFinite || bounds.width <= 0f || bounds.height <= 0f) {
        return null
    }

    val left = bounds.left
    val top = bounds.top

    val width = bounds.width
    val height = bounds.height
    val edgeScale = MAX_CAPTURE_EDGE_PX / maxOf(width, height)
    val areaScale = sqrt(MAX_CAPTURE_AREA_PX / (width * height))
    val captureScale = min(1f, min(edgeScale, areaScale))
    val pixelWidth = (width * captureScale).roundToInt().coerceAtLeast(1)
    val pixelHeight = (height * captureScale).roundToInt().coerceAtLeast(1)

    val bitmap = try {
        Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888)
    } catch (_: OutOfMemoryError) {
        return null
    } catch (_: RuntimeException) {
        return null
    }

    return try {
        val canvas = Canvas(bitmap)
        canvas.translate(-left * captureScale, -top * captureScale)
        canvas.scale(captureScale, captureScale)
        view.draw(canvas)
        bitmap.asImageBitmap()
    } catch (_: OutOfMemoryError) {
        bitmap.recycle()
        null
    } catch (_: RuntimeException) {
        bitmap.recycle()
        null
    }
}

fun captureNavOrigin(
    view: android.view.View,
    rect: Rect,
    cornerRadius: Float,
    color: Color,
    originId: Any? = null,
): NavOrigin = NavOrigin(
    rect = rect,
    cornerRadius = cornerRadius,
    color = color,
    bitmap = captureCardBitmap(view, rect),
    originId = originId,
)

private class NavOriginGeometry {
    var root: Rect = Rect.Zero
    var window: Rect = Rect.Zero
}

fun Modifier.trackNavPointer(state: NavOriginState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press) {
                state.clearPending()
            }
        }
    }
}

@Composable
private fun NavContainerTransformEntry(
    state: NavOriginState,
    entry: NavEntry<NavKey>,
) {
    val navigator = LocalNavigator.current
    val key = entry.contentKey
    val currentKey = navigator.current()
    val isCurrent = currentKey != null &&
        key == (currentKey.toString() to currentKey::class.toString())
    val direction = navigator.lastDirection
    val animateIn = direction == NavDirection.Push && isCurrent

    val cachedOrigin = state.origins[key]
    val predictiveFraction = state.predictiveFraction(key)
    val popping = (direction == NavDirection.Pop && !isCurrent && cachedOrigin != null) ||
        (state.predictiveActive && predictiveFraction != null)
    val origin = when {
        popping -> cachedOrigin
        animateIn -> cachedOrigin
        else -> null
    }
    val progress = remember {
        Animatable(if (animateIn) 0f else 1f)
    }
    LaunchedEffect(direction, isCurrent, animateIn, state.predictiveActive, predictiveFraction) {
        when {
            state.predictiveActive && predictiveFraction != null -> Unit

            direction == NavDirection.Push && isCurrent &&
                animateIn && progress.value < 0.999f ->
                progress.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing))

            direction == NavDirection.Pop && !isCurrent ->
                {
                    val startFraction = state.predictiveFraction(key) ?: 1f
                    progress.snapTo(startFraction)
                    state.clearPredictive(key)
                    progress.animateTo(
                        0f,
                        tween(
                            durationMillis = (startFraction * 280f).toInt().coerceAtLeast(1),
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }

            else -> {
                // A completed predictive-back commit pops the stack in the same frame the
                // gesture ends. Let that commit change direction before treating this as a
                // cancelled gesture that should spring back open.
                withFrameNanos { }
                val predictiveFraction = state.predictiveFraction(key)
                if (predictiveFraction != null) {
                    progress.snapTo(predictiveFraction)
                    state.clearPredictive(key)
                    progress.animateTo(
                        1f,
                        tween(
                            durationMillis = ((1f - predictiveFraction) * 280f)
                                .toInt()
                                .coerceAtLeast(1),
                            easing = FastOutSlowInEasing,
                        ),
                    )
                } else {
                    progress.snapTo(1f)
                }
            }
        }
    }

    DisposableEffect(key) {
        onDispose {
            state.clearPredictive(key)
            state.removeOrigin(key)
        }
    }

    if (origin == null) {
        entry.Content()
        return
    }

    ContainerTransformLayout(
        origin = origin,
        progress = progress.asState(),
        popping = popping,
        gestureFraction = { state.predictiveFraction(key) },
    ) {
        entry.Content()
    }
}

@Composable
private fun ContainerTransformLayout(
    origin: NavOrigin,
    progress: State<Float>,
    popping: Boolean,
    gestureFraction: () -> Float?,
    content: @Composable () -> Unit,
) {
    var offsetInRoot by remember { mutableStateOf(Offset.Zero) }
    var contentBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                contentBoundsInRoot = coordinates.boundsInRoot()
                offsetInRoot = coordinates.localToRoot(Offset.Zero)
            }
            .drawWithContent {
                val fraction = (gestureFraction() ?: progress.value).coerceIn(0f, 1f)
                if (fraction >= 0.999f) {
                    this@drawWithContent.drawContent()
                    return@drawWithContent
                }

                val localOrigin = Rect(
                    left = origin.rect.left - offsetInRoot.x,
                    top = origin.rect.top - offsetInRoot.y,
                    right = origin.rect.right - offsetInRoot.x,
                    bottom = origin.rect.bottom - offsetInRoot.y,
                )
                val localContentBounds = contentBoundsInRoot
                    .takeIf { it.isFinite && !it.isEmpty }
                    ?.let {
                        Rect(
                            left = it.left - offsetInRoot.x,
                            top = it.top - offsetInRoot.y,
                            right = it.right - offsetInRoot.x,
                            bottom = it.bottom - offsetInRoot.y,
                        )
                    }
                    ?: Rect(Offset.Zero, size)

                drawContainerTransform(
                    origin = origin,
                    fraction = fraction,
                    localOrigin = localOrigin,
                    localContentBounds = localContentBounds,
                    surfaceColor = surfaceColor,
                    popping = popping,
                )
            },
    ) {
        content()
    }
}

/**
 * Reveals the settled page through a clipped, moving container. An opaque surface veil replaces
 * layered content alpha, so the transform stays on the normal draw path without `saveLayer`.
 */
private fun ContentDrawScope.drawContainerTransform(
    origin: NavOrigin,
    fraction: Float,
    localOrigin: Rect,
    localContentBounds: Rect,
    surfaceColor: Color,
    popping: Boolean,
) {
    val rect = interpolateContainerBounds(localOrigin, localContentBounds, fraction)
    if (rect.width <= 0f || rect.height <= 0f) return
    val radius = interpolateContainerCorner(origin.cornerRadius, fraction)
    val corner = CornerRadius(radius)
    val path = Path().apply {
        addRoundRect(RoundRect(rect, corner))
    }

    drawRoundRect(
        color = origin.color,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = corner,
    )

    clipPath(path) {
        translate(
            left = rect.left - localContentBounds.left,
            top = rect.top - localContentBounds.top,
        ) {
            this@drawContainerTransform.drawContent()
        }
    }

    val veilAlpha = if (popping) {
        popSurfaceVeilAlpha(fraction)
    } else {
        pushSurfaceVeilAlpha(fraction)
    }
    if (veilAlpha > 0f) {
        drawRoundRect(
            color = lerp(origin.color, surfaceColor, fraction).copy(alpha = veilAlpha),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = corner,
        )
    }

    val bitmap = origin.bitmap
    if (popping && bitmap != null && fraction <= BitmapRevealEnd) {
        val dstWidth = rect.width
        val dstHeight = if (bitmap.width > 0) {
            dstWidth * bitmap.height / bitmap.width
        } else {
            rect.height
        }
        val bitmapAlpha = ((BitmapRevealEnd - fraction) / BitmapRevealEnd).coerceIn(0f, 1f)
        clipPath(path) {
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                dstSize = IntSize(dstWidth.roundToInt(), dstHeight.roundToInt()),
                alpha = bitmapAlpha,
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

internal fun interpolateContainerBounds(
    origin: Rect,
    destination: Rect,
    fraction: Float,
): Rect {
    val progress = fraction.coerceIn(0f, 1f)
    return Rect(
        left = origin.left + (destination.left - origin.left) * progress,
        top = origin.top + (destination.top - origin.top) * progress,
        right = origin.right + (destination.right - origin.right) * progress,
        bottom = origin.bottom + (destination.bottom - origin.bottom) * progress,
    )
}

internal fun interpolateContainerCorner(cornerRadius: Float, fraction: Float): Float =
    cornerRadius * (1f - fraction.coerceIn(0f, 1f))

internal fun pushSurfaceVeilAlpha(fraction: Float): Float {
    val reveal = ((fraction - VeilRevealStart) / (VeilRevealEnd - VeilRevealStart))
        .coerceIn(0f, 1f)
    return 1f - FastOutSlowInEasing.transform(reveal)
}

internal fun popSurfaceVeilAlpha(fraction: Float): Float {
    val conceal = ((VeilRevealEnd - fraction) / (VeilRevealEnd - VeilRevealStart))
        .coerceIn(0f, 1f)
    return FastOutSlowInEasing.transform(conceal)
}

/** Keep a pressed-card capture small enough to hold for the pop animation. */
private const val MAX_CAPTURE_EDGE_PX = 1_280f
private const val MAX_CAPTURE_AREA_PX = 1_200_000f
private const val BitmapRevealEnd = 0.18f
private const val VeilRevealStart = 0.08f
private const val VeilRevealEnd = 0.42f
