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

enum class ChatAttachmentKind {
    File,
    Image,
    Video,
    Audio,
    Voice,
}

enum class ChatAttachmentLoadState {
    NotStarted,
    WaitingForTap,
    CheckingCache,
    Downloading,
    Downloaded,
    Decrypting,
    Ready,
    Failed,
}

data class ChatAttachment(
    val id: String,
    val kind: ChatAttachmentKind,
    val name: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val localUri: String? = null,
    val contentBytes: ByteArray? = null,
    val durationMillis: Long? = null,
    val encryptionKeyId: String? = null,
    val decryptionKey: String? = null,
    val chunkSizeBytes: Long? = null,
    val parts: List<ChatAttachmentPart> = emptyList(),
    val loadState: ChatAttachmentLoadState = ChatAttachmentLoadState.NotStarted,
) {
    val uuid: String
        get() = id
}

data class ChatAttachmentPart(
    val id: String,
    val index: Int,
    val sizeBytes: Long? = null,
    val key: String? = null,
)

data class ChatAttachmentEncryptionSpec(
    val uuid: String,
    val key: String,
    val algorithm: String,
    val sizeBits: Int,
    val parts: List<ChatAttachmentPart> = emptyList(),
    val chunkSizeBytes: Long? = null,
)

data class PreparedChatAttachment(
    val attachment: ChatAttachment,
    val encryption: ChatAttachmentEncryptionSpec,
)

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
        val attachments: List<ChatAttachment> = emptyList(),
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
