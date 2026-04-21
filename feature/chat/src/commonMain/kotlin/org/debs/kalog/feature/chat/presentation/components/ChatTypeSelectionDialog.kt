package org.debs.kalog.feature.chat.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun ChatTypeSelectionDialog(
    onDismiss: () -> Unit,
    onCreatePersonalChat: () -> Unit,
    onCreateGroupChat: () -> Unit,
    isProcessing: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create chat",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = "Choose what you want to create.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        dismissButton = {
            TextButton(
                enabled = !isProcessing,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !isProcessing,
                    onClick = onCreatePersonalChat,
                ) {
                    Text("Personal")
                }
                TextButton(
                    enabled = !isProcessing,
                    onClick = onCreateGroupChat,
                ) {
                    Text("Group")
                }
            }
        },
    )
}
