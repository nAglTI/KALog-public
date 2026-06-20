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
import org.debs.kalog.feature.chat.domain.model.ChatType
import org.debs.kalog.feature.chat.domain.usecase.CreateDirectChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.CreateGroupChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.EnsureSelfChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.ObserveChatsUseCase
import org.debs.kalog.feature.chat.domain.usecase.StartChatSessionUseCase
import org.debs.kalog.feature.chat.presentation.text.UiText
import org.debs.kalog.feature.chat.presentation.text.rawText
import org.debs.kalog.feature.chat.presentation.text.uiText
import mayday_chat.feature.chat.generated.resources.Res
import mayday_chat.feature.chat.generated.resources.*

class ChatListViewModel(
    private val startChatSessionUseCase: StartChatSessionUseCase,
    private val createDirectChatUseCase: CreateDirectChatUseCase,
    private val createGroupChatUseCase: CreateGroupChatUseCase,
    private val ensureSelfChatUseCase: EnsureSelfChatUseCase,
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
                        summary = uiText(Res.string.encrypted_chats_count, chats.size),
                        items = chats.toListItemsWithPinnedSelfChat(),
                    )
                }
            }
        }
    }

    private fun handleChatClick(chatId: String) {
        if (chatId.isBlank()) return
        if (chatId == SYNTHETIC_SELF_CHAT_ID) {
            ensureSelfChatAndOpen()
            return
        }
        _effect.tryEmit(ChatListEffect.NavigateToChat(chatId))
    }

    private fun ensureSelfChatAndOpen() {
        if (state.value.isCreatingChat) return

        createChatJob?.cancel()
        createChatJob = viewModelScope.launch {
            _state.update { it.copy(isCreatingChat = true) }
            try {
                val chatId = ensureSelfChatUseCase()
                _effect.emit(ChatListEffect.NavigateToChat(chatId))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // TODO surface self-chat creation errors in the UI when the product error model is ready.
            } finally {
                _state.update { it.copy(isCreatingChat = false) }
            }
        }
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
    val isSelfChat = type == ChatType.Self
    return ChatListItemUiState(
        id = id,
        title = title,
        avatar = if (isSelfChat) {
            ChatListAvatarUiState.SavedMessages
        } else {
            ChatListAvatarUiState.Initials(avatar)
        },
        timestamp = lastMessage?.timestamp ?: "",
        preview = when (lastMessage) {
            is ChatMessage.Service -> rawText(lastMessage.body)
            is ChatMessage.User -> lastMessage.body.takeIf { it.isNotBlank() }?.let(::rawText)
                ?: lastMessage.attachments.previewLabel()
            null -> uiText(Res.string.no_messages_yet)
        },
        previewAuthor = when (lastMessage) {
            is ChatMessage.Service -> null
            is ChatMessage.User -> if (lastMessage.isMine) {
                uiText(Res.string.you)
            } else {
                rawText(lastMessage.sender)
            }
            null -> null
        },
        unreadCount = unreadCount,
        isPinned = isSelfChat,
    )
}

private fun List<ChatThread>.toListItemsWithPinnedSelfChat(): List<ChatListItemUiState> {
    val selfChat = firstOrNull { chat -> chat.type == ChatType.Self }
    val regularChats = filterNot { chat -> chat.type == ChatType.Self }
    return listOf(selfChat?.toListItem() ?: syntheticSelfChatItem()) +
        regularChats.map(ChatThread::toListItem)
}

private fun syntheticSelfChatItem(): ChatListItemUiState {
    return ChatListItemUiState(
        id = SYNTHETIC_SELF_CHAT_ID,
        title = "Saved Messages",
        avatar = ChatListAvatarUiState.SavedMessages,
        timestamp = "",
        preview = uiText(Res.string.no_messages_yet),
        previewAuthor = null,
        unreadCount = 0,
        isPinned = true,
    )
}

private fun List<ChatAttachment>.previewLabel(): UiText {
    if (isEmpty()) return rawText("")
    val mediaCount = count { attachment ->
        attachment.kind == ChatAttachmentKind.Image || attachment.kind == ChatAttachmentKind.Video
    }
    return when {
        mediaCount > 1 -> uiText(Res.string.media_files_count, mediaCount)
        first().kind == ChatAttachmentKind.Image -> uiText(Res.string.photo)
        first().kind == ChatAttachmentKind.Video -> uiText(Res.string.video)
        first().kind == ChatAttachmentKind.Audio -> uiText(Res.string.audio)
        first().kind == ChatAttachmentKind.Voice -> uiText(Res.string.voice_message)
        else -> uiText(Res.string.file)
    }
}

private const val SYNTHETIC_SELF_CHAT_ID = "__mayday_self_chat__"
