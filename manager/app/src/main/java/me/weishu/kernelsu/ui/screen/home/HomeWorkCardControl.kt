package me.weishu.kernelsu.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import me.weishu.kernelsu.ui.theme.ColorMode
import me.weishu.kernelsu.ui.theme.LocalColorMode
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig
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
 * [wallpaperRole] is non-null while wallpaper mode is active, which routes the card through
 * [me.weishu.kernelsu.ui.component.material.FolkWallpaperSurface] so that it shares the app-wide
 * wallpaper transparency. Otherwise [containerColor] is used as a solid fill.
 *
 * [accentColor] is the colour of the state: it paints the mark and the run-mode tag.
 * [tagColor]/[tagContentColor] are the quieter pair used by the tags that only annotate.
 */
@Immutable
internal data class HomeWorkCardStyle(
    val containerColor: Color,
    val contentColor: Color?,
    val wallpaperRole: WallpaperSurfaceRole?,
    val accentColor: Color,
    val tagColor: Color,
    val tagContentColor: Color,
)

/**
 * The single place that decides how the work card is painted, so the card body only has to lay out
 * its content.
 *
 * The palette is the reference build's: a pale green card with a bright green mark. When the theme
 * follows the wallpaper's colours the pair comes from the dynamic palette instead, because a fixed
 * green beside a generated accent reads as a mistake rather than as state.
 *
 * [surface] is the work card's own wallpaper surface: when it carries a decoded photo the card
 * paints it and borrows the photo's palette. A wallpaper *background* drops the card to the shared
 * neutral material, because an alpha-reduced tint loses the contrast of its paired on-colour once
 * the page behind it is a photograph.
 */
@Composable
internal fun homeWorkCardStyle(
    surface: SurfaceConfig,
    photo: SurfacePhoto,
): HomeWorkCardStyle {
    if (surface.hasImage && photo.ready) {
        return HomeWorkCardStyle(
            containerColor = Color.Transparent,
            contentColor = photo.contentColor(surface, isInDarkTheme()),
            wallpaperRole = null,
            accentColor = MiuixTheme.colorScheme.primary,
            tagColor = MiuixTheme.colorScheme.tertiaryContainer,
            tagContentColor = MiuixTheme.colorScheme.onTertiaryContainer,
        )
    }
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
    return if (LocalFolkWallpaperTokens.current != null) {
        HomeWorkCardStyle(
            containerColor = containerColor,
            contentColor = MiuixTheme.colorScheme.onSurface,
            wallpaperRole = WallpaperSurfaceRole.Group,
            accentColor = accentColor,
            tagColor = tagColor,
            tagContentColor = tagContentColor,
        )
    } else {
        HomeWorkCardStyle(
            containerColor = containerColor,
            // miuix's card publishes its own content colour; both greens want the theme's normal
            // on-surface ink, which is what the reference build draws with.
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
            wallpaperRole = null,
            accentColor = accentColor,
            tagColor = tagColor,
            tagContentColor = tagContentColor,
        )
    }
}
