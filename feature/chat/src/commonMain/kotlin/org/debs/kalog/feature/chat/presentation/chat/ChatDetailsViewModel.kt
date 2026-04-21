package org.debs.kalog.feature.chat.presentation.chat

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
import org.debs.kalog.feature.chat.domain.usecase.AcceptChatInvitationUseCase
import org.debs.kalog.feature.chat.domain.usecase.DeclineChatInvitationUseCase
import org.debs.kalog.feature.chat.domain.usecase.InviteUserToChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.LoadMoreChatMessagesUseCase
import org.debs.kalog.feature.chat.domain.usecase.ObserveChatDetailsUseCase
import org.debs.kalog.feature.chat.domain.usecase.OpenChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.SendChatMessageUseCase

class ChatDetailsViewModel(
    private val chatId: String,
    private val observeChatDetailsUseCase: ObserveChatDetailsUseCase,
    private val openChatUseCase: OpenChatUseCase,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val loadMoreChatMessagesUseCase: LoadMoreChatMessagesUseCase,
    private val inviteUserToChatUseCase: InviteUserToChatUseCase,
    private val acceptChatInvitationUseCase: AcceptChatInvitationUseCase,
    private val declineChatInvitationUseCase: DeclineChatInvitationUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatDetailsUiState())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<ChatDetailsEffect>(extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    private var sendMessageJob: Job? = null
    private var loadMoreMessagesJob: Job? = null
    private var inviteUserJob: Job? = null
    private var invitationJob: Job? = null

    init {
        openChat()
        observeChat()
    }

    fun onEvent(event: ChatDetailsEvent) {
        when (event) {
            is ChatDetailsEvent.DraftChanged -> {
                _state.update { it.copy(draft = event.value) }
            }
            ChatDetailsEvent.SendClicked -> sendMessage()
            ChatDetailsEvent.LoadMoreMessagesClicked -> loadMoreMessages()
            is ChatDetailsEvent.InviteUserConfirmed -> inviteUserToChat(event.userId)
            ChatDetailsEvent.AcceptInvitationClicked -> acceptInvitation()
            ChatDetailsEvent.DeclineInvitationClicked -> declineInvitation()
        }
    }

    private fun openChat() {
        viewModelScope.launch {
            try {
                openChatUseCase(chatId)
                loadMoreMessages()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Keep cached data visible even if opening the chat fails.
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
                        subtitle = chat.subtitle,
                        type = chat.type,
                        avatar = chat.avatar,
                        messages = chat.messages,
                        hasMoreMessages = chat.hasMoreMessages,
                        isLoadingMoreMessages = chat.isLoadingMoreMessages,
                        invitationStatus = chat.invitationStatus,
                    )
                }
            }
        }
    }

    private fun sendMessage() {
        val draft = state.value.draft
        if (draft.isBlank() || sendMessageJob?.isActive == true) return

        sendMessageJob = viewModelScope.launch {
            _state.update { it.copy(isSendingMessage = true) }
            try {
                val wasSent = sendChatMessageUseCase(chatId, draft)
                if (wasSent) {
                    _state.update { it.copy(draft = "") }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // TODO surface send-message errors in the UI when the product error model is ready.
            } finally {
                _state.update { it.copy(isSendingMessage = false) }
            }
        }
    }

    private fun loadMoreMessages() {
        val currentState = state.value
        if (currentState.isLoadingMoreMessages || !currentState.hasMoreMessages || loadMoreMessagesJob?.isActive == true) return

        loadMoreMessagesJob = viewModelScope.launch {
            try {
                loadMoreChatMessagesUseCase(chatId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // TODO surface pagination errors in the UI when the product error model is ready.
            }
        }
    }

    private fun acceptInvitation() {
        if (state.value.isProcessingInvitation || invitationJob?.isActive == true) return

        invitationJob = viewModelScope.launch {
            _state.update { it.copy(isProcessingInvitation = true) }
            try {
                acceptChatInvitationUseCase(chatId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // TODO surface invitation errors in the UI when the product error model is ready.
            } finally {
                _state.update { it.copy(isProcessingInvitation = false) }
            }
        }
    }

    private fun declineInvitation() {
        if (state.value.isProcessingInvitation || invitationJob?.isActive == true) return

        invitationJob = viewModelScope.launch {
            _state.update { it.copy(isProcessingInvitation = true) }
            try {
                declineChatInvitationUseCase(chatId)
                _effect.emit(ChatDetailsEffect.InvitationDeclined)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // TODO surface invitation errors in the UI when the product error model is ready.
            } finally {
                _state.update { it.copy(isProcessingInvitation = false) }
            }
        }
    }

    private fun inviteUserToChat(userId: String) {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isEmpty() || state.value.isInvitingUser || !state.value.canInviteUsers) return

        inviteUserJob?.cancel()
        inviteUserJob = viewModelScope.launch {
            _state.update { it.copy(isInvitingUser = true) }
            try {
                inviteUserToChatUseCase(chatId, normalizedUserId)
                _effect.emit(ChatDetailsEffect.InviteUserCompleted)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // TODO surface invite-user errors in the UI when the product error model is ready.
            } finally {
                _state.update { it.copy(isInvitingUser = false) }
            }
        }
    }
}
