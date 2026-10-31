package me.weishu.kernelsu.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import me.weishu.kernelsu.ui.webui.webview.asset.MonetColorsProvider

// Default dark ripple alpha (~10% pressed) is nearly invisible on near-black surfaces, so boost
// it for a clear press feedback at night. Light mode keeps Compose's default.
private val DarkRippleAlpha = RippleAlpha(
    draggedAlpha = 0.32f,
    focusedAlpha = 0.24f,
    hoveredAlpha = 0.16f,
    pressedAlpha = 0.24f,
)

@Composable
fun MaterialKernelSUTheme(
    appSettings: AppSettings,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDarkTheme)
    val amoledMode = appSettings.colorMode.isAmoled
    val dynamicColor = appSettings.useSystemDynamicColor

    val baseSeed = if (dynamicColor) Color.Unspecified else Color(appSettings.keyColor)

    val colorScheme = rememberKernelSUColorScheme(
        seedColor = baseSeed,
        isDark = darkTheme,
        isAmoled = amoledMode,
        paletteStyle = appSettings.paletteStyle,
        contrastLevel = appSettings.contrastLevel,
        generationMode = appSettings.colorGenerationMode,
    )

    // Animate the scheme once, then derive both the MaterialTheme palette and the miuix theme from
    // the same animated instance. Otherwise a panel's fill comes from the target scheme while the
    // text inside it is still interpolating, leaving a transient low-contrast frame.
    val animatedColorScheme = colorScheme.animateAsState()

    LaunchedEffect(darkTheme) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = animatedColorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = remember { getTypography() },
        shapes = FolkShape.materialShapes,
        content = {
            // alpha28 has no non-deprecated way to override the ripple alpha, so the constructor is
            // suppressed rather than the alpha left at a value that disappears on near-black.
            @Suppress("DEPRECATION")
            val rippleConfiguration = if (darkTheme) {
                RippleConfiguration(rippleAlpha = DarkRippleAlpha)
            } else {
                LocalRippleConfiguration.current
            }
            MiuixKernelSUTheme(
                seedColor = baseSeed,
                isDark = darkTheme,
                paletteStyle = appSettings.paletteStyle,
                colorMode = appSettings.colorMode,
            ) {
                CompositionLocalProvider(
                    LocalRippleConfiguration provides rippleConfiguration,
                ) {
                    MonetColorsProvider.UpdateCss(colorScheme)
                    content()
                }
            }
        }
    )
}
