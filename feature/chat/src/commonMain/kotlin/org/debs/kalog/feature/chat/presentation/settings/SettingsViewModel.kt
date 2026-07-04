package org.debs.kalog.feature.chat.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.debs.kalog.feature.chat.data.preferences.AppThemeMode
import org.debs.kalog.feature.chat.data.account.AccountBackupManager
import org.debs.kalog.feature.chat.data.preferences.ChatPreferencesDataSource
import org.debs.kalog.feature.chat.domain.usecase.BroadcastNicknameUseCase
import org.debs.kalog.feature.chat.domain.usecase.ClearAllChatDataUseCase
import org.debs.kalog.feature.chat.domain.usecase.ClearCachedChatAttachmentsUseCase
import org.debs.kalog.feature.chat.domain.usecase.ClearOldCachedChatAttachmentsUseCase
import org.debs.kalog.feature.chat.domain.usecase.GetCurrentUserIdUseCase
import org.debs.kalog.feature.chat.presentation.text.uiText
import mayday_chat.feature.chat.generated.resources.Res
import mayday_chat.feature.chat.generated.resources.*
import org.debs.kalog.feature.chat.presentation.platform.DesktopAutostartManager

class SettingsViewModel(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val clearAllChatDataUseCase: ClearAllChatDataUseCase,
    private val chatPreferencesDataSource: ChatPreferencesDataSource,
    private val broadcastNicknameUseCase: BroadcastNicknameUseCase,
    private val clearCachedChatAttachmentsUseCase: ClearCachedChatAttachmentsUseCase,
    private val clearOldCachedChatAttachmentsUseCase: ClearOldCachedChatAttachmentsUseCase,
    private val desktopAutostartManager: DesktopAutostartManager,
    private val accountBackupManager: AccountBackupManager,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    private var clearDataJob: Job? = null
    private var clearMediaCacheJob: Job? = null

    init {
        loadData()
        observeAccountBackupStatus()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.NicknameChanged -> {
                _state.update { it.copy(nickname = event.nickname, nicknameSaved = false, nicknameSaveError = false) }
            }
            SettingsEvent.SaveNicknameClicked -> saveNickname()
            SettingsEvent.CopyUserIdClicked -> handleCopyUserId()
            SettingsEvent.ClearAllDataConfirmed -> clearAllData()
            is SettingsEvent.DebugModeToggled -> toggleDebugMode(event.enabled)
            is SettingsEvent.ThemeModeSelected -> selectThemeMode(event.themeMode)
            is SettingsEvent.DesktopAutostartToggled -> toggleDesktopAutostart(event.enabled)
            SettingsEvent.ClearMediaCacheClicked -> clearMediaCache()
            is SettingsEvent.MediaCacheRetentionDaysChanged -> {
                _state.update { current ->
                    current.copy(
                        mediaCacheRetentionDays = event.days.filter(Char::isDigit).take(4),
                        mediaCacheMessage = null,
                    )
                }
            }
            SettingsEvent.ClearOldMediaCacheClicked -> clearOldMediaCache()
            is SettingsEvent.BackupPasswordChanged -> {
                _state.update {
                    it.copy(
                        backupPassword = event.password,
                        accountBackupMessage = null,
                        accountBackupError = null,
                    )
                }
            }
            is SettingsEvent.BackupPasswordConfirmationChanged -> {
                _state.update {
                    it.copy(
                        backupPasswordConfirmation = event.password,
                        accountBackupMessage = null,
                        accountBackupError = null,
                    )
                }
            }
            SettingsEvent.ExportAccountBackupClicked -> exportAccountBackup()
            SettingsEvent.ShareAccountBackupClicked -> shareAccountBackup()
            is SettingsEvent.ImportBackupPasswordChanged -> {
                _state.update {
                    it.copy(
                        importBackupPassword = event.password,
                        accountBackupMessage = null,
                        accountBackupError = null,
                    )
                }
            }
            SettingsEvent.ImportAccountBackupClicked -> importAccountBackup()
        }
    }

    private fun observeAccountBackupStatus() {
        viewModelScope.launch {
            accountBackupManager.observeStatus().collect { status ->
                _state.update {
                    it.copy(
                        lastAccountBackupFileName = status.lastFileName,
                        accountBackupCanShare = status.canShareLastBackup,
                        accountBackupStale = status.isLastBackupStale,
                        accountBackupDeviceBoundKeyCount = status.deviceBoundPrivateKeyCount,
                    )
                }
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val currentUserId = getCurrentUserIdUseCase().orEmpty()
                val nickname = chatPreferencesDataSource.getNickname()
                val debugMode = chatPreferencesDataSource.isDebugModeEnabled()
                val themeMode = chatPreferencesDataSource.getThemeMode()
                val autostartState = syncDesktopAutostartPreference()
                val mediaCacheRetentionDays = chatPreferencesDataSource.getMediaCacheRetentionDays()
                _state.update {
                    it.copy(
                        currentUserId = currentUserId,
                        nickname = nickname,
                        savedNickname = nickname,
                        debugMode = debugMode,
                        themeMode = themeMode,
                        desktopAutostartSupported = autostartState.supported,
                        desktopAutostartEnabled = autostartState.enabled,
                        desktopAutostartError = autostartState.error,
                        mediaCacheRetentionDays = mediaCacheRetentionDays.toString(),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Keep the UI responsive.
            }
        }
    }

    private fun saveNickname() {
        val trimmed = state.value.nickname.trim()
        if (trimmed == state.value.savedNickname) return

        viewModelScope.launch {
            try {
                chatPreferencesDataSource.saveNickname(trimmed)
                broadcastNicknameUseCase()
                _state.update { it.copy(savedNickname = trimmed, nicknameSaved = true, nicknameSaveError = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update { it.copy(nicknameSaved = false, nicknameSaveError = true) }
            }
        }
    }

    private fun handleCopyUserId() {
        val currentUserId = state.value.currentUserId
        if (currentUserId.isBlank()) return
        _effect.tryEmit(SettingsEffect.CopyUserId(currentUserId))
    }

    private fun toggleDebugMode(enabled: Boolean) {
        _state.update { it.copy(debugMode = enabled) }
        viewModelScope.launch {
            runCatching { chatPreferencesDataSource.setDebugModeEnabled(enabled) }
        }
    }

    private suspend fun syncDesktopAutostartPreference(): DesktopAutostartState {
        val preferenceEnabled = chatPreferencesDataSource.isDesktopAutostartEnabled()
        if (!desktopAutostartManager.isSupported) {
            return DesktopAutostartState(supported = false, enabled = preferenceEnabled, error = false)
        }

        return try {
            val actualEnabled = desktopAutostartManager.isEnabled()
            val applied = when {
                preferenceEnabled && !actualEnabled -> desktopAutostartManager.setEnabled(true)
                !preferenceEnabled && actualEnabled -> desktopAutostartManager.setEnabled(false)
                else -> true
            }
            DesktopAutostartState(
                supported = true,
                enabled = desktopAutostartManager.isEnabled(),
                error = !applied,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            DesktopAutostartState(supported = true, enabled = preferenceEnabled, error = true)
        }
    }

    private fun toggleDesktopAutostart(enabled: Boolean) {
        _state.update {
            it.copy(
                desktopAutostartEnabled = enabled,
                desktopAutostartError = false,
            )
        }
        viewModelScope.launch {
            try {
                chatPreferencesDataSource.setDesktopAutostartEnabled(enabled)
                if (!desktopAutostartManager.isSupported) return@launch

                val applied = desktopAutostartManager.setEnabled(enabled)
                val actualEnabled = desktopAutostartManager.isEnabled()
                _state.update {
                    it.copy(
                        desktopAutostartEnabled = actualEnabled,
                        desktopAutostartError = !applied || actualEnabled != enabled,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update { it.copy(desktopAutostartError = true) }
            }
        }
    }

    private fun selectThemeMode(themeMode: AppThemeMode) {
        _state.update { it.copy(themeMode = themeMode) }
        viewModelScope.launch {
            runCatching { chatPreferencesDataSource.saveThemeMode(themeMode) }
        }
    }

    private fun clearAllData() {
        if (state.value.isClearingData) return

        clearDataJob?.cancel()
        clearDataJob = viewModelScope.launch {
            _state.update { it.copy(isClearingData = true) }
            try {
                clearAllChatDataUseCase()
                _state.value = SettingsUiState()
                _effect.emit(SettingsEffect.AppDataCleared)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update { it.copy(isClearingData = false) }
            }
        }
    }

    private fun clearMediaCache() {
        if (state.value.isClearingMediaCache) return

        clearMediaCacheJob?.cancel()
        clearMediaCacheJob = viewModelScope.launch {
            _state.update { it.copy(isClearingMediaCache = true, mediaCacheMessage = null) }
            try {
                val clearedCount = clearCachedChatAttachmentsUseCase()
                _state.update {
                    it.copy(
                        isClearingMediaCache = false,
                        mediaCacheMessage = uiText(Res.string.cleared_cached_files, clearedCount),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        isClearingMediaCache = false,
                        mediaCacheMessage = uiText(Res.string.could_not_clear_media_cache),
                    )
                }
            }
        }
    }

    private fun clearOldMediaCache() {
        if (state.value.isClearingMediaCache) return

        val retentionDays = state.value.mediaCacheRetentionDays.toIntOrNull()?.coerceAtLeast(0) ?: return
        clearMediaCacheJob?.cancel()
        clearMediaCacheJob = viewModelScope.launch {
            _state.update { it.copy(isClearingMediaCache = true, mediaCacheMessage = null) }
            try {
                chatPreferencesDataSource.saveMediaCacheRetentionDays(retentionDays)
                val clearedCount = clearOldCachedChatAttachmentsUseCase(retentionDays * DAY_MILLIS)
                _state.update {
                    it.copy(
                        isClearingMediaCache = false,
                        mediaCacheRetentionDays = retentionDays.toString(),
                        mediaCacheMessage = uiText(
                            Res.string.cleared_files_older_than_days,
                            retentionDays,
                            clearedCount,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        isClearingMediaCache = false,
                        mediaCacheMessage = uiText(Res.string.could_not_clear_old_media),
                    )
                }
            }
        }
    }

    private fun exportAccountBackup() {
        val current = state.value
        if (!current.canExportAccountBackup) {
            _state.update {
                it.copy(
                    accountBackupError = uiText(Res.string.backup_passwords_must_match),
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isExportingAccountBackup = true,
                    accountBackupMessage = null,
                    accountBackupError = null,
                )
            }
            try {
                val result = accountBackupManager.exportAccount(current.backupPassword)
                accountBackupManager.shareBackup(result.file)
                _state.update {
                    it.copy(
                        isExportingAccountBackup = false,
                        backupPassword = "",
                        backupPasswordConfirmation = "",
                        accountBackupMessage = if (result.deviceBoundPrivateKeyCount > 0) {
                            uiText(Res.string.account_backup_created_with_device_bound_warning)
                        } else {
                            uiText(Res.string.account_backup_created)
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (e: IllegalArgumentException) {
                _state.update {
                    it.copy(
                        isExportingAccountBackup = false,
                        accountBackupError = uiText(Res.string.could_not_create_account_backup),
                    )
                }
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        isExportingAccountBackup = false,
                        accountBackupError = uiText(Res.string.could_not_create_account_backup),
                    )
                }
            }
        }
    }

    private fun shareAccountBackup() {
        if (!state.value.accountBackupCanShare) return
        viewModelScope.launch {
            try {
                val shared = accountBackupManager.shareLastBackup()
                _state.update {
                    it.copy(
                        accountBackupMessage = if (shared) {
                            uiText(Res.string.backup_file_ready_to_send)
                        } else {
                            null
                        },
                        accountBackupError = if (shared) {
                            null
                        } else {
                            uiText(Res.string.create_fresh_backup_before_sending)
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        accountBackupError = uiText(Res.string.could_not_open_backup_file),
                    )
                }
            }
        }
    }

    private fun importAccountBackup() {
        val password = state.value.importBackupPassword
        if (password.isBlank() || state.value.isImportingAccountBackup) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isImportingAccountBackup = true,
                    accountBackupMessage = null,
                    accountBackupError = null,
                )
            }
            try {
                val result = accountBackupManager.importAccountFromPicker(password)
                if (result == null) {
                    _state.update {
                        it.copy(
                            isImportingAccountBackup = false,
                            accountBackupMessage = uiText(Res.string.import_cancelled),
                        )
                    }
                    return@launch
                }
                chatPreferencesDataSource.setAccountOnboardingCompleted(true)
                val importedNickname = chatPreferencesDataSource.getNickname()
                _state.update {
                    it.copy(
                        currentUserId = result.userId,
                        nickname = importedNickname,
                        savedNickname = importedNickname,
                        isImportingAccountBackup = false,
                        importBackupPassword = "",
                        accountBackupMessage = if (result.deviceBoundPrivateKeyCount > 0) {
                            uiText(Res.string.imported_account_with_device_bound_warning, result.userId)
                        } else {
                            uiText(Res.string.imported_account, result.userId)
                        },
                    )
                }
                _effect.emit(SettingsEffect.AccountImported)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        isImportingAccountBackup = false,
                        accountBackupError = importBackupFailedMessage(),
                    )
                }
            }
        }
    }

    private companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

        private fun importBackupFailedMessage() = uiText(Res.string.could_not_import_backup)
    }
}

private data class DesktopAutostartState(
    val supported: Boolean,
    val enabled: Boolean,
    val error: Boolean,
)
