package org.debs.kalog.feature.chat.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.debs.kalog.feature.chat.localization.chatLocalized

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
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
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        dismissButton = {
            TextButton(
                enabled = !isProcessing,
                onClick = onDismiss,
            ) {
                Text(chatLocalized(en = "Cancel", ru = "Отмена"))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isProcessing,
                onClick = onConfirm,
            ) {
                Text(confirmLabel)
            }
        },
    )
}
