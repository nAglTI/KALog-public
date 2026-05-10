package org.debs.kalog.feature.chat.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBackClick: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onSaveNicknameClick: () -> Unit,
    onCopyUserIdClick: () -> Unit,
    onClearDataClick: () -> Unit,
    onDebugModeToggle: (Boolean) -> Unit,
    onClearMediaCacheClick: () -> Unit,
    onMediaCacheRetentionDaysChange: (String) -> Unit,
    onClearOldMediaCacheClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF4FAFF),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsHeader(onBackClick = onBackClick)
            Spacer(modifier = Modifier.height(8.dp))
            SettingsNicknameSection(
                nickname = state.nickname,
                onNicknameChange = onNicknameChange,
                onSaveClick = onSaveNicknameClick,
                isSaveEnabled = state.isNicknameChanged,
                isSaved = state.nicknameSaved,
                isError = state.nicknameSaveError,
            )
            Spacer(modifier = Modifier.height(16.dp))
            SettingsUuidSection(
                currentUserId = state.currentUserId,
                onCopyClick = onCopyUserIdClick,
            )
            Spacer(modifier = Modifier.height(32.dp))
            SettingsDebugSection(
                debugMode = state.debugMode,
                onToggle = onDebugModeToggle,
            )
            Spacer(modifier = Modifier.height(32.dp))
            SettingsMediaCacheSection(
                retentionDays = state.mediaCacheRetentionDays,
                isClearing = state.isClearingMediaCache,
                message = state.mediaCacheMessage,
                onRetentionDaysChange = onMediaCacheRetentionDaysChange,
                onClearAllClick = onClearMediaCacheClick,
                onClearOldClick = onClearOldMediaCacheClick,
            )
            Spacer(modifier = Modifier.height(32.dp))
            SettingsWipeSection(
                enabled = !state.isClearingData,
                onClearDataClick = onClearDataClick,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .clickable(onClick = onBackClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBackIos,
                contentDescription = null,
            )
        }
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SettingsNicknameSection(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    isSaveEnabled: Boolean,
    isSaved: Boolean,
    isError: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Nickname",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            OutlinedTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter your nickname") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSaved) {
                    Text(
                        text = "Saved",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF16A34A),
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                if (isError) {
                    Text(
                        text = "Failed to send",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                Button(
                    onClick = onSaveClick,
                    enabled = isSaveEnabled,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun SettingsUuidSection(
    currentUserId: String,
    onCopyClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Your UUID",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (currentUserId.isBlank()) {
                    "UUID is being initialized..."
                } else {
                    currentUserId
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(
                        enabled = currentUserId.isNotBlank(),
                        onClick = onCopyClick,
                    ),
                shape = RoundedCornerShape(18.dp),
                color = if (currentUserId.isNotBlank()) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Text(
                    text = "Copy UUID",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (currentUserId.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SettingsDebugSection(
    debugMode: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Debug mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Show all service messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = debugMode,
                onCheckedChange = onToggle,
            )
        }
    }
}

@Composable
private fun SettingsMediaCacheSection(
    retentionDays: String,
    isClearing: Boolean,
    message: String?,
    onRetentionDaysChange: (String) -> Unit,
    onClearAllClick: () -> Unit,
    onClearOldClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Cached media",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Decrypted files are stored locally for fast previews.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = retentionDays,
                onValueChange = onRetentionDaysChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Clear files older than days") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onClearOldClick,
                    enabled = !isClearing && retentionDays.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Clear old")
                }
                Button(
                    onClick = onClearAllClick,
                    enabled = !isClearing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Clear now")
                }
            }
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsWipeSection(
    enabled: Boolean,
    onClearDataClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onClearDataClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(
                text = "Wipe App",
                modifier = Modifier.padding(vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
