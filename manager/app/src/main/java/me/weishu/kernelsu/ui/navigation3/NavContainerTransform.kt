package me.weishu.kernelsu.ui.navigation3

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens

@Immutable
data class NavOrigin(
    val rect: Rect,
    val cornerRadius: Float,
    val color: Color,
)

class NavOriginState {
    private var pendingOrigin: NavOrigin? = null
    private var pendingAt: Long = 0L
    private var lastTouch: Offset? = null
    private var lastTouchAt: Long = 0L
    val origins = mutableStateMapOf<Any, NavOrigin>()

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

    internal fun hasCachedOrigin(key: Any?): Boolean = key != null && origins.containsKey(key)

    private companion object {
        const val ORIGIN_TIMEOUT_MS = 1_200L
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
    val resolvedColor = color ?: MaterialTheme.colorScheme.surfaceContainer
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val currentBounds by rememberUpdatedState(bounds)
    val currentRadius by rememberUpdatedState(with(density) { cornerRadius.toPx() })
    val currentColor by rememberUpdatedState(resolvedColor)

    return this
        .onGloballyPositioned { coordinates ->
            bounds = coordinates.boundsInRoot()
        }
        .pointerInput(state) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && currentBounds != Rect.Zero) {
                        state.markPending(
                            NavOrigin(
                                rect = currentBounds,
                                cornerRadius = currentRadius,
                                color = currentColor,
                            ),
                        )
                    }
                }
            }
        }
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
    val enteringOrigin = remember(key, animateIn) {
        if (animateIn) {
            state.consumePendingIfFresh()
                ?: state.touchOriginIfFresh(fallbackRadius, fallbackColor)
        } else {
            null
        }
    }
    val origin = if (direction == NavDirection.Push && isCurrent) enteringOrigin else null

    LaunchedEffect(key, animateIn, enteringOrigin) {
        if (animateIn) {
            if (enteringOrigin != null) {
                state.origins[key] = enteringOrigin
            } else {
                state.origins.remove(key)
            }
        }
    }

    val progress = remember {
        Animatable(if (animateIn) 0f else 1f)
    }
    LaunchedEffect(direction, isCurrent) {
        when {
            direction == NavDirection.Push && isCurrent ->
                progress.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing))

            direction == NavDirection.Pop && !isCurrent ->
                progress.animateTo(0f, tween(durationMillis = 300, easing = FastOutSlowInEasing))

            else -> progress.snapTo(1f)
        }
    }

    if (origin == null) {
        entry.Content()
        return
    }

    ContainerTransformLayout(
        origin = origin,
        progress = progress.asState(),
        popping = false,
    ) {
        entry.Content()
    }
}

@Composable
private fun ContainerTransformLayout(
    origin: NavOrigin,
    progress: State<Float>,
    popping: Boolean,
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
                val fraction = progress.value.coerceIn(0f, 1f)
                if (fraction >= 0.999f) {
                    this@drawWithContent.drawContent()
                    return@drawWithContent
                }

                val containerAlpha = if (popping) fraction else 1f
                val tailAlpha = (fraction / 0.08f).coerceIn(0f, 1f) * containerAlpha
                if (tailAlpha <= 0f) {
                    return@drawWithContent
                }

                val localOrigin = Rect(
                    left = origin.rect.left - offsetInRoot.x,
                    top = origin.rect.top - offsetInRoot.y,
                    right = origin.rect.right - offsetInRoot.x,
                    bottom = origin.rect.bottom - offsetInRoot.y,
                )
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
                val backgroundAlpha = origin.color.alpha * (1f - reveal) * containerAlpha
                if (backgroundAlpha > 0f) {
                    drawRoundRect(
                        color = origin.color.copy(alpha = backgroundAlpha),
                        topLeft = rect.topLeft,
                        size = rect.size,
                        cornerRadius = corner,
                    )
                }
                val contentAlpha = reveal * containerAlpha
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
