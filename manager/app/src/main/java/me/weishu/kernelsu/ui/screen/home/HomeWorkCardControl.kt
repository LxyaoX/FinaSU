package me.weishu.kernelsu.ui.screen.home

import androidx.compose.material3.MaterialTheme
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
 * its content. [surface] is the work card's own wallpaper surface: when it carries a decoded photo
 * the card paints it and switches to the photo's palette, otherwise the semantic accent pair of
 * [working] is used and wallpaper mode drops it to the shared neutral material - an alpha-reduced
 * accent container loses the contrast of its paired on-colour once a photo shows through.
 */
@Composable
internal fun homeWorkCardStyle(
    working: Boolean,
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
    val containerColor = if (working) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    return if (LocalFolkWallpaperTokens.current != null) {
        HomeWorkCardStyle(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            wallpaperRole = WallpaperSurfaceRole.Group,
        )
    } else {
        HomeWorkCardStyle(
            containerColor = containerColor,
            contentColor = null,
            wallpaperRole = null,
        )
    }
}
