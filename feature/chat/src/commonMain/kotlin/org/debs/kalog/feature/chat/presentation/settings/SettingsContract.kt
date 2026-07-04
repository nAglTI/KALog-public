package org.debs.kalog.feature.chat.presentation.settings

import org.debs.kalog.feature.chat.data.preferences.AppThemeMode
import org.debs.kalog.feature.chat.presentation.text.UiText

data class SettingsUiState(
    val nickname: String = "",
    val savedNickname: String = "",
    val currentUserId: String = "",
    val isClearingData: Boolean = false,
    val debugMode: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.System,
    val desktopAutostartSupported: Boolean = false,
    val desktopAutostartEnabled: Boolean = true,
    val desktopAutostartError: Boolean = false,
    val mediaCacheRetentionDays: String = "7",
    val isClearingMediaCache: Boolean = false,
    val mediaCacheMessage: UiText? = null,
    val backupPassword: String = "",
    val backupPasswordConfirmation: String = "",
    val importBackupPassword: String = "",
    val isExportingAccountBackup: Boolean = false,
    val isImportingAccountBackup: Boolean = false,
    val lastAccountBackupFileName: String? = null,
    val accountBackupCanShare: Boolean = false,
    val accountBackupStale: Boolean = false,
    val accountBackupDeviceBoundKeyCount: Int = 0,
    val accountBackupMessage: UiText? = null,
    val accountBackupError: UiText? = null,
    val nicknameSaved: Boolean = false,
    val nicknameSaveError: Boolean = false,
) {
    val isNicknameChanged: Boolean
        get() = nickname.trim() != savedNickname

    val canExportAccountBackup: Boolean
        get() = currentUserId.isNotBlank() &&
            backupPassword.length >= 8 &&
            backupPassword == backupPasswordConfirmation &&
            !isExportingAccountBackup

    val canImportAccountBackup: Boolean
        get() = importBackupPassword.isNotBlank() && !isImportingAccountBackup
}

sealed interface SettingsEvent {
    data class NicknameChanged(val nickname: String) : SettingsEvent

    data object SaveNicknameClicked : SettingsEvent

    data object CopyUserIdClicked : SettingsEvent

    data object ClearAllDataConfirmed : SettingsEvent

    data class DebugModeToggled(val enabled: Boolean) : SettingsEvent

    data class ThemeModeSelected(val themeMode: AppThemeMode) : SettingsEvent

    data class DesktopAutostartToggled(val enabled: Boolean) : SettingsEvent

    data object ClearMediaCacheClicked : SettingsEvent

    data class MediaCacheRetentionDaysChanged(val days: String) : SettingsEvent

    data object ClearOldMediaCacheClicked : SettingsEvent

    data class BackupPasswordChanged(val password: String) : SettingsEvent

    data class BackupPasswordConfirmationChanged(val password: String) : SettingsEvent

    data object ExportAccountBackupClicked : SettingsEvent

    data object ShareAccountBackupClicked : SettingsEvent

    data class ImportBackupPasswordChanged(val password: String) : SettingsEvent

    data object ImportAccountBackupClicked : SettingsEvent
}

sealed interface SettingsEffect {
    data class CopyUserId(val userId: String) : SettingsEffect

    data object AppDataCleared : SettingsEffect

    data object AccountImported : SettingsEffect
}
