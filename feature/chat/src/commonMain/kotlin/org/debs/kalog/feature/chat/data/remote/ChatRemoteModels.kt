package org.debs.kalog.feature.chat.data.remote

data class RemoteStartSession(
    val userId: String,
    val serverPublicKey: String,
)

data class RemoteChatSummary(
    val id: String,
    val title: String,
    val type: String,
    val seedMessages: List<RemoteMessage>,
)

data class RemoteChatInfo(
    val id: String,
    val title: String,
    val type: String,
    val users: List<RemoteChatUser>,
)

data class RemoteChatUser(
    val userId: String,
    val publicKey: String,
)

data class RemoteMessage(
    val id: String,
    val chatId: String,
    val fromUserId: String,
    val toUserId: String,
    val type: String,
    val chunks: List<String>,
    val createdAt: String = "",
)

data class RemoteSendPayload(
    val recipientId: String,
    val chunks: List<String>,
)

data class RemoteChatCreated(
    val id: String,
    val title: String,
    val type: String,
)

data class RemotePolledMessages(
    val messages: List<RemoteMessage>,
    val timestamp: String,
)

data class RemoteAttachmentUploadReservation(
    val attachmentId: String,
    val uploadToken: String,
)
