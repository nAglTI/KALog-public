package org.debs.kalog.feature.chat.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import mayday_chat.feature.chat.generated.resources.Res
import mayday_chat.feature.chat.generated.resources.*
import org.debs.kalog.feature.chat.data.preferences.AppThemeMode
import org.debs.kalog.feature.chat.presentation.text.UiText
import org.debs.kalog.feature.chat.presentation.text.asString
import org.jetbrains.compose.resources.stringResource

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
        Surface(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onBackClick),
            shape = MaterialTheme.shapes.medium,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBackIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            text = stringResource(Res.string.settings),
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
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.nickname),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            OutlinedTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(Res.string.enter_nickname))
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSaved) {
                    Text(
                        text = stringResource(Res.string.saved),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                if (isError) {
                    Text(
                        text = stringResource(Res.string.could_not_send),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                Button(
                    onClick = onSaveClick,
                    enabled = isSaveEnabled,
                    shape = CircleShape,
                ) {
                    Text(stringResource(Res.string.save))
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
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.your_uuid),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (currentUserId.isBlank()) {
                    stringResource(Res.string.uuid_initializing)
                } else {
                    currentUserId
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(
                        enabled = currentUserId.isNotBlank(),
                        onClick = onCopyClick,
                    ),
                shape = MaterialTheme.shapes.medium,
                color = if (currentUserId.isNotBlank()) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    text = stringResource(Res.string.copy_uuid),
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
    message: UiText?,
    error: UiText?,
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
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.account_backup),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.account_backup_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (deviceBoundKeyCount > 0) {
                Text(
                    text = stringResource(Res.string.device_bound_backup_warning, deviceBoundKeyCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = backupPassword,
                onValueChange = onBackupPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.new_backup_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.large,
            )
            OutlinedTextField(
                value = backupPasswordConfirmation,
                onValueChange = onBackupPasswordConfirmationChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.repeat_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.large,
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
                    shape = CircleShape,
                ) {
                    Text(
                        if (isExporting) {
                            stringResource(Res.string.creating)
                        } else {
                            stringResource(Res.string.create_backup)
                        },
                    )
                }
                Button(
                    onClick = onShareClick,
                    enabled = canShare,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Text(stringResource(Res.string.share))
                }
            }
            if (lastBackupFileName != null) {
                Text(
                    text = if (isStale) {
                        stringResource(Res.string.last_backup_stale, lastBackupFileName)
                    } else {
                        stringResource(Res.string.last_backup_current, lastBackupFileName)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isStale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.import_backup),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.import_backup_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = importPassword,
                onValueChange = onImportPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.backup_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.large,
            )
            OutlinedButton(
                onClick = onImportClick,
                enabled = importPassword.isNotBlank() && !isImporting,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
            ) {
                Text(
                    if (isImporting) {
                        stringResource(Res.string.importing)
                    } else {
                        stringResource(Res.string.choose_backup_file)
                    },
                )
            }
            message?.let {
                Text(
                    text = it.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let {
                Text(
                    text = it.asString(),
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
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.theme),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppThemeMode.entries.forEach { mode ->
                    val selected = mode == themeMode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onThemeModeChange(mode) },
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            Color.Transparent
                        },
                    ) {
                        Text(
                            text = mode.label(),
                            modifier = Modifier.padding(vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
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
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                    text = stringResource(Res.string.autostart),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(Res.string.autostart_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isError) {
                    Text(
                        text = stringResource(Res.string.autostart_error),
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
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                    text = stringResource(Res.string.debug_mode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(Res.string.debug_mode_description),
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
    message: UiText?,
    onRetentionDaysChange: (String) -> Unit,
    onClearAllClick: () -> Unit,
    onClearOldClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.media_cache),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.media_cache_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = retentionDays,
                onValueChange = onRetentionDaysChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(stringResource(Res.string.clear_files_older_than_days))
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
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
                    shape = CircleShape,
                ) {
                    Text(stringResource(Res.string.clear_old))
                }
                Button(
                    onClick = onClearAllClick,
                    enabled = !isClearing,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(Res.string.clear_all))
                }
            }
            if (message != null) {
                Text(
                    text = message.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppThemeMode.label(): String {
    return when (this) {
        AppThemeMode.System -> stringResource(Res.string.theme_system)
        AppThemeMode.Light -> stringResource(Res.string.theme_light)
        AppThemeMode.Dark -> stringResource(Res.string.theme_dark)
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
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(
                text = stringResource(Res.string.wipe_app),
                modifier = Modifier.padding(vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
