package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.FolkMountProvider
import me.weishu.kernelsu.ui.component.material.LocalWallpaperSupportingColor
import me.weishu.kernelsu.ui.theme.FolkType
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun InfoCard(
    systemInfo: SystemInfo,
    modifier: Modifier = Modifier,
    susfs: me.weishu.kernelsu.data.susfs.SusfsStatus = me.weishu.kernelsu.data.susfs.SusfsStatus(),
    zygiskProvider: String? = null,
    mount: HomeMount = HomeMount(),
    onSusfs: () -> Unit = {},
) {
    val selinuxDisplay = selinuxDisplayName(systemInfo.selinuxStatus)
    val seccompDisplay = seccompDisplayName(systemInfo.seccompStatus)
    val display = rememberHomeDisplaySettings()

    // The labels are resolved here, in the composable body: `stringResource` cannot be called from
    // inside a lambda that `buildList` would take.
    val managerVersionLabel = stringResource(R.string.home_manager_version)
    val kernelLabel = stringResource(R.string.home_kernel)
    val kernelFullVersionLabel = stringResource(R.string.home_kernel_full_version)
    val deviceModelLabel = stringResource(R.string.home_device_model)
    val fingerprintLabel = stringResource(R.string.home_fingerprint)
    val zygiskLabel = stringResource(R.string.home_zygisk_implement)
    val mountLabel = stringResource(R.string.home_mount_implementation)
    val susfsLabel = stringResource(R.string.susfs_title)

    val systemRows = mutableListOf<SpecRow>()
    systemRows += SpecRow(managerVersionLabel, systemInfo.managerVersion)
    systemRows += SpecRow(kernelLabel, systemInfo.kernelVersion)
    systemInfo.kernelFullVersion?.takeIf { it.isNotBlank() }?.let { value ->
        systemRows += SpecRow(kernelFullVersionLabel, value)
    }
    systemRows += SpecRow(deviceModelLabel, systemInfo.deviceModel)
    if (!display.hideFingerprint) {
        systemRows += SpecRow(fingerprintLabel, systemInfo.fingerprint)
    }
    if (zygiskProvider != null && !display.hideZygisk) {
        systemRows += SpecRow(zygiskLabel, zygiskProvider)
    }
    if (!display.hideMount) {
        homeMountValue(mount)?.let { value ->
            systemRows += SpecRow(mountLabel, value)
        }
    }
    if (susfs.detected && !display.hideSusfs) {
        val management = me.weishu.kernelsu.ui.screen.susfsManagementLabel(susfs.management)
        systemRows += SpecRow(
            label = susfsLabel,
            value = "${susfs.version} · ${susfs.implementation} · $management",
            onClick = onSusfs,
        )
    }
    val securityRows = listOf(
        SpecRow(stringResource(R.string.home_selinux_status), selinuxDisplay),
        SpecRow(stringResource(R.string.home_seccomp_status), seccompDisplay),
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpecSheet(rows = systemRows)
        SpecSheet(rows = securityRows)
    }
}

/** One line of a spec sheet: what is asked on the left, what was answered on the right. */
private data class SpecRow(
    val label: String,
    val value: String,
    /** Set on a row that leads somewhere; the value then reads as a link. */
    val onClick: (() -> Unit)? = null,
)

/**
 * A card of label/value lines separated by hairlines, the shape miuix gives a block of facts.
 *
 * The values are monospaced and right-aligned so a version string and a fingerprint line up under
 * each other; the label column keeps its own width instead of pushing the value off the card.
 */
@Composable
private fun SpecSheet(
    rows: List<SpecRow>,
    modifier: Modifier = Modifier,
) {
    val overColor = LocalHomeTileCardContentColor.current
    val supportingColor = LocalWallpaperSupportingColor.current
    HomeCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (row.onClick != null) {
                                Modifier.clickable(onClick = row.onClick)
                            } else {
                                Modifier
                            }
                        )
                        .heightIn(min = 48.dp)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = row.label,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = overColor ?: MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = row.value,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (row.onClick != null) {
                            overColor ?: MaterialTheme.colorScheme.primary
                        } else {
                            supportingColor ?: MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
internal fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // On wallpaper the material carries its own supporting colour, tuned against the translucent
        // fill; off it we keep the semantic greys.
        val supportingColor = LocalWallpaperSupportingColor.current
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = supportingColor ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = FolkType.Summary)
            Text(
                text = value,
                style = FolkType.Machine,
                color = supportingColor ?: MaterialTheme.colorScheme.outline
            )
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(16.dp))
            trailingContent()
        }
    }
}

/** The provider that really mounted modules this boot, or null when it cannot be named. */
@Composable
private fun homeMountValue(mount: HomeMount): String? = when (mount.provider) {
    FolkMountProvider.METAMODULE -> listOfNotNull(
        stringResource(R.string.settings_folk_mount_provider_metamodule),
        mount.metamodule?.name?.takeIf { it.isNotBlank() } ?: mount.metamoduleId,
        mount.metamodule?.version?.takeIf { it.isNotBlank() && it != "Unknown" },
    ).joinToString(" · ")

    FolkMountProvider.BUILTIN -> stringResource(R.string.home_mount_builtin_magic)
    FolkMountProvider.NOMOUNT -> stringResource(R.string.home_mount_builtin_vfs)
    FolkMountProvider.NONE -> stringResource(R.string.settings_folk_mount_mode_off)
    null -> null
}
