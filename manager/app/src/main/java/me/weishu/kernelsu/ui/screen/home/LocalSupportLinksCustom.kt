package me.weishu.kernelsu.ui.screen.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The user-entered contents of the `SupportLinksStyle.CUSTOM` home card.
 *
 * All three fields are required: the card renders only when every one of them is non-blank, so a
 * half-configured card silently behaves like [me.weishu.kernelsu.data.model.SupportLinksStyle.HIDDEN]
 * instead of showing a broken row.
 */
@Immutable
data class SupportLinksCustomContent(
    val title: String = "",
    val subtitle: String = "",
    val url: String = "",
) {
    /** Whether the card has enough content to be drawn. */
    val isRenderable: Boolean
        get() = title.isNotBlank() && subtitle.isNotBlank() && url.isNotBlank()
}

/**
 * The active custom bottom-card content, provided once at the top of the app so editing the
 * preference recomposes the home screen without a restart.
 */
val LocalSupportLinksCustom = staticCompositionLocalOf { SupportLinksCustomContent() }
