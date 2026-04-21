package org.debs.kalog.feature.chat.domain.model

enum class AvatarAccent {
    Sky,
    Emerald,
    Amber,
    Rose,
    Indigo,
}

data class AvatarSpec(
    val initials: String,
    val accent: AvatarAccent,
)

enum class ChatType {
    Personal,
    Group,
    Unknown,
}

enum class DeliveryStatus {
    Sending,
    Sent,
    Read,
}

enum class InvitationStatus {
    None,
    Pending,
    Accepted,
}

sealed interface ChatMessage {
    val id: String
    val timestamp: String

    data class User(
        override val id: String,
        val sender: String,
        val body: String,
        override val timestamp: String,
        val isMine: Boolean,
        val deliveryStatus: DeliveryStatus = DeliveryStatus.Sent,
    ) : ChatMessage

    data class Service(
        override val id: String,
        val body: String,
        override val timestamp: String,
    ) : ChatMessage
}

data class ChatParticipant(
    val userId: String,
    val displayName: String,
    val isCurrentUser: Boolean,
)

data class ChatThread(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: ChatType,
    val avatar: AvatarSpec,
    val unreadCount: Int,
    val messages: List<ChatMessage>,
    val hasMoreMessages: Boolean = false,
    val isLoadingMoreMessages: Boolean = false,
    val invitationStatus: InvitationStatus = InvitationStatus.None,
) {
    val lastMessage: ChatMessage?
        get() = messages.lastOrNull()
}
