package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.SupportLinksStyle
import me.weishu.kernelsu.ui.component.material.LocalWallpaperSupportingColor
import me.weishu.kernelsu.ui.component.material.folkPressScale
import me.weishu.kernelsu.ui.theme.FolkType

/**
 * The bottom card of the home screen. [LocalSupportLinksStyle] selects one of three shapes: an
 * introduction to FinaSU, the upstream KernelSU pair, or nothing at all. Each shape is its own
 * composable so adding a new one touches this dispatcher and [SupportLinksStyle] only.
 */
@Composable
internal fun SupportLinks(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (LocalSupportLinksStyle.current) {
        SupportLinksStyle.HIDDEN -> Unit
        SupportLinksStyle.LEGACY -> LegacySupportLinks(onOpenUrl = onOpenUrl, modifier = modifier)
        else -> LearnFinaSuCard(onOpenUrl = onOpenUrl, modifier = modifier)
    }
}

/** The upstream KernelSU pair: a donation row and a KernelSU documentation row. */
@Composable
private fun LegacySupportLinks(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier,
) {
    val learnMoreUrl = stringResource(R.string.home_learn_kernelsu_url)

    HomeCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SupportLinkRow(
                icon = Icons.Filled.VolunteerActivism,
                title = stringResource(R.string.home_support_title),
                subtitle = stringResource(R.string.home_support_content),
                onClick = { onOpenUrl("https://patreon.com/weishu") },
            )
            SupportLinkRow(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = stringResource(R.string.home_learn_kernelsu),
                subtitle = stringResource(R.string.home_click_to_learn_kernelsu),
                onClick = { onOpenUrl(learnMoreUrl) },
            )
        }
    }
}

/** A single row that opens the FinaSU website. */
@Composable
private fun LearnFinaSuCard(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier,
) {
    val learnMoreUrl = stringResource(R.string.home_learn_finasu_url)

    HomeCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SupportLinkRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.home_learn_finasu),
                subtitle = stringResource(R.string.home_click_to_learn_finasu),
                onClick = { onOpenUrl(learnMoreUrl) },
            )
        }
    }
}

@Composable
private fun SupportLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    // On wallpaper the card material carries its own supporting colour, tuned against the
    // translucent fill; off it we keep the semantic greys.
    val supportingColor = LocalWallpaperSupportingColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .folkPressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(20.dp),
            tint = supportingColor ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = FolkType.Summary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = FolkType.Caption,
                color = supportingColor ?: MaterialTheme.colorScheme.outline
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = supportingColor ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
