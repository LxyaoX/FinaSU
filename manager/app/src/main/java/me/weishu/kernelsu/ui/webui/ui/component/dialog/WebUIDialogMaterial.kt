package me.weishu.kernelsu.ui.webui.ui.component.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.ExpressiveDialog
import me.weishu.kernelsu.ui.component.material.FolkTextButton
import me.weishu.kernelsu.ui.webui.model.WebUIDialog

/**
 * The three dialogs a WebUI can raise (alert, confirm, prompt), drawn with the manager's own dialog
 * frame so a module page cannot make the app look like a different product.
 */
@Composable
fun WebUIDialogMaterial(
    dialog: WebUIDialog,
) {
    when (dialog) {
        is WebUIDialog.Alert -> {
            ExpressiveDialog(
                onDismissRequest = dialog.onDismiss,
                confirmButton = {
                    FolkTextButton(onClick = dialog.onConfirm) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                text = { Text(dialog.message) }
            )
        }

        is WebUIDialog.Confirm -> {
            ExpressiveDialog(
                onDismissRequest = dialog.onDismiss,
                confirmButton = {
                    FolkTextButton(onClick = dialog.onConfirm) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    FolkTextButton(onClick = dialog.onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
                text = { Text(dialog.message) }
            )
        }

        is WebUIDialog.Prompt -> {
            val inputState = remember(dialog) { mutableStateOf(dialog.defaultValue) }
            ExpressiveDialog(
                onDismissRequest = dialog.onDismiss,
                confirmButton = {
                    FolkTextButton(onClick = { dialog.onConfirm(inputState.value) }) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    FolkTextButton(onClick = dialog.onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
                text = {
                    Column {
                        OutlinedTextField(
                            label = { Text(dialog.message) },
                            value = inputState.value,
                            onValueChange = { inputState.value = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )
        }
    }
}
