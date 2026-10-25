package me.weishu.kernelsu.ui.theme

import androidx.compose.material3.LocalContentColor as MaterialLocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.materialkolor.PaletteStyle
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
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
    fontFamily: FontFamily = FontFamily.Default,
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
    // The Material side paints an AMOLED background pitch black; miuix would keep its own near-black
    // surface, so the home scaffold and every miuix top bar would show as a lighter band. The same
    // roles are blacked out here so both toolkits agree in that mode.
    val colors = controller.currentColors().let { base ->
        if (!colorMode.isAmoled) {
            base
        } else {
            base.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainer = Color.Black,
                surfaceContainerHigh = Color.Black,
                surfaceContainerHighest = Color.Black,
            )
        }
    }
    // miuix carries its own type ramp, separate from Material's typography. Without this the imported
    // font (the bundled Xiaolai face, or the user's own) only shows in the screens Material still
    // draws, and every miuix label, preference and button falls back to the platform font.
    val defaultTextStyles = MiuixTheme.textStyles
    val textStyles = remember(defaultTextStyles, fontFamily) {
        if (fontFamily == FontFamily.Default) {
            defaultTextStyles
        } else {
            defaultTextStyles.withFontFamily(fontFamily)
        }
    }
    MiuixTheme(colors = colors, textStyles = textStyles) {
        CompositionLocalProvider(
            // miuix draws text with LocalContentColor, Material draws it with contentColorFor();
            // seeding it from the miuix surface keeps text readable on miuix backgrounds.
            LocalContentColor provides colors.onBackground,
            // Material's LocalContentColor is a different local with a black default. Nothing above a
            // Material Scaffold/Surface seeds it - and miuix's scaffold, top bar and cards are not
            // Material surfaces - so text and icons placed in them would fall back to black. Seed it
            // here; any Material Surface inside still overrides it with its own content colour.
            MaterialLocalContentColor provides colors.onBackground,
            content = content,
        )
    }
}

/**
 * The miuix type ramp with [family] in place of the platform font.
 *
 * Every style has to be rewritten individually: miuix resolves `fontFamily` per style and offers no
 * theme-wide override, so a single missed style would still render in the default face.
 */
private fun TextStyles.withFontFamily(family: FontFamily): TextStyles = copy(
    main = main.copy(fontFamily = family),
    paragraph = paragraph.copy(fontFamily = family),
    body1 = body1.copy(fontFamily = family),
    body2 = body2.copy(fontFamily = family),
    button = button.copy(fontFamily = family),
    footnote1 = footnote1.copy(fontFamily = family),
    footnote2 = footnote2.copy(fontFamily = family),
    headline1 = headline1.copy(fontFamily = family),
    headline2 = headline2.copy(fontFamily = family),
    subtitle = subtitle.copy(fontFamily = family),
    title1 = title1.copy(fontFamily = family),
    title2 = title2.copy(fontFamily = family),
    title3 = title3.copy(fontFamily = family),
    title4 = title4.copy(fontFamily = family),
)
