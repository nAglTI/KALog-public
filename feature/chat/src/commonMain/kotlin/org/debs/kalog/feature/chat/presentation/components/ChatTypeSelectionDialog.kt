package org.debs.kalog.feature.chat.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import mayday_chat.feature.chat.generated.resources.Res
import mayday_chat.feature.chat.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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
                text = stringResource(Res.string.create_chat),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.choose_chat_type),
                style = MaterialTheme.typography.bodyMedium,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !isProcessing,
                    onClick = onCreatePersonalChat,
                ) {
                    Text(stringResource(Res.string.personal))
                }
                TextButton(
                    enabled = !isProcessing,
                    onClick = onCreateGroupChat,
                ) {
                    Text(stringResource(Res.string.group))
                }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
