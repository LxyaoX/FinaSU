package me.weishu.kernelsu.ui.screen.sui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.SuiApp
import me.weishu.kernelsu.data.model.SuiMode
import me.weishu.kernelsu.data.sui.SuiPermissionHost
import me.weishu.kernelsu.ui.component.profile.dialogs.SingleSelectDialog
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.SuiNotice
import me.weishu.kernelsu.ui.viewmodel.SuiPermissionsViewModel

@Composable
fun SuiPermissionsScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<SuiPermissionsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(SuiPermissionHost.notificationsEnabled(context)) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsEnabled = granted }
    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var editing by remember { mutableStateOf<SuiApp?>(null) }
    var editingDefault by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { SuiPermissionHost.register() }
        // Without this grant a request arriving in the background is silent.
        if (!notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications()
        }
    }

    LaunchedEffect(uiState.notice) {
        when (uiState.notice) {
            SuiNotice.REBOOT_REQUIRED -> {
                Toast.makeText(context, R.string.sui_reboot_required, Toast.LENGTH_SHORT).show()
                viewModel.consumeNotice()
            }

            null -> Unit
        }
    }

    val actions = SuiPermissionsActions(
        onBack = { navigator.pop() },
        onRefresh = { viewModel.load(refresh = true) },
        onSearchTextChange = viewModel::setSearch,
        onClearSearch = { viewModel.setSearch("") },
        onToggleShowOnlyShizukuApps = {
            viewModel.setShowOnlyShizukuApps(!uiState.showOnlyShizukuApps)
        },
        onToggleLegacyBinderCompat = { viewModel.setLegacyBinderCompat(!uiState.legacyBinderCompat) },
        onToggleKsuNoEscape = { viewModel.setKsuNoEscape(!uiState.ksuNoEscape) },
        onSetAdbRootMode = viewModel::setAdbRootMode,
        onSetDefaultMode = viewModel::setDefaultMode,
        onOpenDefaultMode = { editingDefault = true },
        onOpenApp = { editing = it },
        onEnableNotifications = requestNotifications,
    )

    SuiPermissionsMaterial(
        uiState = uiState,
        actions = actions,
        notificationsEnabled = notificationsEnabled,
    )

    editing?.let { app ->
        SuiModeDialog(
            title = app.label,
            current = app.suiMode,
            labelOf = { it.labelRes },
            onConfirm = { viewModel.setMode(app, it) },
            onDismiss = { editing = null },
        )
    }

    if (editingDefault) {
        SuiModeDialog(
            title = stringResource(R.string.sui_batch_unconfigured),
            current = uiState.defaultMode,
            labelOf = { it.defaultLabelRes },
            onConfirm = viewModel::setDefaultMode,
            onDismiss = { editingDefault = false },
        )
    }
}

@Composable
private fun SuiModeDialog(
    title: String,
    current: SuiMode,
    labelOf: (SuiMode) -> Int,
    onConfirm: (SuiMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val titles = mapOf(
        SuiMode.DEFAULT to stringResource(labelOf(SuiMode.DEFAULT)),
        SuiMode.ROOT to stringResource(labelOf(SuiMode.ROOT)),
        SuiMode.SHELL to stringResource(labelOf(SuiMode.SHELL)),
        SuiMode.DENY to stringResource(labelOf(SuiMode.DENY)),
        SuiMode.HIDDEN to stringResource(labelOf(SuiMode.HIDDEN)),
    )
    SingleSelectDialog(
        title = title,
        items = SuiMode.entries,
        selectedItem = current,
        itemTitle = { titles.getValue(it) },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
