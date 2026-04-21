package org.debs.kalog.feature.chat.domain.repository

import kotlinx.coroutines.flow.Flow
import org.debs.kalog.feature.chat.domain.model.ChatParticipant
import org.debs.kalog.feature.chat.domain.model.ChatThread

interface ChatRepository {
    suspend fun startSession()

    suspend fun runSyncLoop()

    suspend fun getCurrentUserId(): String?

    suspend fun clearAllData()

    fun observeChats(): Flow<List<ChatThread>>

    fun observeChat(chatId: String): Flow<ChatThread?>

    suspend fun closeChat()

    suspend fun openChat(chatId: String)

    suspend fun loadMoreMessages(chatId: String): Boolean

    suspend fun sendMessage(chatId: String, plainText: String)

    suspend fun createDirectChat(targetUserId: String): String

    suspend fun createGroupChat(publicKey: String? = null): String

    suspend fun inviteUserToChat(chatId: String, userId: String)

    suspend fun leaveGroupChat(chatId: String)

    suspend fun setGroupChatPublicKey(chatId: String, publicKey: String)

    suspend fun renameChatLocally(chatId: String, newTitle: String)

    suspend fun getChatParticipants(chatId: String): List<ChatParticipant>

    suspend fun findDirectChatWith(userId: String): String?

    suspend fun acceptChatInvitation(chatId: String)

    suspend fun declineChatInvitation(chatId: String)

    suspend fun broadcastNicknameToAllChats()
}
