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
import org.debs.kalog.feature.chat.domain.usecase.RequestChatAttachmentDownloadUseCase
import org.debs.kalog.feature.chat.domain.usecase.SendChatMessageUseCase
import org.debs.kalog.feature.chat.localization.chatLocalized

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
    private val requestChatAttachmentDownloadUseCase: RequestChatAttachmentDownloadUseCase,
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
    private var queuedAttachmentDrafts: List<ChatAttachment> = emptyList()

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
            ChatDetailsEvent.AttachFileClicked -> notifyAttachmentPickerPending(attachFilePendingMessage())
            ChatDetailsEvent.PickImageClicked -> notifyAttachmentPickerPending(pickImagePendingMessage())
            ChatDetailsEvent.RecordVoiceClicked -> notifyAttachmentPickerPending(recordVoicePendingMessage())
            is ChatDetailsEvent.AttachmentDraftSelected -> prepareAttachment(event.attachment)
            is ChatDetailsEvent.AttachmentDraftsSelected -> prepareAttachments(event.attachments)
            is ChatDetailsEvent.RemoveAttachmentDraft -> removeAttachment(event.attachmentId)
            is ChatDetailsEvent.AttachmentDownloadClicked -> requestAttachmentDownload(event.attachmentId)
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
        if (state.value.isPreparingAttachment) {
            viewModelScope.launch {
                _effect.emit(ChatDetailsEffect.ShowMessage(waitAttachmentsUploadMessage()))
            }
            return
        }

        sendMessageJob = viewModelScope.launch {
            _state.update { it.copy(isSendingMessage = true) }
            try {
                val attachmentBatches = attachments.chunked(MAX_ATTACHMENTS_PER_MESSAGE)
                val wasSent = if (attachmentBatches.isEmpty()) {
                    sendChatMessageUseCase(chatId, draft, emptyList())
                } else {
                    attachmentBatches.mapIndexed { index, batch ->
                        sendChatMessageUseCase(
                            chatId = chatId,
                            plainText = if (index == 0) draft else "",
                            attachments = batch,
                        )
                    }.any { sent -> sent }
                }
                if (wasSent) {
                    _state.update { it.copy(draft = "", pendingAttachments = emptyList()) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _effect.emit(ChatDetailsEffect.ShowError(sendMessageError()))
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
        prepareAttachments(listOf(attachment))
    }

    private fun prepareAttachments(attachments: List<ChatAttachment>) {
        val attachmentsToPrepare = attachments.filter { attachment ->
            attachment.id.isNotBlank() && attachment.name.isNotBlank()
        }
        if (attachmentsToPrepare.isEmpty()) return
        if (state.value.isPendingInvitation) return
        if (state.value.isPreparingAttachment || prepareAttachmentJob?.isActive == true) {
            queuedAttachmentDrafts = (queuedAttachmentDrafts + attachmentsToPrepare)
                .distinctBy { attachment -> attachment.id }
            return
        }

        prepareAttachmentJob = viewModelScope.launch {
            _state.update { it.copy(isPreparingAttachment = true) }
            var failedAttachmentCount = 0
            var firstFailureMessage: String? = null
            try {
                attachmentsToPrepare.forEach { attachment ->
                    try {
                        _state.update {
                            it.copy(
                                attachmentUploadProgress = AttachmentUploadProgress(
                                    fileName = attachment.name,
                                    bytesSent = 0L,
                                    totalBytes = attachment.contentBytes?.size?.toLong() ?: attachment.sizeBytes ?: 0L,
                                ),
                            )
                        }
                        val prepared = prepareChatAttachmentUseCase(chatId, attachment) { bytesSent, totalBytes ->
                            _state.update {
                                it.copy(
                                    attachmentUploadProgress = AttachmentUploadProgress(
                                        fileName = attachment.name,
                                        bytesSent = bytesSent,
                                        totalBytes = totalBytes,
                                    ),
                                )
                            }
                        }
                        _state.update { current ->
                            current.copy(
                                pendingAttachments = (current.pendingAttachments + prepared)
                                    .distinctBy { draft -> draft.attachment.id },
                            )
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        failedAttachmentCount += 1
                        if (firstFailureMessage == null) {
                            firstFailureMessage = error.message?.takeIf(String::isNotBlank)
                                ?: error::class.simpleName
                                ?: chatLocalized(en = "unknown error", ru = "неизвестная ошибка")
                        }
                    }
                }
                if (failedAttachmentCount > 0) {
                    val message = firstFailureMessage?.let { reason ->
                        "${prepareAttachmentError()} $reason"
                    } ?: prepareAttachmentError()
                    _effect.emit(ChatDetailsEffect.ShowError(message))
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                _state.update {
                    it.copy(
                        isPreparingAttachment = false,
                        attachmentUploadProgress = null,
                    )
                }
                val queuedDrafts = queuedAttachmentDrafts
                queuedAttachmentDrafts = emptyList()
                if (queuedDrafts.isNotEmpty()) {
                    prepareAttachments(queuedDrafts)
                }
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

    private fun requestAttachmentDownload(attachmentId: String) {
        viewModelScope.launch {
            try {
                requestChatAttachmentDownloadUseCase(chatId, attachmentId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _effect.emit(ChatDetailsEffect.ShowError(downloadAttachmentError()))
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

    private companion object {
        private const val MAX_ATTACHMENTS_PER_MESSAGE = 10

        private fun sendMessageError(): String = chatLocalized(
            en = "Could not encrypt or send the message. Nothing was sent.",
            ru = "Не удалось зашифровать или отправить сообщение. Ничего не отправлено.",
        )

        private fun prepareAttachmentError(): String = chatLocalized(
            en = "Could not prepare or upload one or more attachments.",
            ru = "Не удалось подготовить или загрузить одно или несколько вложений.",
        )

        private fun downloadAttachmentError(): String = chatLocalized(
            en = "Could not start downloading the attachment.",
            ru = "Не удалось начать скачивание вложения.",
        )

        private fun waitAttachmentsUploadMessage(): String = chatLocalized(
            en = "Wait until attachments finish uploading.",
            ru = "Дождитесь окончания загрузки вложений.",
        )

        private fun attachFilePendingMessage(): String = chatLocalized(
            en = "File picking is unavailable on this platform or was cancelled.",
            ru = "Выбор файла недоступен на этой платформе или был отменён.",
        )

        private fun pickImagePendingMessage(): String = chatLocalized(
            en = "Image picking is unavailable on this platform or was cancelled.",
            ru = "Выбор изображения недоступен на этой платформе или был отменён.",
        )

        private fun recordVoicePendingMessage(): String = chatLocalized(
            en = "Voice recording on this platform requires a native recorder.",
            ru = "Для записи голоса на этой платформе нужен нативный рекордер.",
        )
    }
}
