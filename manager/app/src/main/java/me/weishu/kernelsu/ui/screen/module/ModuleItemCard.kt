package me.weishu.kernelsu.ui.screen.module

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Wysiwyg
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.Module
import me.weishu.kernelsu.ui.component.card.cardSurfaceOpacity
import me.weishu.kernelsu.ui.component.material.ExpressiveSwitch
import me.weishu.kernelsu.ui.component.module.AdaptiveModuleButtonRow
import me.weishu.kernelsu.ui.component.module.ModuleButtonConfig
import me.weishu.kernelsu.ui.component.module.ModuleCardLabel
import me.weishu.kernelsu.ui.component.module.ModuleCardShell

enum class ModuleButtonStyle {
    Full,
    IconOnly;

    companion object {
        fun fromKey(key: String): ModuleButtonStyle =
            if (key == me.weishu.kernelsu.modulecard.ModuleCardConfig.STYLE_ICON_ONLY) IconOnly else Full
    }
}

@Composable
fun ModuleItemCard(
    module: Module,
    updateUrl: String,
    buttonStyle: ModuleButtonStyle,
    showMoreInfo: Boolean,
    foldSystemModule: Boolean,
    zygiskAvailable: Boolean,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    onUninstallClicked: () -> Unit,
    onCheckChanged: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onAddShortcut: (ShortcutType) -> Unit,
    onOpenWebUi: () -> Unit,
    onExecuteAction: () -> Unit,
    closeSearch: () -> Unit,
) {
    val iconOnly = buttonStyle == ModuleButtonStyle.IconOnly
    val zygiskBlocked = module.zygiskRequired && !zygiskAvailable && !module.remove
    val canOpenWebUi = module.hasWebUi && !module.remove && module.enabled && !zygiskBlocked
    val hasUpdate = updateUrl.isNotEmpty()
    val haptics = LocalHapticFeedback.current

    val labels = buildList {
        if (showMoreInfo) {
            add(
                ModuleCardLabel(
                    text = module.id,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
        when {
            module.remove -> add(
                ModuleCardLabel(
                    text = stringResource(R.string.uninstall),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            )
            hasUpdate && !module.update -> add(
                ModuleCardLabel(
                    text = stringResource(R.string.module_update),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            )
            module.update -> add(
                ModuleCardLabel(
                    text = "Updated",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            )
        }
        if (zygiskBlocked) {
            add(
                ModuleCardLabel(
                    text = stringResource(R.string.zygisk_required),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            )
        }
        if (showMoreInfo && canOpenWebUi) {
            add(
                ModuleCardLabel(
                    text = "WebUI",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
        if (showMoreInfo && module.hasActionScript && module.enabled && !module.remove && !zygiskBlocked) {
            add(
                ModuleCardLabel(
                    text = stringResource(R.string.action),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            )
        }
        if (module.metamodule && !module.remove) {
            add(
                ModuleCardLabel(
                    text = "META",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            )
        }
    }

    val subtitles = if (showMoreInfo) {
        listOf(
            "${stringResource(R.string.module_version)}: ${module.version}",
            "${stringResource(R.string.module_author)}: ${module.author}",
        )
    } else {
        emptyList()
    }

    ModuleCardShell(
        title = module.name,
        labels = labels,
        subtitles = subtitles,
        description = module.description.takeIf { showMoreInfo },
        expanded = expanded,
        onClickLabel = null,
        onClick = {
            if (foldSystemModule) {
                onExpandToggle()
            } else if (canOpenWebUi) {
                onOpenWebUi()
                closeSearch()
            }
        },
        titleDecoration = if (module.remove) TextDecoration.LineThrough else null,
        switch = {
            ExpressiveSwitch(
                enabled = !module.update,
                checked = module.enabled,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckChanged(it)
                },
            )
        },
    ) {
        Spacer(Modifier.height(if (showMoreInfo && module.description.isNotBlank()) 16.dp else 12.dp))
        AnimatedVisibility(
            visible = !foldSystemModule || expanded,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            ModuleActionRow(
                module = module,
                hasUpdate = hasUpdate,
                iconOnly = iconOnly,
                opacity = cardSurfaceOpacity(),
                zygiskBlocked = zygiskBlocked,
                onUninstallClicked = onUninstallClicked,
                onUpdate = onUpdate,
                onAddShortcut = onAddShortcut,
                onOpenWebUi = onOpenWebUi,
                onExecuteAction = onExecuteAction,
                closeSearch = closeSearch,
            )
        }
    }
}

@Composable
private fun ModuleActionRow(
    module: Module,
    hasUpdate: Boolean,
    iconOnly: Boolean,
    opacity: Float,
    zygiskBlocked: Boolean,
    onUninstallClicked: () -> Unit,
    onUpdate: () -> Unit,
    onAddShortcut: (ShortcutType) -> Unit,
    onOpenWebUi: () -> Unit,
    onExecuteAction: () -> Unit,
    closeSearch: () -> Unit,
) {
    val hasUpdateButton = hasUpdate && !module.remove && !module.update
    val buttons = buildList {
        if (module.hasWebUi && module.enabled && !module.remove) {
            add(
                ModuleButtonConfig(
                    icon = Icons.AutoMirrored.Outlined.Wysiwyg,
                    text = stringResource(R.string.open),
                    contentDescription = stringResource(R.string.open),
                    onClick = {
                        onOpenWebUi()
                        closeSearch()
                    },
                    onLongClick = { onAddShortcut(ShortcutType.WebUI) },
                    enabled = !zygiskBlocked,
                ),
            )
        }
        if (module.hasActionScript && module.enabled && !module.remove) {
            add(
                ModuleButtonConfig(
                    icon = Icons.Outlined.Terminal,
                    text = stringResource(R.string.action),
                    contentDescription = stringResource(R.string.action),
                    onClick = {
                        onExecuteAction()
                        closeSearch()
                    },
                    onLongClick = { onAddShortcut(ShortcutType.Action) },
                    enabled = !zygiskBlocked,
                ),
            )
        }
        if (hasUpdateButton) {
            add(
                ModuleButtonConfig(
                    icon = Icons.Outlined.Download,
                    text = stringResource(R.string.module_update),
                    contentDescription = stringResource(R.string.module_update),
                    onClick = onUpdate,
                ),
            )
        }
    }
    val trailing = ModuleButtonConfig(
        icon = if (module.remove) Icons.Outlined.Restore else Icons.Outlined.Delete,
        text = if (module.remove) stringResource(R.string.undo) else stringResource(R.string.uninstall),
        contentDescription = if (module.remove) stringResource(R.string.undo) else stringResource(R.string.uninstall),
        onClick = onUninstallClicked,
        colors = when {
            iconOnly -> ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
            module.remove -> ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            else -> ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        },
    )
    AdaptiveModuleButtonRow(
        buttons = buttons,
        trailingButton = trailing,
        iconOnly = iconOnly,
        spacing = if (iconOnly) 12 else 8,
        opacity = opacity,
    )
}
