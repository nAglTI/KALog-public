package org.debs.kalog.feature.chat.presentation.chatlist

import org.debs.kalog.feature.chat.domain.model.AvatarSpec
import org.debs.kalog.feature.chat.localization.chatLocalized

data class ChatListUiState(
    val title: String = "Mayday Chat",
    val summary: String = chatLocalized(
        en = "Encrypted chats",
        ru = "Зашифрованные чаты",
    ),
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
