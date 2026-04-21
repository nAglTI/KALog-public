package org.debs.kalog.feature.chat.data.remote

import org.debs.kalog.feature.chat.data.remote.api.ChatApiService
import org.debs.kalog.feature.chat.data.remote.api.CreateDirectChatRequestDto
import org.debs.kalog.feature.chat.data.remote.api.CreateGroupChatRequestDto
import org.debs.kalog.feature.chat.data.remote.api.InviteUserToChatRequestDto
import org.debs.kalog.feature.chat.data.remote.api.LeaveGroupChatRequestDto
import org.debs.kalog.feature.chat.data.remote.api.SendDataDto
import org.debs.kalog.feature.chat.data.remote.api.SendMessagesRequestDto
import org.debs.kalog.feature.chat.data.remote.api.SetGroupChatPublicKeyRequestDto
import org.debs.kalog.feature.chat.data.remote.api.StartRequestDto

interface ChatRemoteDataSource {
    suspend fun start(publicKey: String): RemoteStartSession

    suspend fun getChats(): List<RemoteChatSummary>

    suspend fun getChatInfo(chatId: String): RemoteChatInfo

    suspend fun getMessageHistory(chatId: String, offset: Int, limit: Int): List<RemoteMessage>

    suspend fun pollMessages(since: String): RemotePolledMessages

    suspend fun sendMessage(chatId: String, payloads: List<RemoteSendPayload>)

    suspend fun sendServiceMessage(chatId: String, payloads: List<RemoteSendPayload>)

    suspend fun createDirectChat(targetUserId: String, publicKey: String): RemoteChatCreated

    suspend fun createGroupChat(publicKey: String): RemoteChatCreated

    suspend fun inviteUserToChat(chatId: String, userId: String)

    suspend fun leaveGroupChat(chatId: String)

    suspend fun leaveChat(chatId: String)

    suspend fun setGroupChatPublicKey(chatId: String, publicKey: String)
}

class KtorChatRemoteDataSource(
    private val chatApiService: ChatApiService,
) : ChatRemoteDataSource {
    override suspend fun start(publicKey: String): RemoteStartSession {
        val response = chatApiService.start(StartRequestDto(publicKey = publicKey))
        return RemoteStartSession(
            userId = response.userId,
            serverPublicKey = response.serverPublicKey,
        )
    }

    override suspend fun getChats(): List<RemoteChatSummary> {
        return chatApiService.getChatList().map { chat ->
            val seedMessages = chat.messages.ifEmpty {
                listOfNotNull(chat.message)
            }
            RemoteChatSummary(
                id = chat.id,
                title = chat.title,
                type = chat.type,
                seedMessages = seedMessages.map { message ->
                    RemoteMessage(
                        id = message.id,
                        chatId = message.chatId,
                        fromUserId = message.from,
                        toUserId = message.to,
                        type = message.messageType,
                        chunks = message.data,
                        createdAt = message.createdAt,
                    )
                },
            )
        }
    }

    override suspend fun getChatInfo(chatId: String): RemoteChatInfo {
        val response = chatApiService.getChatInfo(chatId)
        return RemoteChatInfo(
            id = response.id,
            title = response.title,
            type = response.type,
            users = response.users.map { user ->
                RemoteChatUser(
                    userId = user.userId,
                    publicKey = user.publicKey,
                )
            },
        )
    }

    override suspend fun getMessageHistory(chatId: String, offset: Int, limit: Int): List<RemoteMessage> {
        return chatApiService.getMessageHistory(chatId, offset, limit).map { message ->
            RemoteMessage(
                id = message.id,
                chatId = message.chatId,
                fromUserId = message.from,
                toUserId = message.to,
                type = message.messageType,
                chunks = message.data,
                createdAt = message.createdAt,
            )
        }
    }

    override suspend fun pollMessages(since: String): RemotePolledMessages {
        val response = chatApiService.pollMessages(
            org.debs.kalog.feature.chat.data.remote.api.PollMessagesRequestDto(since = since),
        )
        return RemotePolledMessages(
            timestamp = response.timestamp,
            messages = response.messages.map { message ->
                RemoteMessage(
                    id = message.id,
                    chatId = message.chatId,
                    fromUserId = message.from,
                    toUserId = message.to,
                    type = message.messageType,
                    chunks = message.data,
                    createdAt = message.createdAt,
                )
            },
        )
    }

    override suspend fun sendMessage(chatId: String, payloads: List<RemoteSendPayload>) {
        chatApiService.sendMessages(
            SendMessagesRequestDto(
                chatId = chatId,
                messageType = "default",
                sendData = payloads.map { payload ->
                    SendDataDto(
                        data = payload.chunks,
                        to = payload.recipientId,
                    )
                },
            ),
        )
    }

    override suspend fun sendServiceMessage(chatId: String, payloads: List<RemoteSendPayload>) {
        chatApiService.sendMessages(
            SendMessagesRequestDto(
                chatId = chatId,
                messageType = "service",
                sendData = payloads.map { payload ->
                    SendDataDto(
                        data = payload.chunks,
                        to = payload.recipientId,
                    )
                },
            ),
        )
    }

    override suspend fun createDirectChat(targetUserId: String, publicKey: String): RemoteChatCreated {
        val response = chatApiService.createDirectChat(
            CreateDirectChatRequestDto(
                publicKey = publicKey,
                userId = targetUserId,
            ),
        )
        return response.toRemote()
    }

    override suspend fun createGroupChat(publicKey: String): RemoteChatCreated {
        val response = chatApiService.createGroupChat(
            CreateGroupChatRequestDto(publicKey = publicKey),
        )
        return response.toRemote()
    }

    override suspend fun inviteUserToChat(chatId: String, userId: String) {
        chatApiService.inviteUserToChat(
            InviteUserToChatRequestDto(
                chatId = chatId,
                userId = userId,
            ),
        )
    }

    override suspend fun leaveGroupChat(chatId: String) {
        chatApiService.leaveGroupChat(
            LeaveGroupChatRequestDto(chatId = chatId),
        )
    }

    override suspend fun leaveChat(chatId: String) {
        chatApiService.leaveChat(
            LeaveGroupChatRequestDto(chatId = chatId),
        )
    }

    override suspend fun setGroupChatPublicKey(chatId: String, publicKey: String) {
        chatApiService.setGroupChatPublicKey(
            SetGroupChatPublicKeyRequestDto(
                chatId = chatId,
                publicKey = publicKey,
            ),
        )
    }
}

private fun org.debs.kalog.feature.chat.data.remote.api.ChatResponseDto.toRemote() = RemoteChatCreated(
    id = id,
    title = title,
    type = type,
)
