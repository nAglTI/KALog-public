package org.debs.kalog.feature.chat.presentation.chatinfo

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
import org.debs.kalog.feature.chat.domain.usecase.CreateDirectChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.GetChatParticipantsUseCase
import org.debs.kalog.feature.chat.domain.usecase.ObserveChatDetailsUseCase
import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class ChatInfoViewModel(
    private val chatId: String,
    private val getChatParticipantsUseCase: GetChatParticipantsUseCase,
    private val observeChatDetailsUseCase: ObserveChatDetailsUseCase,
    private val createDirectChatUseCase: CreateDirectChatUseCase,
    private val chatRepository: ChatRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatInfoUiState())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<ChatInfoEffect>(extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    private var navigateJob: Job? = null

    init {
        observeChat()
        loadParticipants()
    }

    fun onEvent(event: ChatInfoEvent) {
        when (event) {
            is ChatInfoEvent.TitleChanged -> renameChat(event.newTitle)
            is ChatInfoEvent.ParticipantClicked -> navigateToDirectChat(event.userId)
        }
    }

    private fun renameChat(newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            try {
                chatRepository.renameChatLocally(chatId, trimmed)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // silently ignore rename errors
            }
        }
    }

    private fun observeChat() {
        viewModelScope.launch {
            observeChatDetailsUseCase(chatId).collect { chat ->
                chat ?: return@collect
                _state.update { current ->
                    current.copy(
                        title = chat.title,
                        type = chat.type,
                        avatar = chat.avatar,
                    )
                }
                refreshParticipants()
            }
        }
    }

    private fun loadParticipants() {
        viewModelScope.launch {
            refreshParticipants()
        }
    }

    private suspend fun refreshParticipants() {
        try {
            val participants = getChatParticipantsUseCase(chatId)
            _state.update { current ->
                current.copy(
                    participants = participants.map { participant ->
                        ParticipantUiModel(
                            userId = participant.userId,
                            displayName = participant.displayName,
                            isCurrentUser = participant.isCurrentUser,
                        )
                    },
                    isLoading = false,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun navigateToDirectChat(userId: String) {
        if (navigateJob?.isActive == true || state.value.isCreatingDirectChat) return

        navigateJob = viewModelScope.launch {
            _state.update { it.copy(isCreatingDirectChat = true) }
            try {
                val existingChatId = chatRepository.findDirectChatWith(userId)
                val targetChatId = existingChatId ?: createDirectChatUseCase(userId)
                _effect.emit(ChatInfoEffect.NavigateToChat(targetChatId))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // TODO surface navigation errors
            } finally {
                _state.update { it.copy(isCreatingDirectChat = false) }
            }
        }
    }
}
