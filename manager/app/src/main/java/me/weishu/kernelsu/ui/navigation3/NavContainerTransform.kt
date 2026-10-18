package me.weishu.kernelsu.ui.navigation3

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Immutable
data class NavOrigin(
    val rect: Rect,
    val cornerRadius: Float,
    val color: Color,
    val bitmap: ImageBitmap? = null,
)

class NavOriginState {
    private var pendingOrigin: NavOrigin? = null
    private var pendingAt: Long = 0L
    private var lastTouch: Offset? = null
    private var lastTouchAt: Long = 0L
    private var predictiveKey = mutableStateOf<Any?>(null)
    private var predictiveFraction = mutableFloatStateOf(1f)
    val origins = mutableStateMapOf<Any, NavOrigin>()

    var predictiveActive by mutableStateOf(false)
        private set

    internal fun markPending(origin: NavOrigin) {
        pendingOrigin = origin
        pendingAt = SystemClock.uptimeMillis()
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

    internal fun updateTouch(position: Offset) {
        lastTouch = position
        lastTouchAt = SystemClock.uptimeMillis()
    }

    internal fun touchOriginIfFresh(
        radius: Float,
        color: Color,
    ): NavOrigin? {
        val point = lastTouch ?: return null
        if (SystemClock.uptimeMillis() - lastTouchAt > ORIGIN_TIMEOUT_MS) return null
        return NavOrigin(
            rect = Rect(
                left = point.x - radius,
                top = point.y - radius,
                right = point.x + radius,
                bottom = point.y + radius,
            ),
            cornerRadius = radius,
            color = color,
        )
    }

    internal fun hasExpandableOrigin(): Boolean {
        val now = SystemClock.uptimeMillis()
        return (pendingOrigin != null && now - pendingAt <= ORIGIN_TIMEOUT_MS) ||
            (lastTouch != null && now - lastTouchAt <= ORIGIN_TIMEOUT_MS)
    }

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
): Modifier {
    val state = LocalNavOriginState.current
    val density = LocalDensity.current
    val view = LocalView.current
    val resolvedColor = color ?: MaterialTheme.colorScheme.surfaceContainer
    val geometry = remember { NavOriginGeometry() }
    val currentRadius by rememberUpdatedState(with(density) { cornerRadius.toPx() })
    val currentColor by rememberUpdatedState(resolvedColor)

    return this
        .onGloballyPositioned { coordinates ->
            geometry.root = coordinates.boundsInRoot()
            geometry.window = coordinates.boundsInWindow()
        }
        .pointerInput(state, view) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && geometry.root != Rect.Zero) {
                        state.markPending(
                            NavOrigin(
                                rect = geometry.root,
                                cornerRadius = currentRadius,
                                color = currentColor,
                            ),
                        )
                    }
                }
            }
        }
}

/**
 * Captures only the pressed card, never the window. The caller bounds are in window
 * coordinates, so the card is translated into [view]'s local coordinate space before drawing.
 * The bitmap is capped in size because it is held for the duration of the pop transition.
 */
