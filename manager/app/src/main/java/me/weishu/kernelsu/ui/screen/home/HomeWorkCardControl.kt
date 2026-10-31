package me.weishu.kernelsu.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import me.weishu.kernelsu.ui.theme.ColorMode
import me.weishu.kernelsu.ui.theme.LocalColorMode
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The accent the reference build uses for a working kernel: a bright green. */
private val SuKiWorkingGreen = Color(0xFF36D167)

/** Its card: a pale green in the light theme, a deep one at night. */
private val SuKiWorkingContainerLight = Color(0xFFDFFAE4)
private val SuKiWorkingContainerDark = Color(0xFF1A3825)

/** The quieter pair the custom-LKM badge sits on. */
private val SuKiWorkingTagLight = Color(0xFFB8E8C5)
private val SuKiWorkingTagDark = Color(0xFF315D3E)
private val SuKiWorkingTagContentLight = Color(0xFF164A29)
private val SuKiWorkingTagContentDark = Color(0xFFB8E8C5)

/**
 * The resolved surface for the home work card.
 *
 * [accentColor] is the colour of the state: it paints the mark and the run-mode tag.
 * [tagColor]/[tagContentColor] are the quieter pair used by the tags that only annotate.
 */
@Immutable
internal data class HomeWorkCardStyle(
    val containerColor: Color,
    val contentColor: Color?,
    val accentColor: Color,
    val tagColor: Color,
    val tagContentColor: Color,
)

/**
 * The single place that decides how the work card is painted, so the card body only has to lay out
 * its content.
 *
 * The palette is the reference build's: a pale green card with a bright green mark. When the theme
 * is monet the pair comes from the dynamic palette instead, because a fixed green beside a generated
 * accent reads as a mistake rather than as state.
 */
@Composable
internal fun homeWorkCardStyle(): HomeWorkCardStyle {
    val dynamic = ColorMode.fromValue(LocalColorMode.current).isMonet
    val dark = isInDarkTheme()
    val accentColor = if (dynamic) MiuixTheme.colorScheme.primary else SuKiWorkingGreen
    val containerColor = when {
        dynamic -> MiuixTheme.colorScheme.secondaryContainer
        dark -> SuKiWorkingContainerDark
        else -> SuKiWorkingContainerLight
    }
    val tagColor = when {
        dynamic -> MiuixTheme.colorScheme.tertiaryContainer
        dark -> SuKiWorkingTagDark
        else -> SuKiWorkingTagLight
    }
    val tagContentColor = when {
        dynamic -> MiuixTheme.colorScheme.onTertiaryContainer
        dark -> SuKiWorkingTagContentDark
        else -> SuKiWorkingTagContentLight
    }
    return HomeWorkCardStyle(
        containerColor = containerColor,
        // miuix's card publishes its own content colour; both greens want the theme's normal
        // on-surface ink, which is what the reference build draws with.
        contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
        accentColor = accentColor,
        tagColor = tagColor,
        tagContentColor = tagContentColor,
    )
}
