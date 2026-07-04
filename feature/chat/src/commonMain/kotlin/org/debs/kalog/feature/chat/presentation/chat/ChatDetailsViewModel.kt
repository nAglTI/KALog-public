package org.debs.kalog.feature.chat.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatMessage
import org.debs.kalog.feature.chat.domain.model.DeliveryStatus
import org.debs.kalog.feature.chat.domain.model.PreparedChatAttachment
import org.debs.kalog.feature.chat.domain.usecase.AcceptChatInvitationUseCase
import org.debs.kalog.feature.chat.domain.usecase.DeclineChatInvitationUseCase
import org.debs.kalog.feature.chat.domain.usecase.InviteUserToChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.LoadMoreChatMessagesUseCase
import org.debs.kalog.feature.chat.domain.usecase.ObserveChatDetailsUseCase
import org.debs.kalog.feature.chat.domain.usecase.OpenChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.PrepareChatAttachmentUseCase
import org.debs.kalog.feature.chat.domain.usecase.RefreshChatMessagesUseCase
import org.debs.kalog.feature.chat.domain.usecase.RequestChatAttachmentDownloadUseCase
import org.debs.kalog.feature.chat.domain.usecase.SendChatMessageUseCase
import org.debs.kalog.feature.chat.presentation.text.UiText
import org.debs.kalog.feature.chat.presentation.text.uiText
import mayday_chat.feature.chat.generated.resources.Res
import mayday_chat.feature.chat.generated.resources.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class ChatDetailsViewModel(
    private val chatId: String,
    private val observeChatDetailsUseCase: ObserveChatDetailsUseCase,
    private val openChatUseCase: OpenChatUseCase,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val prepareChatAttachmentUseCase: PrepareChatAttachmentUseCase,
    private val refreshChatMessagesUseCase: RefreshChatMessagesUseCase,
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

    private val sendMessageQueue = Channel<QueuedOutgoingMessage>(Channel.UNLIMITED)
    private val optimisticMessagesMutex = Mutex()
    private var observedMessages: List<ChatMessage> = emptyList()
    private var optimisticMessages: List<OptimisticChatMessage> = emptyList()
    private var nextOptimisticMessageIndex = 0L
    private var prepareAttachmentJob: Job? = null
    private var refreshMessagesJob: Job? = null
    private var loadMoreMessagesJob: Job? = null
    private var inviteUserJob: Job? = null
    private var invitationJob: Job? = null
    private var queuedAttachmentDrafts: List<ChatAttachment> = emptyList()

    init {
        startSendMessageQueue()
        openChat()
        startRecentMessageRefresh()
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
                optimisticMessagesMutex.withLock {
                    observedMessages = chat.messages
                    reconcileDeliveredOptimisticMessagesLocked()
                    _state.update { current ->
                        current.copy(
                            title = chat.title,
                            subtitle = chat.subtitle,
                            type = chat.type,
                            avatar = chat.avatar,
                            messages = messagesWithOptimisticMessagesLocked(),
                            isSendingMessage = hasSendingOptimisticMessagesLocked(),
                            hasMoreMessages = chat.hasMoreMessages,
                            isLoadingMoreMessages = chat.isLoadingMoreMessages,
                            invitationStatus = chat.invitationStatus,
                        )
                    }
                }
            }
        }
    }

    private fun startRecentMessageRefresh() {
        refreshMessagesJob?.cancel()
        refreshMessagesJob = viewModelScope.launch {
            while (true) {
                try {
                    refreshChatMessagesUseCase(chatId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    // The main sync loop handles transient network recovery.
                }
                delay(RECENT_MESSAGE_REFRESH_INTERVAL_MS.milliseconds)
            }
        }
    }

    private fun sendMessage() {
        when (val snapshot = takeComposerSnapshot()) {
            ComposerSnapshot.Empty -> return
            ComposerSnapshot.WaitingForAttachments -> {
                viewModelScope.launch {
                    _effect.emit(ChatDetailsEffect.ShowMessage(waitAttachmentsUploadMessage()))
                }
            }
            is ComposerSnapshot.Ready -> {
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    enqueueOutgoingMessages(snapshot.messages)
                }
            }
        }
    }

    private fun startSendMessageQueue() {
        viewModelScope.launch {
            for (message in sendMessageQueue) {
                try {
                    val wasSent = sendChatMessageUseCase(
                        chatId = chatId,
                        plainText = message.plainText,
                        attachments = message.attachments,
                    )
                    if (wasSent) {
                        markOptimisticMessageSent(message.id)
                    } else {
                        markOptimisticMessageFailed(message.id)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    markOptimisticMessageFailed(message.id)
                    _effect.emit(ChatDetailsEffect.ShowError(sendMessageError()))
                }
            }
        }
    }

    private fun takeComposerSnapshot(): ComposerSnapshot {
        while (true) {
            val current = _state.value
            if (current.isPreparingAttachment) return ComposerSnapshot.WaitingForAttachments
            if ((current.draft.isBlank() && current.pendingAttachments.isEmpty()) || current.isPendingInvitation) {
                return ComposerSnapshot.Empty
            }

            val messages = current.toOutgoingMessageDrafts()
            if (messages.isEmpty()) return ComposerSnapshot.Empty

            val updated = current.copy(draft = "", pendingAttachments = emptyList())
            if (_state.compareAndSet(current, updated)) {
                return ComposerSnapshot.Ready(messages)
            }
        }
    }

    private suspend fun enqueueOutgoingMessages(messages: List<OutgoingMessageDraft>) {
        var failedToQueue = false
        optimisticMessagesMutex.withLock {
            val knownMessageIds = observedMessages.mapTo(mutableSetOf()) { message -> message.id }
            val queuedMessages = messages.map { draft ->
                QueuedOutgoingMessage(
                    id = nextOptimisticMessageIdLocked(),
                    plainText = draft.plainText,
                    attachments = draft.attachments,
                )
            }
            val optimistic = queuedMessages.map { message ->
                OptimisticChatMessage(
                    queueId = message.id,
                    message = message.toOptimisticMessage(),
                    observedMessageIdsAtEnqueue = knownMessageIds,
                )
            }
            optimisticMessages = optimisticMessages + optimistic
            _state.update {
                it.copy(
                    messages = messagesWithOptimisticMessagesLocked(),
                    isSendingMessage = hasSendingOptimisticMessagesLocked(),
                )
            }
            queuedMessages.forEach { message ->
                if (sendMessageQueue.trySend(message).isFailure) {
                    failedToQueue = true
                    optimisticMessages = optimisticMessages.filterNot { optimisticMessage ->
                        optimisticMessage.queueId == message.id
                    }
                }
            }
            if (failedToQueue) {
                _state.update {
                    it.copy(
                        messages = messagesWithOptimisticMessagesLocked(),
                        isSendingMessage = hasSendingOptimisticMessagesLocked(),
                    )
                }
            }
        }
        if (failedToQueue) {
            _effect.emit(ChatDetailsEffect.ShowError(sendMessageError()))
        }
    }

    private suspend fun markOptimisticMessageSent(messageId: String) {
        updateOptimisticMessageStatus(messageId, DeliveryStatus.Sent)
    }

    private suspend fun markOptimisticMessageFailed(messageId: String) {
        updateOptimisticMessageStatus(messageId, DeliveryStatus.Failed)
    }

    private suspend fun updateOptimisticMessageStatus(messageId: String, status: DeliveryStatus) {
        optimisticMessagesMutex.withLock {
            optimisticMessages = optimisticMessages.map { optimisticMessage ->
                if (optimisticMessage.queueId == messageId) {
                    optimisticMessage.copy(
                        message = optimisticMessage.message.copy(deliveryStatus = status),
                    )
                } else {
                    optimisticMessage
                }
            }
            _state.update {
                it.copy(
                    messages = messagesWithOptimisticMessagesLocked(),
                    isSendingMessage = hasSendingOptimisticMessagesLocked(),
                )
            }
        }
    }

    private fun reconcileDeliveredOptimisticMessagesLocked() {
        if (optimisticMessages.isEmpty()) return

        val matchedObservedIndices = mutableSetOf<Int>()
        optimisticMessages = optimisticMessages.filter { optimisticMessage ->
            if (optimisticMessage.message.deliveryStatus == DeliveryStatus.Failed) {
                return@filter true
            }
            val matchedIndex = observedMessages.indexOfFirstUnmatchedDelivery(
                optimisticMessage = optimisticMessage,
                matchedObservedIndices = matchedObservedIndices,
            )
            if (matchedIndex >= 0) {
                matchedObservedIndices += matchedIndex
                false
            } else {
                true
            }
        }
    }

    private fun List<ChatMessage>.indexOfFirstUnmatchedDelivery(
        optimisticMessage: OptimisticChatMessage,
        matchedObservedIndices: Set<Int>,
    ): Int {
        forEachIndexed { index, message ->
            if (
                index !in matchedObservedIndices &&
                message.id !in optimisticMessage.observedMessageIdsAtEnqueue &&
                message.isDeliveredVersionOf(optimisticMessage.message)
            ) {
                return index
            }
        }
        return -1
    }

    private fun ChatMessage.isDeliveredVersionOf(optimisticMessage: ChatMessage.User): Boolean {
        val userMessage = this as? ChatMessage.User ?: return false
        return userMessage.isMine &&
            userMessage.deliveryStatus != DeliveryStatus.Sending &&
            userMessage.deliveryStatus != DeliveryStatus.Failed &&
            userMessage.body == optimisticMessage.body &&
            userMessage.attachments.map(ChatAttachment::id) == optimisticMessage.attachments.map(ChatAttachment::id)
    }

    private fun messagesWithOptimisticMessagesLocked(): List<ChatMessage> {
        return observedMessages + optimisticMessages.map(OptimisticChatMessage::message)
    }

    private fun hasSendingOptimisticMessagesLocked(): Boolean {
        return optimisticMessages.any { optimisticMessage ->
            optimisticMessage.message.deliveryStatus == DeliveryStatus.Sending
        }
    }

    private fun ChatDetailsUiState.toOutgoingMessageDrafts(): List<OutgoingMessageDraft> {
        val trimmedDraft = draft.trim()
        val attachmentBatches = pendingAttachments.chunked(MAX_ATTACHMENTS_PER_MESSAGE)
        return if (attachmentBatches.isEmpty()) {
            listOf(OutgoingMessageDraft(plainText = trimmedDraft, attachments = emptyList()))
        } else {
            attachmentBatches.mapIndexed { index, batch ->
                OutgoingMessageDraft(
                    plainText = if (index == 0) trimmedDraft else "",
                    attachments = batch,
                )
            }
        }.filter { message ->
            message.plainText.isNotEmpty() || message.attachments.isNotEmpty()
        }
    }

    private fun QueuedOutgoingMessage.toOptimisticMessage(): ChatMessage.User {
        return ChatMessage.User(
            id = id,
            sender = CURRENT_USER_SENDER,
            body = plainText,
            timestamp = currentMessageTimestamp(),
            isMine = true,
            deliveryStatus = DeliveryStatus.Sending,
            attachments = attachments.map { attachment -> attachment.attachment },
        )
    }

    private fun nextOptimisticMessageIdLocked(): String {
        nextOptimisticMessageIndex += 1
        return "pending-$chatId-$nextOptimisticMessageIndex"
    }

    private fun currentMessageTimestamp(): String {
        val dateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dateTime.hour.twoDigits()}:${dateTime.minute.twoDigits()}"
    }

    private fun notifyAttachmentPickerPending(message: UiText) {
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
                    } catch (_: Throwable) {
                        failedAttachmentCount += 1
                    }
                }
                if (failedAttachmentCount > 0) {
                    _effect.emit(ChatDetailsEffect.ShowError(prepareAttachmentError()))
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

    private sealed interface ComposerSnapshot {
        data object Empty : ComposerSnapshot

        data object WaitingForAttachments : ComposerSnapshot

        data class Ready(val messages: List<OutgoingMessageDraft>) : ComposerSnapshot
    }

    private data class OutgoingMessageDraft(
        val plainText: String,
        val attachments: List<PreparedChatAttachment>,
    )

    private data class QueuedOutgoingMessage(
        val id: String,
        val plainText: String,
        val attachments: List<PreparedChatAttachment>,
    )

    private data class OptimisticChatMessage(
        val queueId: String,
        val message: ChatMessage.User,
        val observedMessageIdsAtEnqueue: Set<String>,
    )

    private companion object {
        private const val CURRENT_USER_SENDER = "You"
        private const val MAX_ATTACHMENTS_PER_MESSAGE = 10
        private const val RECENT_MESSAGE_REFRESH_INTERVAL_MS = 2_000L

        private fun sendMessageError(): UiText = uiText(Res.string.send_message_failed)

        private fun prepareAttachmentError(): UiText = uiText(Res.string.prepare_attachment_failed)

        private fun downloadAttachmentError(): UiText = uiText(Res.string.download_attachment_failed)

        private fun waitAttachmentsUploadMessage(): UiText = uiText(Res.string.wait_attachments_upload)

        private fun attachFilePendingMessage(): UiText = uiText(Res.string.file_picking_unavailable_or_cancelled)

        private fun pickImagePendingMessage(): UiText = uiText(Res.string.image_picking_unavailable_or_cancelled)

        private fun recordVoicePendingMessage(): UiText = uiText(Res.string.voice_recording_requires_native_recorder)

        private fun Int.twoDigits(): String = toString().padStart(2, '0')
    }
}
