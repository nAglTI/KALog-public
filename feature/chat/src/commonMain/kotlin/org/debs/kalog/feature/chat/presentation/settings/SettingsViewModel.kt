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
import org.debs.kalog.feature.chat.data.preferences.ChatPreferencesDataSource
import org.debs.kalog.feature.chat.domain.usecase.BroadcastNicknameUseCase
import org.debs.kalog.feature.chat.domain.usecase.ClearAllChatDataUseCase
import org.debs.kalog.feature.chat.domain.usecase.ClearCachedChatAttachmentsUseCase
import org.debs.kalog.feature.chat.domain.usecase.ClearOldCachedChatAttachmentsUseCase
import org.debs.kalog.feature.chat.domain.usecase.GetCurrentUserIdUseCase

class SettingsViewModel(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val clearAllChatDataUseCase: ClearAllChatDataUseCase,
    private val chatPreferencesDataSource: ChatPreferencesDataSource,
    private val broadcastNicknameUseCase: BroadcastNicknameUseCase,
    private val clearCachedChatAttachmentsUseCase: ClearCachedChatAttachmentsUseCase,
    private val clearOldCachedChatAttachmentsUseCase: ClearOldCachedChatAttachmentsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    private var clearDataJob: Job? = null
    private var clearMediaCacheJob: Job? = null

    init {
        loadData()
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
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val currentUserId = getCurrentUserIdUseCase().orEmpty()
                val nickname = chatPreferencesDataSource.getNickname()
                val debugMode = chatPreferencesDataSource.isDebugModeEnabled()
                val mediaCacheRetentionDays = chatPreferencesDataSource.getMediaCacheRetentionDays()
                _state.update {
                    it.copy(
                        currentUserId = currentUserId,
                        nickname = nickname,
                        savedNickname = nickname,
                        debugMode = debugMode,
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
                        mediaCacheMessage = "Cleared $clearedCount cached files.",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        isClearingMediaCache = false,
                        mediaCacheMessage = "Couldn't clear cached media.",
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
                        mediaCacheMessage = "Cleared $clearedCount files older than $retentionDays days.",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        isClearingMediaCache = false,
                        mediaCacheMessage = "Couldn't clear old media.",
                    )
                }
            }
        }
    }

    private companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
