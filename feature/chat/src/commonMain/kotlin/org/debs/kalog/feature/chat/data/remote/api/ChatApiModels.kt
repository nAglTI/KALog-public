package org.debs.kalog.feature.chat.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StartRequestDto(
    @SerialName("pk") val publicKey: String,
)

@Serializable
data class StartResponseDto(
    @SerialName("spk") val serverPublicKey: String,
    @SerialName("uid") val userId: String,
)

@Serializable
data class CreateDirectChatRequestDto(
    @SerialName("pk") val publicKey: String,
    @SerialName("uid") val userId: String,
)

@Serializable
data class CreateGroupChatRequestDto(
    @SerialName("pk") val publicKey: String,
)

@Serializable
data class InviteUserToChatRequestDto(
    @SerialName("chat_id") val chatId: String,
    @SerialName("uid") val userId: String,
)

@Serializable
data class LeaveGroupChatRequestDto(
    @SerialName("chat_id") val chatId: String,
)

@Serializable
data class SetGroupChatPublicKeyRequestDto(
    @SerialName("chat_id") val chatId: String,
    @SerialName("pk") val publicKey: String,
)

@Serializable
data class InitAttachmentResponseDto(
    @SerialName("aid") val attachmentId: String,
    @SerialName("upload_token") val uploadToken: String,
)

@Serializable
data class ChatResponseDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("type") val type: String,
)

@Serializable
data class ChatUserDto(
    @SerialName("pk") val publicKey: String,
    @SerialName("uid") val userId: String,
)

@Serializable
data class ChatInfoDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("type") val type: String,
    @SerialName("users") val users: List<ChatUserDto> = emptyList(),
)

@Serializable
data class MessagePreviewDto(
    @SerialName("chat_id") val chatId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("data") val data: List<String> = emptyList(),
    @SerialName("from") val from: String,
    @SerialName("id") val id: String,
    @SerialName("message_type") val messageType: String,
    @SerialName("to") val to: String,
)

@Serializable
data class ChatListItemDto(
    @SerialName("id") val id: String,
    @SerialName("message") val message: MessagePreviewDto? = null,
    @SerialName("messages") val messages: List<MessagePreviewDto> = emptyList(),
    @SerialName("title") val title: String,
    @SerialName("type") val type: String,
)

@Serializable
data class MessageResponseDto(
    @SerialName("chat_id") val chatId: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("data") val data: List<String> = emptyList(),
    @SerialName("from") val from: String,
    @SerialName("id") val id: String,
    @SerialName("message_type") val messageType: String,
    @SerialName("to") val to: String,
)

@Serializable
data class PollMessagesRequestDto(
    @SerialName("since") val since: String,
)

@Serializable
data class PollMessagesResponseDto(
    @SerialName("messages") val messages: List<MessageResponseDto> = emptyList(),
    @SerialName("timestamp") val timestamp: String = "",
)

@Serializable
data class SendDataDto(
    @SerialName("data") val data: List<String>,
    @SerialName("to") val to: String,
)

@Serializable
data class SendMessagesRequestDto(
    @SerialName("chat_id") val chatId: String,
    @SerialName("message_type") val messageType: String,
    @SerialName("send_data") val sendData: List<SendDataDto>,
)
