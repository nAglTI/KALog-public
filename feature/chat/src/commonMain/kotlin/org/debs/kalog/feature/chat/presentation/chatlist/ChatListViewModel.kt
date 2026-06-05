package org.debs.kalog.feature.chat.presentation.chatlist

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
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentKind
import org.debs.kalog.feature.chat.domain.model.ChatMessage
import org.debs.kalog.feature.chat.domain.model.ChatThread
import org.debs.kalog.feature.chat.domain.usecase.CreateDirectChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.CreateGroupChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.ObserveChatsUseCase
import org.debs.kalog.feature.chat.domain.usecase.StartChatSessionUseCase
import org.debs.kalog.feature.chat.localization.chatLocalized

class ChatListViewModel(
    private val startChatSessionUseCase: StartChatSessionUseCase,
    private val createDirectChatUseCase: CreateDirectChatUseCase,
    private val createGroupChatUseCase: CreateGroupChatUseCase,
    private val observeChatsUseCase: ObserveChatsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatListUiState())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<ChatListEffect>(extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    private var createChatJob: Job? = null

    init {
        bootstrapSession()
        observeChats()
    }

    fun onEvent(event: ChatListEvent) {
        when (event) {
            is ChatListEvent.ChatClicked -> handleChatClick(event.chatId)
            is ChatListEvent.CreateChatConfirmed -> createDirectChat(event.targetUserId)
            ChatListEvent.CreateGroupChatClicked -> createGroupChat()
        }
    }

    private fun bootstrapSession() {
        viewModelScope.launch {
            try {
                startChatSessionUseCase()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Keep the UI responsive and allow later retries from the sync loop.
            }
        }
    }

    private fun observeChats() {
        viewModelScope.launch {
            observeChatsUseCase().collect { chats ->
                _state.update {
                    it.copy(
                        summary = chatLocalized(
                            en = "Encrypted chats: ${chats.size}",
                            ru = "Зашифрованные чаты: ${chats.size}",
                        ),
                        items = chats.map(ChatThread::toListItem),
                    )
                }
            }
        }
    }

    private fun handleChatClick(chatId: String) {
        if (chatId.isBlank()) return
        _effect.tryEmit(ChatListEffect.NavigateToChat(chatId))
    }

    private fun createDirectChat(targetUserId: String) {
        val normalizedUserId = targetUserId.trim()
        if (normalizedUserId.isEmpty() || state.value.isCreatingChat) return

        createChatJob?.cancel()
        createChatJob = viewModelScope.launch {
            _state.update { it.copy(isCreatingChat = true) }
            try {
                val chatId = createDirectChatUseCase(normalizedUserId)
                _effect.emit(ChatListEffect.NavigateToChat(chatId))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // TODO surface create-chat errors in the UI when the product error model is ready.
            } finally {
                _state.update { it.copy(isCreatingChat = false) }
            }
        }
    }

    private fun createGroupChat() {
        if (state.value.isCreatingChat) return

        createChatJob?.cancel()
        createChatJob = viewModelScope.launch {
            _state.update { it.copy(isCreatingChat = true) }
            try {
                val chatId = createGroupChatUseCase()
                _effect.emit(ChatListEffect.NavigateToChat(chatId))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // TODO surface create-group errors in the UI when the product error model is ready.
            } finally {
                _state.update { it.copy(isCreatingChat = false) }
            }
        }
    }
}

private fun ChatThread.toListItem(): ChatListItemUiState {
    val lastMessage = lastMessage
    return ChatListItemUiState(
        id = id,
        title = title,
        avatar = avatar,
        timestamp = lastMessage?.timestamp ?: "",
        preview = when (lastMessage) {
            is ChatMessage.Service -> lastMessage.body
            is ChatMessage.User -> lastMessage.body.takeIf { it.isNotBlank() }
                ?: lastMessage.attachments.previewLabel()
            null -> chatLocalized(
                en = "No messages yet",
                ru = "Сообщений пока нет",
            )
        },
        previewAuthor = when (lastMessage) {
            is ChatMessage.Service -> null
            is ChatMessage.User -> if (lastMessage.isMine) {
                chatLocalized(en = "You", ru = "Вы")
            } else {
                lastMessage.sender
            }
            null -> null
        },
        unreadCount = unreadCount,
    )
}

private fun List<ChatAttachment>.previewLabel(): String {
    if (isEmpty()) return ""
    val mediaCount = count { attachment ->
        attachment.kind == ChatAttachmentKind.Image || attachment.kind == ChatAttachmentKind.Video
    }
    return when {
        mediaCount > 1 -> chatLocalized(
            en = "Media files: $mediaCount",
            ru = "Медиафайлы: $mediaCount",
        )
        first().kind == ChatAttachmentKind.Image -> chatLocalized(en = "Photo", ru = "Фото")
        first().kind == ChatAttachmentKind.Video -> chatLocalized(en = "Video", ru = "Видео")
        first().kind == ChatAttachmentKind.Audio -> chatLocalized(en = "Audio", ru = "Аудио")
        first().kind == ChatAttachmentKind.Voice -> chatLocalized(
            en = "Voice message",
            ru = "Голосовое сообщение",
        )
        else -> chatLocalized(en = "File", ru = "Файл")
    }
}
