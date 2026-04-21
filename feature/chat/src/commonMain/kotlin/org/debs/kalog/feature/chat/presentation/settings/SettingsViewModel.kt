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
import org.debs.kalog.feature.chat.domain.usecase.GetCurrentUserIdUseCase

class SettingsViewModel(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val clearAllChatDataUseCase: ClearAllChatDataUseCase,
    private val chatPreferencesDataSource: ChatPreferencesDataSource,
    private val broadcastNicknameUseCase: BroadcastNicknameUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    private var clearDataJob: Job? = null

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
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val currentUserId = getCurrentUserIdUseCase().orEmpty()
                val nickname = chatPreferencesDataSource.getNickname()
                val debugMode = chatPreferencesDataSource.isDebugModeEnabled()
                _state.update { it.copy(currentUserId = currentUserId, nickname = nickname, savedNickname = nickname, debugMode = debugMode) }
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
                _state.update { it.copy(savedNickname = trimmed, nicknameSaved = false, nicknameSaveError = true) }
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
}
