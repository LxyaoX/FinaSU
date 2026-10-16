package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import me.weishu.kernelsu.ui.component.liquid.lens
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Ported from the upstream KernelSU floating bar and compose-miuix-ui
// — https://github.com/compose-miuix-ui/miuix (Apache 2.0).
val IosGlassHighlight: Highlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

@Immutable
internal data class LiquidIndicatorGlass(
    val backdrop: Backdrop,
    val highlight: Highlight,
    /** Material over the selected item's lens, taken from the theme so it reads in both polarities. */
    val film: Color,
    /** Shadow under labels and icons. */
    val contentShadow: Shadow,
)

/** How much of the selected item's own material sits over its lens. */
internal const val LIQUID_FILM_ALPHA = 0.85f

internal fun Modifier.liquidIndicatorGlass(
    glass: LiquidIndicatorGlass,
    shape: Shape,
    lensPx: Float,
): Modifier = drawBackdrop(
    backdrop = glass.backdrop,
    shape = { shape },
    effects = {
        lens(
            refractionHeight = lensPx,
            refractionAmount = lensPx,
            depthEffect = true,
            chromaticAberration = 0.5f,
        )
    },
    highlight = { glass.highlight },
)

// Mirrors miuix-blur's HighlightStyle.LIGHT_REF — keep in sync.
private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f

private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f

/** Quantising keeps a jittering gravity reading from flickering the light. */
private const val GRAVITY_ANGLE_STEP_RAD = (3.0 * PI / 180.0).toFloat()

private val FLAT_ANGLE = (-PI / 2).toFloat()

@Composable
private fun rememberQuantizedGravityAngle(): State<Float> {
    val tiltState = rememberDeviceTilt()
    return remember(tiltState) {
        derivedStateOf {
            val tilt = tiltState.value
            val magnitudeSquared = tilt.gravityX * tilt.gravityX + tilt.gravityY * tilt.gravityY
            if (magnitudeSquared > GRAVITY_DIR_THRESHOLD_SQ) {
                (atan2(tilt.gravityY, tilt.gravityX) / GRAVITY_ANGLE_STEP_RAD)
                    .roundToInt() * GRAVITY_ANGLE_STEP_RAD
            } else {
                FLAT_ANGLE
            }
        }
    }
}

/** The base highlight with its primary light rotated onto the gravity direction. */
@Composable
fun rememberGravityRotatedHighlight(
    base: Highlight,
    extraDegrees: Float = 0f,
): State<Highlight> {
    val baseStyle = base.style as BloomStroke
    val angle = rememberQuantizedGravityAngle()
    return remember(angle, base, extraDegrees) {
        derivedStateOf {
            val basePrimary = baseStyle.primaryLight
            val rad = angle.value + (extraDegrees * PI / 180.0).toFloat()
            base.copy(
                style = baseStyle.copy(
                    primaryLight = basePrimary.copy(
                        position = LightPosition(
                            x = LIGHT_REF_X + cos(rad),
                            y = LIGHT_REF_Y + sin(rad),
                            z = basePrimary.position.z,
                        ),
                    ),
                ),
            )
        }
    }
}
