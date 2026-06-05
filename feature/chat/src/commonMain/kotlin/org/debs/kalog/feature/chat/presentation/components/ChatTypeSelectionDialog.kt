package org.debs.kalog.feature.chat.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.debs.kalog.feature.chat.localization.chatLocalized

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
                text = chatLocalized(
                    en = "Create chat",
                    ru = "Создать чат",
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = chatLocalized(
                    en = "Choose what you want to create.",
                    ru = "Выберите тип чата.",
                ),
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !isProcessing,
                    onClick = onCreatePersonalChat,
                ) {
                    Text(chatLocalized(en = "Personal", ru = "Личный"))
                }
                TextButton(
                    enabled = !isProcessing,
                    onClick = onCreateGroupChat,
                ) {
                    Text(chatLocalized(en = "Group", ru = "Групповой"))
                }
            }
        },
    )
}
