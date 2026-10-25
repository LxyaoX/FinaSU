package me.weishu.kernelsu.ui.theme

import androidx.compose.material3.LocalContentColor as MaterialLocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * The miuix side of the theme, mounted inside [MaterialKernelSUTheme].
 *
 * Both toolkits get their colours from the same seed and the same palette style, so a screen that
 * mixes `MiuixScaffold` with a Material component does not show two different apps. Until the
 * screens are ported there is nothing reading this, which is why it changes no pixels yet: it is
 * here first so the dependency, the palette mapping and the content colour are settled before any
 * layout depends on them.
 */
@Composable
fun MiuixKernelSUTheme(
    seedColor: Color,
    isDark: Boolean,
    paletteStyle: PaletteStyle,
    colorMode: ColorMode,
    content: @Composable () -> Unit,
) {
    val mode = when (colorMode) {
        ColorMode.SYSTEM -> ColorSchemeMode.System
        ColorMode.LIGHT -> ColorSchemeMode.Light
        ColorMode.DARK -> ColorSchemeMode.Dark
        ColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
        ColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
        ColorMode.MONET_DARK, ColorMode.DARK_AMOLED -> ColorSchemeMode.MonetDark
    }
    // miuix names its palettes after materialkolor's, but the enum is its own: an unknown name
    // falls back instead of failing the first frame.
    val style = remember(paletteStyle) {
        runCatching { ThemePaletteStyle.valueOf(paletteStyle.name) }
            .getOrDefault(ThemePaletteStyle.TonalSpot)
    }
    val colorSpec = if (paletteStyle.supportsSpec2025) {
        ThemeColorSpec.Spec2025
    } else {
        ThemeColorSpec.Spec2021
    }
    val keyColor = seedColor.takeIf { it != Color.Unspecified }
    val controller = remember(mode, keyColor, isDark, style, colorSpec) {
        ThemeController(
            mode,
            keyColor = keyColor,
            isDark = isDark,
            paletteStyle = style,
            colorSpec = colorSpec,
        )
    }
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(
            // miuix draws text with LocalContentColor, Material draws it with contentColorFor();
            // seeding it from the miuix surface keeps text readable on miuix backgrounds.
            LocalContentColor provides MiuixTheme.colorScheme.onBackground,
            // Material's LocalContentColor is a different local with a black default. Nothing above a
            // Material Scaffold/Surface seeds it - and miuix's scaffold, top bar and cards are not
            // Material surfaces - so text and icons placed in them would fall back to black. Seed it
            // here; any Material Surface inside still overrides it with its own content colour.
            MaterialLocalContentColor provides MiuixTheme.colorScheme.onBackground,
            content = content,
        )
    }
}
