package org.debs.kalog.feature.chat.presentation.chatinfo

import org.debs.kalog.feature.chat.domain.model.AvatarAccent
import org.debs.kalog.feature.chat.domain.model.AvatarSpec
import org.debs.kalog.feature.chat.domain.model.ChatType

data class ChatInfoUiState(
    val title: String = "",
    val type: ChatType = ChatType.Unknown,
    val avatar: AvatarSpec = AvatarSpec(initials = "--", accent = AvatarAccent.Sky),
    val participants: List<ParticipantUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val isCreatingDirectChat: Boolean = false,
)

data class ParticipantUiModel(
    val userId: String,
    val displayName: String,
    val isCurrentUser: Boolean,
)

sealed interface ChatInfoEvent {
    data class TitleChanged(val newTitle: String) : ChatInfoEvent
    data class ParticipantClicked(val userId: String) : ChatInfoEvent
}

sealed interface ChatInfoEffect {
    data class NavigateToChat(val chatId: String) : ChatInfoEffect
}
