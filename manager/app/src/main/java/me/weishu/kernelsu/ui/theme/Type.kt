package me.weishu.kernelsu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/**
 * Material's sizes with our line breaking. The sizes stay Material's: a screen that wants one of
 * our three levels asks [FolkType] for it.
 */
fun getTypography(): Typography {
    fun heading(style: TextStyle) = style.wrapAware(LineBreak.Heading)
    fun paragraph(style: TextStyle) = style.wrapAware()

    // The M3E emphasized slots match the standard ones in sizing; the weight and tracking
    // treatment is defined here. Our line breaking has to be carried over either way.
    fun emphasize(style: TextStyle, isHeading: Boolean): TextStyle {
        val base = if (isHeading) heading(style) else paragraph(style)
        val tracking = (style.letterSpacing.value - if (isHeading) 0.2f else 0.1f).sp
        return if (isHeading) {
            base.copy(fontWeight = FontWeight.W700, letterSpacing = tracking)
        } else {
            base.copy(fontWeight = FontWeight.W600, letterSpacing = tracking)
        }
    }

    val defaults = Typography()
    return Typography(
        displayLarge = heading(defaults.displayLarge),
        displayMedium = heading(defaults.displayMedium),
        displaySmall = heading(defaults.displaySmall),
        headlineLarge = heading(defaults.headlineLarge),
        headlineMedium = heading(defaults.headlineMedium),
        headlineSmall = heading(defaults.headlineSmall),
        titleLarge = heading(defaults.titleLarge),
        titleMedium = heading(defaults.titleMedium),
        titleSmall = paragraph(defaults.titleSmall),
        bodyLarge = paragraph(defaults.bodyLarge),
        bodyMedium = paragraph(defaults.bodyMedium),
        bodySmall = paragraph(defaults.bodySmall),
        labelLarge = paragraph(defaults.labelLarge),
        labelMedium = paragraph(defaults.labelMedium),
        labelSmall = paragraph(defaults.labelSmall),
        displayLargeEmphasized = emphasize(defaults.displayLargeEmphasized, true),
        displayMediumEmphasized = emphasize(defaults.displayMediumEmphasized, true),
        displaySmallEmphasized = emphasize(defaults.displaySmallEmphasized, true),
        headlineLargeEmphasized = emphasize(defaults.headlineLargeEmphasized, true),
        headlineMediumEmphasized = emphasize(defaults.headlineMediumEmphasized, true),
        headlineSmallEmphasized = emphasize(defaults.headlineSmallEmphasized, true),
        titleLargeEmphasized = emphasize(defaults.titleLargeEmphasized, true),
        titleMediumEmphasized = emphasize(defaults.titleMediumEmphasized, true),
        titleSmallEmphasized = emphasize(defaults.titleSmallEmphasized, true),
        bodyLargeEmphasized = emphasize(defaults.bodyLargeEmphasized, false),
        bodyMediumEmphasized = emphasize(defaults.bodyMediumEmphasized, false),
        bodySmallEmphasized = emphasize(defaults.bodySmallEmphasized, false),
        labelLargeEmphasized = emphasize(defaults.labelLargeEmphasized, false),
        labelMediumEmphasized = emphasize(defaults.labelMediumEmphasized, false),
        labelSmallEmphasized = emphasize(defaults.labelSmallEmphasized, false)
    )
}