private fun captureCardBitmap(
    view: android.view.View,
    windowBounds: Rect,
): ImageBitmap? {
    if (!windowBounds.isFinite || windowBounds.width <= 0f || windowBounds.height <= 0f) {
        Log.d("NavCapture", "invalid bounds=$windowBounds")
        return null
    }

    val viewLocation = IntArray(2)
    view.getLocationInWindow(viewLocation)
    val left = windowBounds.left - viewLocation[0]
    val top = windowBounds.top - viewLocation[1]

    val width = windowBounds.width
    val height = windowBounds.height
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
        Log.d("NavCapture", "captured bounds=$windowBounds size=${pixelWidth}x${pixelHeight}")
        bitmap.asImageBitmap()
    } catch (_: OutOfMemoryError) {
        bitmap.recycle()
        null
    } catch (_: RuntimeException) {
        bitmap.recycle()
        null
    }
}

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
                event.changes.firstOrNull()?.position?.let(state::updateTouch)
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
    val currentId = navigator.current()?.toString()
    val entryId = (key as? Pair<*, *>)?.first as? String
    val isCurrent = entryId != null && entryId == currentId
    val direction = navigator.lastDirection
    val animateIn = direction == NavDirection.Push && isCurrent

    val density = LocalDensity.current
    val wallpaperTokens = LocalFolkWallpaperTokens.current
    val fallbackColor = wallpaperTokens?.group?.fill ?: MaterialTheme.colorScheme.surfaceContainer
    val fallbackRadius = with(density) { 28.dp.toPx() }
    val cachedOrigin = state.origins[key]
    Log.d("NavCapture", "entry=$key dir=$direction current=$isCurrent cached=${cachedOrigin != null} bitmap=${cachedOrigin?.bitmap != null}")
    val enteringOrigin = remember(key, animateIn) {
        if (animateIn) {
            state.consumePendingIfFresh()
                ?: state.touchOriginIfFresh(fallbackRadius, fallbackColor)
        } else {
            null
        }
    }
    val popping = (direction == NavDirection.Pop && !isCurrent) ||
        (state.predictiveActive && isCurrent)
    val origin = when {
        direction == NavDirection.Push && isCurrent -> enteringOrigin ?: cachedOrigin
        popping -> cachedOrigin ?: enteringOrigin
        else -> cachedOrigin
    }

    LaunchedEffect(key, animateIn, enteringOrigin) {
        if (animateIn) {
            if (enteringOrigin != null) {
                state.putOrigin(key, enteringOrigin)
            } else {
                state.removeOrigin(key)
            }
        }
    }

    val progress = remember {
        Animatable(if (animateIn) 0f else 1f)
    }
    LaunchedEffect(direction, isCurrent, animateIn, state.predictiveActive) {
        when {
            state.predictiveActive -> Unit

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
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
                if (popping) {
                    drawPagePop(origin, fraction, localOrigin)
                    return@drawWithContent
                }

                val tailAlpha = (fraction / 0.08f).coerceIn(0f, 1f)
                if (tailAlpha <= 0f) {
                    return@drawWithContent
                }

                val rect = Rect(
                    left = localOrigin.left * (1f - fraction),
                    top = localOrigin.top * (1f - fraction),
                    right = localOrigin.right + (size.width - localOrigin.right) * fraction,
                    bottom = localOrigin.bottom + (size.height - localOrigin.bottom) * fraction,
                )
                val radius = origin.cornerRadius * (1f - fraction)
                val corner = CornerRadius(radius)
                val path = Path().apply {
                    addRoundRect(RoundRect(rect, corner))
                }

                val needsTailLayer = tailAlpha < 1f
                if (needsTailLayer) {
                    drawContext.canvas.saveLayer(
                        rect,
                        Paint().apply { alpha = tailAlpha },
                    )
                }

                val reveal = ((fraction - 0.18f) / 0.24f).coerceIn(0f, 1f)
                val backgroundAlpha = origin.color.alpha * (1f - reveal)
                if (backgroundAlpha > 0f) {
                    drawRoundRect(
                        color = origin.color.copy(alpha = backgroundAlpha),
                        topLeft = rect.topLeft,
                        size = rect.size,
                        cornerRadius = corner,
                    )
                }
                val contentAlpha = reveal
                if (contentAlpha > 0f) {
                    val needsContentLayer = contentAlpha < 1f
                    if (needsContentLayer) {
                        drawContext.canvas.saveLayer(
                            rect,
                            Paint().apply { alpha = contentAlpha },
                        )
                    }
                    clipPath(path) {
                        translate(left = rect.left, top = rect.top) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    if (needsContentLayer) {
                        drawContext.canvas.restore()
                    }
                }
                if (needsTailLayer) {
                    drawContext.canvas.restore()
                }
            },
    ) {
        content()
    }
}

private fun ContentDrawScope.drawPagePop(
    origin: NavOrigin,
    fraction: Float,
    localOrigin: Rect,
) {
    if (fraction <= 0f) return
    val rect = animatedCardRect(localOrigin, fraction)
    if (rect.width <= 0f || rect.height <= 0f) return
    val radius = origin.cornerRadius * (1f - fraction)
    val path = Path().apply {
        addRoundRect(RoundRect(rect, CornerRadius(radius)))
    }
    val alpha = fraction.coerceIn(0f, 1f)
    drawContext.canvas.saveLayer(
        Rect(offset = Offset.Zero, size = size),
        Paint().apply { this.alpha = alpha },
    )
    clipPath(path) {
        translate(left = rect.left, top = rect.top) {
            this@drawPagePop.drawContent()
        }
    }
    drawContext.canvas.restore()
}

private fun ContentDrawScope.animatedCardRect(
    localOrigin: Rect,
    fraction: Float,
): Rect {
    return Rect(
        left = localOrigin.left * (1f - fraction),
        top = localOrigin.top * (1f - fraction),
        right = localOrigin.right + (size.width - localOrigin.right) * fraction,
        bottom = localOrigin.bottom + (size.height - localOrigin.bottom) * fraction,
    )
}

/** Keep a pressed-card capture small enough to hold for the pop animation. */
private const val MAX_CAPTURE_EDGE_PX = 1_280f
private const val MAX_CAPTURE_AREA_PX = 1_200_000f
