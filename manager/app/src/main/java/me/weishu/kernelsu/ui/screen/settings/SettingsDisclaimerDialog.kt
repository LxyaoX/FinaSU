package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.ExpressiveDialog
import me.weishu.kernelsu.ui.component.material.FolkTextButton

/**
 * The root-risk notice reachable from the bottom of the settings screen, mirroring FolkPatch's
 * disclaimer dialog: a short title over the body copy, dismissed by a single OK.
 */
@Composable
internal fun SettingsDisclaimerDialog(showDialog: MutableState<Boolean>) {
    ExpressiveDialog(
        onDismissRequest = { showDialog.value = false },
        title = {
            Text(
                text = stringResource(R.string.settings_disclaimer),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.settings_disclaimer_message),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            FolkTextButton(onClick = { showDialog.value = false }) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}
