package org.debs.kalog.feature.chat.presentation.settings

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.debs.kalog.feature.chat.data.preferences.AppThemeMode
import org.debs.kalog.feature.chat.localization.chatLocalized

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBackClick: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onSaveNicknameClick: () -> Unit,
    onCopyUserIdClick: () -> Unit,
    onClearDataClick: () -> Unit,
    onDebugModeToggle: (Boolean) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onDesktopAutostartToggle: (Boolean) -> Unit,
    onClearMediaCacheClick: () -> Unit,
    onMediaCacheRetentionDaysChange: (String) -> Unit,
    onClearOldMediaCacheClick: () -> Unit,
    onBackupPasswordChange: (String) -> Unit,
    onBackupPasswordConfirmationChange: (String) -> Unit,
    onExportAccountBackupClick: () -> Unit,
    onShareAccountBackupClick: () -> Unit,
    onImportBackupPasswordChange: (String) -> Unit,
    onImportAccountBackupClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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
            SettingsAccountBackupSection(
                backupPassword = state.backupPassword,
                backupPasswordConfirmation = state.backupPasswordConfirmation,
                importPassword = state.importBackupPassword,
                lastBackupFileName = state.lastAccountBackupFileName,
                canExport = state.canExportAccountBackup,
                canShare = state.accountBackupCanShare,
                isStale = state.accountBackupStale,
                isExporting = state.isExportingAccountBackup,
                isImporting = state.isImportingAccountBackup,
                deviceBoundKeyCount = state.accountBackupDeviceBoundKeyCount,
                message = state.accountBackupMessage,
                error = state.accountBackupError,
                onBackupPasswordChange = onBackupPasswordChange,
                onBackupPasswordConfirmationChange = onBackupPasswordConfirmationChange,
                onExportClick = onExportAccountBackupClick,
                onShareClick = onShareAccountBackupClick,
                onImportPasswordChange = onImportBackupPasswordChange,
                onImportClick = onImportAccountBackupClick,
            )
            Spacer(modifier = Modifier.height(32.dp))
            SettingsThemeSection(
                themeMode = state.themeMode,
                onThemeModeChange = onThemeModeChange,
            )
            if (state.desktopAutostartSupported) {
                Spacer(modifier = Modifier.height(32.dp))
                SettingsDesktopAutostartSection(
                    enabled = state.desktopAutostartEnabled,
                    isError = state.desktopAutostartError,
                    onToggle = onDesktopAutostartToggle,
                )
            }
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
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBackIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = chatLocalized(en = "Settings", ru = "Настройки"),
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
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
                text = chatLocalized(en = "Nickname", ru = "Никнейм"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            OutlinedTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(chatLocalized(en = "Enter nickname", ru = "Введите никнейм"))
                },
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
                        text = chatLocalized(en = "Saved", ru = "Сохранено"),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF16A34A),
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                if (isError) {
                    Text(
                        text = chatLocalized(en = "Could not send", ru = "Не удалось отправить"),
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
                    Text(chatLocalized(en = "Save", ru = "Сохранить"))
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
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
                text = chatLocalized(en = "Your UUID", ru = "Ваш UUID"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (currentUserId.isBlank()) {
                    chatLocalized(
                        en = "UUID is initializing...",
                        ru = "UUID инициализируется...",
                    )
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
                    text = chatLocalized(en = "Copy UUID", ru = "Копировать UUID"),
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
private fun SettingsAccountBackupSection(
    backupPassword: String,
    backupPasswordConfirmation: String,
    importPassword: String,
    lastBackupFileName: String?,
    canExport: Boolean,
    canShare: Boolean,
    isStale: Boolean,
    isExporting: Boolean,
    isImporting: Boolean,
    deviceBoundKeyCount: Int,
    message: String?,
    error: String?,
    onBackupPasswordChange: (String) -> Unit,
    onBackupPasswordConfirmationChange: (String) -> Unit,
    onExportClick: () -> Unit,
    onShareClick: () -> Unit,
    onImportPasswordChange: (String) -> Unit,
    onImportClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
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
                text = chatLocalized(en = "Account backup", ru = "Резервная копия аккаунта"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = chatLocalized(
                    en = "The backup contains your UUID and saved encryption keys. It is protected with the password below, so keep both the file and the password safe.",
                    ru = "Резервная копия содержит ваш UUID и сохранённые ключи шифрования. Она защищена паролем ниже, поэтому храните и файл, и пароль в безопасности.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (deviceBoundKeyCount > 0) {
                Text(
                    text = chatLocalized(
                        en = "This device has platform-bound keys: $deviceBoundKeyCount. They can only be reused where those system keys still exist.",
                        ru = "На этом устройстве есть ключи, привязанные к платформе: $deviceBoundKeyCount. Их можно повторно использовать только там, где эти системные ключи ещё существуют.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = backupPassword,
                onValueChange = onBackupPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(chatLocalized(en = "New backup password", ru = "Новый пароль копии")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = backupPasswordConfirmation,
                onValueChange = onBackupPasswordConfirmationChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(chatLocalized(en = "Repeat password", ru = "Повторите пароль")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onExportClick,
                    enabled = canExport,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (isExporting) {
                            chatLocalized(en = "Creating...", ru = "Создание...")
                        } else {
                            chatLocalized(en = "Create backup", ru = "Создать копию")
                        },
                    )
                }
                Button(
                    onClick = onShareClick,
                    enabled = canShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(chatLocalized(en = "Share", ru = "Поделиться"))
                }
            }
            if (lastBackupFileName != null) {
                Text(
                    text = if (isStale) {
                        chatLocalized(
                            en = "Last backup is stale: $lastBackupFileName",
                            ru = "Последняя копия устарела: $lastBackupFileName",
                        )
                    } else {
                        chatLocalized(
                            en = "Last backup is current: $lastBackupFileName",
                            ru = "Последняя копия актуальна: $lastBackupFileName",
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isStale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = chatLocalized(en = "Import backup", ru = "Импорт копии"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = chatLocalized(
                    en = "Import replaces local account keys with the encrypted file contents. Use it before creating chats on a new device.",
                    ru = "Импорт заменит локальные ключи аккаунта содержимым зашифрованного файла. Используйте его перед созданием чатов на новом устройстве.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = importPassword,
                onValueChange = onImportPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(chatLocalized(en = "Backup password", ru = "Пароль от копии")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
            )
            OutlinedButton(
                onClick = onImportClick,
                enabled = importPassword.isNotBlank() && !isImporting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    if (isImporting) {
                        chatLocalized(en = "Importing...", ru = "Импорт...")
                    } else {
                        chatLocalized(en = "Choose backup file", ru = "Выбрать файл копии")
                    },
                )
            }
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SettingsThemeSection(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
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
                text = chatLocalized(en = "Theme", ru = "Тема"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppThemeMode.entries.forEach { mode ->
                    val selected = mode == themeMode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onThemeModeChange(mode) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                    ) {
                        Text(
                            text = mode.label(),
                            modifier = Modifier.padding(vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDesktopAutostartSection(
    enabled: Boolean,
    isError: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = chatLocalized(en = "Autostart", ru = "Автозапуск"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = chatLocalized(
                        en = "Start Mayday Chat when signing in",
                        ru = "Запускать Mayday Chat при входе в систему",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isError) {
                    Text(
                        text = chatLocalized(
                            en = "Could not update system autostart.",
                            ru = "Не удалось обновить системный автозапуск.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
            )
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
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
                    text = chatLocalized(en = "Debug mode", ru = "Режим отладки"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = chatLocalized(
                        en = "Show all service messages",
                        ru = "Показывать все сервисные сообщения",
                    ),
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
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
                text = chatLocalized(en = "Media cache", ru = "Кэш медиа"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = chatLocalized(
                    en = "Decrypted files are stored locally for quick previews.",
                    ru = "Расшифрованные файлы хранятся локально для быстрого предпросмотра.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = retentionDays,
                onValueChange = onRetentionDaysChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(chatLocalized(en = "Clear files older than, days", ru = "Очищать файлы старше, дней"))
                },
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
                    Text(chatLocalized(en = "Clear old", ru = "Очистить старые"))
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
                    Text(chatLocalized(en = "Clear all", ru = "Очистить всё"))
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

private fun AppThemeMode.label(): String {
    return when (this) {
        AppThemeMode.System -> chatLocalized(en = "System", ru = "Системная")
        AppThemeMode.Light -> chatLocalized(en = "Light", ru = "Светлая")
        AppThemeMode.Dark -> chatLocalized(en = "Dark", ru = "Тёмная")
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
                text = chatLocalized(en = "Wipe app", ru = "Стереть приложение"),
                modifier = Modifier.padding(vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
