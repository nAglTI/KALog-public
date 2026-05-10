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
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.usecase.AcceptChatInvitationUseCase
import org.debs.kalog.feature.chat.domain.usecase.DeclineChatInvitationUseCase
import org.debs.kalog.feature.chat.domain.usecase.InviteUserToChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.LoadMoreChatMessagesUseCase
import org.debs.kalog.feature.chat.domain.usecase.ObserveChatDetailsUseCase
import org.debs.kalog.feature.chat.domain.usecase.OpenChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.PrepareChatAttachmentUseCase
import org.debs.kalog.feature.chat.domain.usecase.SendChatMessageUseCase

class ChatDetailsViewModel(
    private val chatId: String,
    private val observeChatDetailsUseCase: ObserveChatDetailsUseCase,
    private val openChatUseCase: OpenChatUseCase,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val prepareChatAttachmentUseCase: PrepareChatAttachmentUseCase,
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
    private var prepareAttachmentJob: Job? = null
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
            ChatDetailsEvent.AttachFileClicked -> notifyAttachmentPickerPending(ATTACH_FILE_PENDING)
            ChatDetailsEvent.PickImageClicked -> notifyAttachmentPickerPending(PICK_IMAGE_PENDING)
            ChatDetailsEvent.RecordVoiceClicked -> notifyAttachmentPickerPending(RECORD_VOICE_PENDING)
            is ChatDetailsEvent.AttachmentDraftSelected -> prepareAttachment(event.attachment)
            is ChatDetailsEvent.RemoveAttachmentDraft -> removeAttachment(event.attachmentId)
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
        val attachments = state.value.pendingAttachments
        if ((draft.isBlank() && attachments.isEmpty()) || sendMessageJob?.isActive == true) return

        sendMessageJob = viewModelScope.launch {
            _state.update { it.copy(isSendingMessage = true) }
            try {
                val wasSent = sendChatMessageUseCase(chatId, draft, attachments)
                if (wasSent) {
                    _state.update { it.copy(draft = "", pendingAttachments = emptyList()) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _effect.emit(ChatDetailsEffect.ShowError(SEND_MESSAGE_ERROR))
            } finally {
                _state.update { it.copy(isSendingMessage = false) }
            }
        }
    }

    private fun notifyAttachmentPickerPending(message: String) {
        viewModelScope.launch {
            _effect.emit(ChatDetailsEffect.ShowMessage(message))
        }
    }

    private fun prepareAttachment(attachment: ChatAttachment) {
        if (state.value.isPreparingAttachment || state.value.isPendingInvitation || prepareAttachmentJob?.isActive == true) return

        prepareAttachmentJob = viewModelScope.launch {
            _state.update { it.copy(isPreparingAttachment = true) }
            try {
                val prepared = prepareChatAttachmentUseCase(chatId, attachment)
                _state.update { current ->
                    current.copy(
                        pendingAttachments = (current.pendingAttachments + prepared)
                            .distinctBy { draft -> draft.attachment.id },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _effect.emit(ChatDetailsEffect.ShowError(PREPARE_ATTACHMENT_ERROR))
            } finally {
                _state.update { it.copy(isPreparingAttachment = false) }
            }
        }
    }

    private fun removeAttachment(attachmentId: String) {
        _state.update { current ->
            current.copy(
                pendingAttachments = current.pendingAttachments.filterNot { attachment ->
                    attachment.attachment.id == attachmentId
                },
            )
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

    private companion object {
        private const val SEND_MESSAGE_ERROR = "Couldn't encrypt or send the message. Nothing was sent."
        private const val PREPARE_ATTACHMENT_ERROR = "Couldn't prepare the attachment encryption key."
        private const val ATTACH_FILE_PENDING = "File sending is prepared locally. Backend upload contract is still pending."
        private const val PICK_IMAGE_PENDING = "Gallery images are prepared locally. Backend upload contract is still pending."
        private const val RECORD_VOICE_PENDING = "Voice recording is prepared locally. Backend upload contract is still pending."
    }
}
