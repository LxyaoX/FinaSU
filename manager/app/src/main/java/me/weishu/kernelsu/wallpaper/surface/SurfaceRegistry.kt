package me.weishu.kernelsu.wallpaper.surface

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.ui.graphics.vector.ImageVector
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.HomeLayoutStyle

/**
 * Describes one configurable surface: where it sits in the scope chain, which home layouts it
 * belongs to, and which controls it exposes.
 *
 * [available] is the single source of truth for layout gating. The settings UI asks the registry
 * for the descriptors of the active layout instead of testing layout tokens itself, so a
 * layout-exclusive surface (the grid work card, for example) can never leak into another layout.
 * The Stats layout's header form is part of the decision, because its grid header draws the grid
 * work card.
 *
 * A descriptor is pure data: its [rows] drive the settings UI, its [fields]/[flags] drive storage
 * and the theme container, and [storageStem]/[themeBase] name the image file and the zip entry.
 */
data class SurfaceDescriptor(
    val id: SurfaceId,
    /** Section title shown for this surface. */
    val titleRes: Int,
    /** Section icon shown for this surface. */
    val icon: ImageVector,
    val scope: SurfaceScope,
    /** Home layout tokens this surface belongs to; empty means every layout. */
    val layouts: Set<String> = emptySet(),
    /** The controls this surface exposes. */
    val fields: Set<SurfaceField>,
    /** Extra boolean toggles this surface exposes. */
    val flags: Set<SurfaceFlag> = emptySet(),
    /** The editable rows, in display order. */
    val rows: List<SurfaceRow> = emptyList(),
    val pickIcon: ImageVector = Icons.Filled.Image,
    val clearIcon: ImageVector = Icons.Filled.Delete,
    /**
     * Aspect ratio (width / height) of the surface's on-screen box. The crop frame is locked to it
     * so the stored image matches the card one-to-one; [SurfaceBounds] overrides it with the box
     * actually measured on screen.
     */
    val aspect: Float = 1.6f,
    /** File-name stem of the stored image; null when the surface owns no file. */
    val storageStem: String? = null,
    /** Zip-entry stem used by the theme container; null when the surface is not exported. */
    val themeBase: String? = null,
    /** Historical `theme.json` keys kept for interop with themes from the wider ecosystem. */
    val legacyThemeFields: Map<SurfaceField, String> = emptyMap(),
    val legacyThemeFlags: Map<SurfaceFlag, String> = emptyMap(),
    /**
     * Whether this surface is available for a home layout token such as [HomeLayoutStyle.HOME],
     * given the Stats layout's header form (`list` or `grid`).
     */
    val available: (String, String) -> Boolean = { _, _ -> true },
    /**
     * Id of the surface that owns this one's shared master switch, when this is a child of a grouped
     * feature. The focus cards are children of [FOCUS], whose `enabled` bit is the single gate the
     * settings host and the renderer both consult. Null for top-level surfaces.
     */
    val parentId: SurfaceId? = null,
)

/**
 * The registry of every configurable wallpaper surface.
 *
 * The grid work card, the focus cards and the dashboard hero are live surfaces: each renders its
 * stored image and exposes its rows for its own layout. The stats top card is described but stays
 * unavailable, so it renders nothing and its settings never leak; its values still round-trip
 * through the theme container. Flipping [SurfaceDescriptor.available] on is what turns one into a
 * live surface once its rendering exists.
 */
object SurfaceRegistry {

    /** Stable id of the grid work-card background surface. */
    val GRID_WORK_CARD = SurfaceId("layout.grid.workCard")

    /** Shared config of the focus layout's card backgrounds; its four cards own only their image. */
    val FOCUS = SurfaceId("layout.focus")

    /** The four focus-layout card slots, mirroring the wider ecosystem's card ids. */
    val FOCUS_CARD_KERNEL = SurfaceId("layout.focus.card.kernel")
    val FOCUS_CARD_APP = SurfaceId("layout.focus.card.app")
    val FOCUS_CARD_DEVICE = SurfaceId("layout.focus.card.device")
    val FOCUS_CARD_STORAGE = SurfaceId("layout.focus.card.storage")

    /** The dashboard layout's hero card. */
    val DASHBOARD_HERO = SurfaceId("layout.dashboard.hero")

