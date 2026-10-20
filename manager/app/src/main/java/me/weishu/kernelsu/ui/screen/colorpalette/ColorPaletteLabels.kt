package me.weishu.kernelsu.ui.screen.colorpalette

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import me.weishu.kernelsu.R

@StringRes
internal fun paletteStyleLabelRes(style: PaletteStyle): Int = when (style) {
    PaletteStyle.TonalSpot -> R.string.palette_style_tonal_spot
    PaletteStyle.Neutral -> R.string.palette_style_neutral
    PaletteStyle.Vibrant -> R.string.palette_style_vibrant
    PaletteStyle.Expressive -> R.string.palette_style_expressive
    PaletteStyle.Rainbow -> R.string.palette_style_rainbow
    PaletteStyle.FruitSalad -> R.string.palette_style_fruit_salad
    PaletteStyle.Monochrome -> R.string.palette_style_monochrome
    PaletteStyle.Fidelity -> R.string.palette_style_fidelity
    PaletteStyle.Content -> R.string.palette_style_content
}

@Composable
internal fun paletteStyleLabel(style: PaletteStyle): String = stringResource(paletteStyleLabelRes(style))

internal fun colorSpecLabel(spec: ColorSpec.SpecVersion): String = spec.name.removePrefix("SPEC_")
