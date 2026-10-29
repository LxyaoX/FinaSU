package me.weishu.kernelsu.ui.screen.home

import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig

/**
 * The resolved surface for the home work card.
 *
 * [wallpaperRole] is non-null while wallpaper mode is active, which routes the card through
 * [me.weishu.kernelsu.ui.component.material.FolkWallpaperSurface] so that it shares the app-wide
 * wallpaper transparency. Otherwise [containerColor] is used as a solid fill.
 *
 * A work card with its own photo drops to a transparent container and asks the photo for a content
 * colour, so the hero text stays readable over whatever the user picked.
 */
@Immutable
internal data class HomeWorkCardStyle(
    val containerColor: Color,
    val contentColor: Color?,
    val wallpaperRole: WallpaperSurfaceRole?,
)

/**
 * The single place that decides how the work card is painted, so the card body only has to lay out
 * its content.
 *
 * The card is neutral: a tinted container across the whole surface made a slab of colour sit on top
 * of a page whose other cards are all the same quiet material, and with a saturated seed that slab
 * read as the wrong colour rather than as status. The accent is left to the parts that carry the
 * meaning - the state in the title, the run-mode tag and the mark behind the text.
 *
 * [surface] is the work card's own wallpaper surface: when it carries a decoded photo the card
 * paints it and borrows the photo's palette. In wallpaper mode the card joins the shared neutral
 * material instead, because an alpha-reduced fill loses the contrast of its paired on-colour once a
 * wallpaper shows through.
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
        )
    }
    return if (LocalFolkWallpaperTokens.current != null) {
        HomeWorkCardStyle(
            containerColor = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurface,
            wallpaperRole = WallpaperSurfaceRole.Group,
        )
    } else {
        HomeWorkCardStyle(
            containerColor = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
            wallpaperRole = null,
        )
    }
}