    private val gridWorkCard = SurfaceDescriptor(
        id = GRID_WORK_CARD,
        titleRes = R.string.wallpaper_work_card_section,
        icon = Icons.Filled.Wallpaper,
        scope = SurfaceScope.Slot,
        layouts = setOf(HomeLayoutStyle.HOME),
        fields = setOf(
            SurfaceField.Image,
            SurfaceField.Enabled,
            SurfaceField.Opacity,
            SurfaceField.DualOpacity,
            SurfaceField.DayOpacity,
            SurfaceField.NightOpacity,
            SurfaceField.Dim,
        ),
        flags = setOf(SurfaceFlag.HideIcon, SurfaceFlag.HideText, SurfaceFlag.HideMode),
        rows = listOf(
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_work_card_enable,
                icon = Icons.Filled.Wallpaper,
                field = SurfaceField.Enabled,
                summaryRes = R.string.wallpaper_work_card_enable_summary,
            ),
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_work_card_dual_opacity,
                icon = Icons.Filled.Contrast,
                field = SurfaceField.DualOpacity,
                showWhenEnabled = true,
            ),
            SurfaceScalarRow(
                singleTitleRes = R.string.wallpaper_work_card_opacity,
                dayTitleRes = R.string.wallpaper_work_card_day_opacity,
                nightTitleRes = R.string.wallpaper_work_card_night_opacity,
                singleField = SurfaceField.Opacity,
                dayField = SurfaceField.DayOpacity,
                nightField = SurfaceField.NightOpacity,
                dualField = SurfaceField.DualOpacity,
                singleIcon = Icons.Filled.Opacity,
                dayNightIcon = Icons.Filled.Contrast,
            ),
            SurfaceSliderRow(
                titleRes = R.string.wallpaper_work_card_dim,
                icon = Icons.Filled.Brightness6,
                field = SurfaceField.Dim,
            ),
            SurfaceImageRow(
                pickTitleRes = R.string.wallpaper_work_card_pick,
                changeTitleRes = R.string.wallpaper_work_card_change,
                clearTitleRes = R.string.wallpaper_work_card_clear,
                pickIcon = Icons.Filled.Image,
                clearIcon = Icons.Filled.Delete,
            ),
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_work_card_hide_check,
                icon = Icons.Filled.CheckCircle,
                flag = SurfaceFlag.HideIcon,
            ),
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_work_card_hide_text,
                icon = Icons.Filled.TextFields,
                flag = SurfaceFlag.HideText,
            ),
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_work_card_hide_mode,
                icon = Icons.AutoMirrored.Filled.Label,
                flag = SurfaceFlag.HideMode,
            ),
        ),
        storageStem = "work_card_background",
        themeBase = "grid_working_card_background",
        aspect = 1.0f,
        legacyThemeFields = mapOf(
            SurfaceField.Enabled to "isGridWorkingCardBackgroundEnabled",
            SurfaceField.Opacity to "gridWorkingCardBackgroundOpacity",
            SurfaceField.Dim to "gridWorkingCardBackgroundDim",
            SurfaceField.DualOpacity to "isGridDualOpacityEnabled",
            SurfaceField.DayOpacity to "gridWorkingCardBackgroundDayOpacity",
            SurfaceField.NightOpacity to "gridWorkingCardBackgroundNightOpacity",
        ),
        legacyThemeFlags = mapOf(
            SurfaceFlag.HideIcon to "isGridWorkingCardCheckHidden",
            SurfaceFlag.HideText to "isGridWorkingCardTextHidden",
            SurfaceFlag.HideMode to "isGridWorkingCardModeHidden",
        ),
        available = { _, _ -> true },
    )

    /** Every registered surface. */
    val all: List<SurfaceDescriptor> = listOf(gridWorkCard)

    /** The surfaces this home page offers, in registration order. */
    fun forLayout(): List<SurfaceDescriptor> = all

    /** The descriptor registered under [id], or null when no surface uses it. */
    fun descriptor(id: SurfaceId): SurfaceDescriptor? = all.firstOrNull { it.id == id }

    /**
     * The surfaces that carry theme data: either a file, or historical `theme.json` keys, so their
     * values round-trip through the theme container. A config-only surface (the focus parent) has no
     * file but is still exported for its shared dim/opacity values.
     */
    fun themeSlots(): List<SurfaceDescriptor> =
        all.filter {
            it.themeBase != null || it.legacyThemeFields.isNotEmpty() || it.legacyThemeFlags.isNotEmpty()
        }
}
