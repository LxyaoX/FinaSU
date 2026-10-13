package me.weishu.kernelsu.ui.screen.home

import androidx.compose.runtime.staticCompositionLocalOf
import me.weishu.kernelsu.data.model.SupportLinksStyle

/**
 * The active bottom-card style token, provided once at the top of the app so switching the
 * preference recomposes the home screen without a restart.
 */
val LocalSupportLinksStyle = staticCompositionLocalOf { SupportLinksStyle.DEFAULT }
