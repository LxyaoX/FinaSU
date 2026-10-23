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
import androidx.compose.material3.ColorScheme
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
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Immutable
data class NavOrigin(
    val rect: Rect,
    val cornerRadius: Float,
    val color: Color,
    val themeSignature: Int,
    val bitmap: ImageBitmap? = null,
    val originId: Any? = null,
)

class NavOriginState {
    private var pendingOrigin: NavOrigin? = null
    private var pendingAt: Long = 0L
    private var predictiveKey = mutableStateOf<Any?>(null)
    private var predictiveFraction = mutableFloatStateOf(1f)
    private var lastPredictiveDrawnFraction = 1f
    private var commitFraction = mutableStateOf<Float?>(null)
    private var settleAnimationStarted = mutableStateOf(false)
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
            val drawnFraction = fraction.coerceIn(0f, 1f)
            predictiveKey.value = key
            predictiveFraction.floatValue = drawnFraction
            lastPredictiveDrawnFraction = drawnFraction
            commitFraction.value = null
            settleAnimationStarted.value = false
        }
        predictiveActive = active
    }

    internal fun predictiveFraction(key: Any?): Float? {
        if (!predictiveActive || predictiveKey.value != key) return null
        return predictiveFraction.floatValue.takeIf { it < 0.999f }
    }

    internal fun markPredictiveCommit() {
        if (predictiveKey.value != null) {
            commitFraction.value = lastPredictiveDrawnFraction.coerceIn(0f, 1f)
        }
    }

    internal fun committedFraction(key: Any?): Float? {
        if (predictiveKey.value != key) return null
        return commitFraction.value
    }

    internal fun lastPredictiveFraction(key: Any?): Float? {
        if (predictiveKey.value != key) return null
        return lastPredictiveDrawnFraction.takeIf { it < 0.999f }
    }

    internal fun predictiveDrawFraction(key: Any?): Float? {
        if (predictiveKey.value != key) return null
        if (predictiveActive) {
            return predictiveFraction.floatValue.takeIf { it < 0.999f }
        }
        if (settleAnimationStarted.value) return null
        return lastPredictiveDrawnFraction.takeIf { it < 0.999f }
    }

    internal fun beginPredictiveSettle(key: Any?) {
        if (predictiveKey.value == key) {
            settleAnimationStarted.value = true
        }
    }

    internal fun clearPredictive(key: Any?) {
        if (predictiveKey.value == key) {
            predictiveKey.value = null
            predictiveFraction.floatValue = 1f
            lastPredictiveDrawnFraction = 1f
            commitFraction.value = null
            settleAnimationStarted.value = false
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
    val colorScheme = MaterialTheme.colorScheme
    val resolvedColor = color ?: colorScheme.surfaceContainer
    val resolvedThemeSignature = navThemeSignature(colorScheme)
    val geometry = remember { NavOriginGeometry() }
    val currentRadius by rememberUpdatedState(with(density) { cornerRadius.toPx() })
    val currentColor by rememberUpdatedState(resolvedColor)
    val currentThemeSignature by rememberUpdatedState(resolvedThemeSignature)
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
                themeSignature = currentThemeSignature,
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
    themeSignature: Int,
    originId: Any? = null,
): NavOrigin = NavOrigin(
    rect = rect,
    cornerRadius = cornerRadius,
    color = color,
    themeSignature = themeSignature,
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
    val isCurrent = currentKey != null && key == navContentKey(currentKey)
    val direction = navigator.lastDirection
    val animateIn = direction == NavDirection.Push && isCurrent

    val cachedOrigin = state.origins[key]
    val predictiveFraction = state.predictiveFraction(key)
    val committedFraction = state.committedFraction(key)
    val popping = (direction == NavDirection.Pop && !isCurrent && cachedOrigin != null) ||
        (state.predictiveActive && predictiveFraction != null) ||
        committedFraction != null
    val origin = when {
        popping -> cachedOrigin
        animateIn -> cachedOrigin
        else -> null
    }
    val progress = remember {
        Animatable(if (animateIn) 0f else 1f)
    }
    LaunchedEffect(
        direction,
        isCurrent,
        animateIn,
        state.predictiveActive,
        predictiveFraction,
        committedFraction,
    ) {
        when {
            direction == NavDirection.Pop && !isCurrent ->
                {
                    if (progress.value <= 0.0001f) {
                        state.clearPredictive(key)
                        return@LaunchedEffect
                    }
                    val animation = containerTransformAnimation(
                        startFraction = state.committedFraction(key) ?: 1f,
                        targetFraction = 0f,
                    )
                    state.beginPredictiveSettle(key)
                    progress.snapTo(animation.startFraction)
                    try {
                        progress.animateTo(
                            animation.targetFraction,
                            tween(
                                durationMillis = animation.durationMillis,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    } finally {
                        state.clearPredictive(key)
                    }
                }

            state.predictiveActive && predictiveFraction != null -> Unit

            direction == NavDirection.Push && isCurrent &&
                animateIn && progress.value < 0.999f ->
                progress.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing))

            committedFraction != null ->
                progress.snapTo(committedFraction)

            else -> {
                val startFraction = state.lastPredictiveFraction(key) ?: return@LaunchedEffect
                val animation = containerTransformAnimation(
                    startFraction = startFraction,
                    targetFraction = 1f,
                )
                state.beginPredictiveSettle(key)
                progress.snapTo(animation.startFraction)
                try {
                    progress.animateTo(
                        animation.targetFraction,
                        tween(
                            durationMillis = animation.durationMillis,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                } finally {
                    state.clearPredictive(key)
                }
            }
        }
    }

    DisposableEffect(key) {
        onDispose {
            state.clearPredictive(key)
            if (shouldDiscardNavOrigin(key, navigator.backStack)) {
                state.removeOrigin(key)
            }
        }
    }

    val wallpaperActive = LocalFolkWallpaperTokens.current != null
    if (origin == null || wallpaperActive) {
        entry.Content()
        return
    }

    ContainerTransformLayout(
        origin = origin,
        progress = progress.asState(),
        popping = popping,
        gestureFraction = { state.predictiveDrawFraction(key) },
    ) {
        entry.Content()
    }
}

internal fun shouldDiscardNavOrigin(key: Any?, backStack: Collection<NavKey>): Boolean =
    key != null && backStack.none { navContentKey(it) == key }

internal fun navContentKey(key: NavKey): Any = key.toString() to key::class.toString()

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
    val colorScheme = MaterialTheme.colorScheme
    val surfaceColor = colorScheme.surfaceContainer
    val themeSignature = navThemeSignature(colorScheme)

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
                    currentThemeSignature = themeSignature,
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
    currentThemeSignature: Int,
    popping: Boolean,
) {
    val rect = interpolateContainerBounds(localOrigin, localContentBounds, fraction)
    if (rect.width <= 0f || rect.height <= 0f) return
    val radius = interpolateContainerCorner(origin.cornerRadius, fraction)
    val corner = CornerRadius(radius)
    val appearance = resolveContainerAppearance(
        originColor = origin.color,
        originThemeSignature = origin.themeSignature,
        currentThemeSignature = currentThemeSignature,
        surfaceColor = surfaceColor,
        popping = popping,
        hasSnapshot = origin.bitmap != null,
        fraction = fraction,
    )
    val path = Path().apply {
        addRoundRect(RoundRect(rect, corner))
    }

    drawRoundRect(
        color = appearance.fillColor,
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
        popSurfaceVeilAlpha(fraction) *
            if (appearance.snapshotEnabled) SnapshotWashAlpha else 1f
    } else {
        pushSurfaceVeilAlpha(fraction)
    }
    if (veilAlpha > 0f) {
        drawRoundRect(
            color = lerp(appearance.fillColor, surfaceColor, fraction).copy(alpha = veilAlpha),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = corner,
        )
    }

    val snapshot = origin.bitmap
    val snapshotAlpha = appearance.snapshotAlpha
    if (snapshotAlpha > 0f && snapshot != null) {
        clipPath(path) {
            drawImage(
                image = snapshot,
                dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                alpha = snapshotAlpha,
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

internal data class ContainerTransformAnimation(
    val startFraction: Float,
    val targetFraction: Float,
    val durationMillis: Int,
)

internal fun containerTransformAnimation(
    startFraction: Float,
    targetFraction: Float,
): ContainerTransformAnimation {
    val start = startFraction.coerceIn(0f, 1f)
    val target = targetFraction.coerceIn(0f, 1f)
    return ContainerTransformAnimation(
        startFraction = start,
        targetFraction = target,
        durationMillis = (abs(target - start) * ContainerTransformDurationMs)
            .roundToInt()
            .coerceAtLeast(1),
    )
}

internal data class ContainerAppearance(
    val fillColor: Color,
    val snapshotEnabled: Boolean,
    val snapshotAlpha: Float,
)

internal fun resolveContainerAppearance(
    originColor: Color,
    originThemeSignature: Int,
    currentThemeSignature: Int,
    surfaceColor: Color,
    popping: Boolean,
    hasSnapshot: Boolean,
    fraction: Float,
): ContainerAppearance {
    val themeMatches = originThemeSignature == currentThemeSignature
    val snapshotEnabled = themeMatches && popping && hasSnapshot
    return ContainerAppearance(
        fillColor = if (themeMatches) originColor else surfaceColor,
        snapshotEnabled = snapshotEnabled,
        snapshotAlpha = if (snapshotEnabled) popSnapshotAlpha(fraction) else 0f,
    )
}

internal fun navThemeSignature(colorScheme: ColorScheme): Int {
    var signature = 1
    signature = 31 * signature + colorScheme.background.hashCode()
    signature = 31 * signature + colorScheme.surface.hashCode()
    signature = 31 * signature + colorScheme.surfaceContainer.hashCode()
    signature = 31 * signature + colorScheme.surfaceBright.hashCode()
    signature = 31 * signature + colorScheme.surfaceDim.hashCode()
    return signature
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

internal fun popSnapshotAlpha(fraction: Float): Float {
    val snapshot = ((SnapshotRevealStart - fraction) / (SnapshotRevealStart - SnapshotRevealEnd))
        .coerceIn(0f, 1f)
    return FastOutSlowInEasing.transform(snapshot)
}

/** Keep a pressed-card capture small enough to hold for the pop animation. */
private const val MAX_CAPTURE_EDGE_PX = 1_280f
private const val MAX_CAPTURE_AREA_PX = 1_200_000f
private const val ContainerTransformDurationMs = 280f
private const val VeilRevealStart = 0.08f
private const val VeilRevealEnd = 0.42f
private const val SnapshotRevealStart = 0.42f
private const val SnapshotRevealEnd = 0.14f
private const val SnapshotWashAlpha = 0.18f
