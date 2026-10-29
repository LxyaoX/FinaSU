package me.weishu.kernelsu.ui.navigation3

import androidx.navigation3.runtime.NavKey
import me.weishu.kernelsu.wallpaper.WallpaperConfig

/**
 * The wallpaper page owned by the current navigation destination.
 *
 * Without this every pushed screen kept the wallpaper of whichever main page was visible, so a
 * settings sub-page under a module tab painted the module background. Routes without a section of
 * their own inherit the nearest route below them, which is how an editor or a flash flow keeps its
 * parent page's wallpaper.
 */
// shortcut: unmapped routes use the visible main page; add new section routes to the map below.
internal fun wallpaperPageFor(backStack: List<NavKey>, fallback: Int): Int =
    backStack.asReversed().firstNotNullOfOrNull { it.wallpaperPage } ?: fallback

private val NavKey.wallpaperPage: Int?
    get() = when (this) {
        Route.Susfs,
        Route.Install,
        -> WallpaperConfig.PAGE_HOME

        Route.Sulog,
        is Route.AppProfile,
        Route.AppProfileTemplate,
        is Route.TemplateEditor,
        -> WallpaperConfig.PAGE_SUPERUSER

        Route.ModuleRepo,
        is Route.ModuleRepoDetail,
        Route.KernelModules,
        is Route.ExecuteModuleAction,
        -> WallpaperConfig.PAGE_MODULE

        Route.About,
        Route.Nomount,
        Route.NomountExclusions,
        Route.UmountPaths,
        Route.YukiZygisk,
        Route.Teesim,
        Route.TeesimTargets,
        Route.ColorPalette,
        Route.Wallpaper,
        Route.Language,
        Route.BackupList,
        is Route.SettingsCategory,
        is Route.WebDavBrowser,
        -> WallpaperConfig.PAGE_SETTINGS

        else -> null
    }
