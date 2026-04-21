package org.debs.kalog.feature.chat.presentation.chatlist

import org.debs.kalog.feature.chat.domain.model.AvatarSpec

data class ChatListUiState(
    val title: String = "KALog",
    val summary: String = "Encrypted chats",
    val isCreatingChat: Boolean = false,
    val items: List<ChatListItemUiState> = emptyList(),
)

data class ChatListItemUiState(
    val id: String,
    val title: String,
    val avatar: AvatarSpec,
    val timestamp: String,
    val preview: String,
    val previewAuthor: String?,
    val unreadCount: Int,
)

sealed interface ChatListEvent {
    data class ChatClicked(val chatId: String) : ChatListEvent

    data class CreateChatConfirmed(val targetUserId: String) : ChatListEvent

    data object CreateGroupChatClicked : ChatListEvent
}

sealed interface ChatListEffect {
    data class NavigateToChat(val chatId: String) : ChatListEffect
}
