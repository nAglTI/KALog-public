package org.debs.kalog.feature.chat.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import mayday_chat.feature.chat.generated.resources.Res
import mayday_chat.feature.chat.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun UuidInputDialog(
    title: String,
    value: String,
    confirmLabel: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isProcessing: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                label = { Text("UUID") },
                shape = MaterialTheme.shapes.large,
            )
        },
        dismissButton = {
            TextButton(
                enabled = !isProcessing,
                onClick = onDismiss,
            ) {
                Text(stringResource(Res.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank() && !isProcessing,
                onClick = onConfirm,
            ) {
                Text(confirmLabel)
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
