package org.debs.kalog.feature.chat.presentation.chatlist

import org.debs.kalog.feature.chat.domain.model.AvatarSpec
import org.debs.kalog.feature.chat.presentation.text.UiText
import org.debs.kalog.feature.chat.presentation.text.uiText
import mayday_chat.feature.chat.generated.resources.Res
import mayday_chat.feature.chat.generated.resources.*

data class ChatListUiState(
    val title: String = "Mayday Chat",
    val summary: UiText = uiText(Res.string.encrypted_chats),
    val isCreatingChat: Boolean = false,
    val items: List<ChatListItemUiState> = emptyList(),
)

data class ChatListItemUiState(
    val id: String,
    val title: String,
    val avatar: ChatListAvatarUiState,
    val timestamp: String,
    val preview: UiText,
    val previewAuthor: UiText?,
    val unreadCount: Int,
    val isPinned: Boolean = false,
)

sealed interface ChatListAvatarUiState {
    data class Initials(val avatar: AvatarSpec) : ChatListAvatarUiState

    data object SavedMessages : ChatListAvatarUiState
}

sealed interface ChatListEvent {
    data class ChatClicked(val chatId: String) : ChatListEvent

    data class CreateChatConfirmed(val targetUserId: String) : ChatListEvent

    data object CreateGroupChatClicked : ChatListEvent
}

sealed interface ChatListEffect {
    data class NavigateToChat(val chatId: String) : ChatListEffect
}
