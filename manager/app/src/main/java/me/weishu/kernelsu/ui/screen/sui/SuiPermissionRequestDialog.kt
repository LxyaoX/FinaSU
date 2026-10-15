package me.weishu.kernelsu.ui.screen.sui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.sui.SuiPermissionDecision
import me.weishu.kernelsu.data.sui.SuiPermissionHost
import me.weishu.kernelsu.data.sui.SuiPermissionRequest
import me.weishu.kernelsu.ui.theme.FolkShape

/** Hosts Sui's permission prompts wherever the user happens to be in the app. */
@Composable
fun SuiPermissionRequestDialog() {
    val pending by SuiPermissionHost.pending.collectAsStateWithLifecycle()
    val request = pending ?: return
    SuiPermissionRequestDialog(request = request, onDecide = SuiPermissionHost::reply)
}

/** Dismissing the prompt refuses this request only; no rule is written down. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuiPermissionRequestDialog(
    request: SuiPermissionRequest,
    onDecide: (SuiPermissionDecision) -> Unit,
) {
    val app = rememberRequestingApp(request.packageName)
    BasicAlertDialog(onDismissRequest = { onDecide(SuiPermissionDecision.DISMISS) }) {
        Surface(
            shape = FolkShape.Corner28,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = app.icon
                    if (icon != null) {
                        val bitmap = remember(icon) { icon.toBitmap(96, 96) }
                        Image(
                            painter = BitmapPainter(bitmap.asImageBitmap()),
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.WaterDrop,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sui_request_title, app.label),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.sui_request_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                PermissionOption(
                    icon = Icons.Filled.Check,
                    title = stringResource(R.string.sui_request_root),
                    subtitle = stringResource(R.string.sui_request_root_summary),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onDecide(SuiPermissionDecision.ALLOW_ROOT) },
                )
                PermissionOption(
                    icon = Icons.Outlined.Terminal,
                    title = stringResource(R.string.sui_request_shell),
                    subtitle = stringResource(R.string.sui_request_shell_summary),
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onDecide(SuiPermissionDecision.ALLOW_SHELL) },
                )
                PermissionOption(
                    icon = Icons.Outlined.Timelapse,
                    title = stringResource(R.string.sui_request_once),
                    subtitle = stringResource(R.string.sui_request_once_summary),
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onDecide(SuiPermissionDecision.ALLOW_ONCE) },
                )
                PermissionOption(
                    icon = Icons.Filled.Block,
                    title = stringResource(R.string.sui_request_deny),
                    subtitle = stringResource(R.string.sui_request_deny_summary),
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onDecide(SuiPermissionDecision.DENY) },
                )
            }
        }
    }
}

@Composable
private fun PermissionOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = FolkShape.Corner16,
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f),
                )
            }
        }
    }
}

private data class RequestingApp(val label: String, val icon: Drawable?)

@Composable
private fun rememberRequestingApp(packageName: String): RequestingApp {
    val context = LocalContext.current
    val fallback = RequestingApp(label = packageName, icon = null)
    val state by produceState(initialValue = fallback, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                RequestingApp(
                    label = pm.getApplicationLabel(info).toString(),
                    icon = info.loadIcon(pm),
                )
            }.getOrElse { fallback }
        }
    }
    return state
}
