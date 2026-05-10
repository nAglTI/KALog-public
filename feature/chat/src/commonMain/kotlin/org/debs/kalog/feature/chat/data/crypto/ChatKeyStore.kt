package org.debs.kalog.feature.chat.data.crypto

import kotlinx.serialization.Serializable

interface ChatKeyStore {
    suspend fun currentUserId(): String?

    suspend fun currentUserPublicKey(): String?

    suspend fun currentUserPrivateKey(): String?

    suspend fun serverPublicKey(): String?

    suspend fun saveCurrentUserKeys(userId: String?, publicKey: String, privateKey: String)

    suspend fun saveCurrentUserId(userId: String)

    suspend fun saveServerPublicKey(publicKey: String)

    suspend fun chatPublicKey(chatId: String): String?

    suspend fun chatPrivateKey(chatId: String): String?

    suspend fun saveChatKeyPair(chatId: String, publicKey: String, privateKey: String)

    suspend fun attachmentEncryptionKey(chatId: String, attachmentId: String): AttachmentEncryptionKey?

    suspend fun saveAttachmentEncryptionKey(chatId: String, key: AttachmentEncryptionKey)

    suspend fun participantsFor(chatId: String): List<ChatParticipantKey>

    suspend fun saveParticipants(chatId: String, participants: List<ChatParticipantKey>)

    suspend fun clearChatState(chatId: String)

    suspend fun clearAll()
}

@Serializable
data class ChatParticipantKey(
    val userId: String,
    val displayName: String,
    val publicKey: String,
    val isCurrentUser: Boolean = false,
)

@Serializable
data class AttachmentEncryptionKey(
    val id: String,
    val chatId: String,
    val algorithm: String,
    val sizeBits: Int,
    val key: String,
)

data class EncryptedRecipientPayload(
    val recipientId: String,
    val chunks: List<String>,
)
