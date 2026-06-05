package org.debs.kalog.feature.chat.data.crypto

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.debs.kalog.core.crypto.PrivateKeyRef
import org.debs.kalog.core.crypto.exportedValueOrNull
import org.debs.kalog.feature.chat.localization.chatLocalized

interface ChatKeyStore {
    fun observeKeyRevision(): Flow<String>

    suspend fun currentKeyRevision(): String

    suspend fun currentUserId(): String?

    suspend fun currentUserPublicKey(): String?

    suspend fun currentUserPrivateKey(): String?

    suspend fun currentUserPrivateKeyRef(): PrivateKeyRef? {
        return currentUserPrivateKey()?.let(PrivateKeyRef::Exported)
    }

    suspend fun serverPublicKey(): String?

    suspend fun saveCurrentUserKeys(userId: String?, publicKey: String, privateKey: String)

    suspend fun saveCurrentUserKeys(userId: String?, publicKey: String, privateKeyRef: PrivateKeyRef) {
        saveCurrentUserKeys(
            userId = userId,
            publicKey = publicKey,
            privateKey = privateKeyRef.exportedValueOrNull()
                ?: error(
                    chatLocalized(
                        en = "This chat key store cannot persist platform private key references.",
                        ru = "Это хранилище ключей чата не может сохранять ссылки на платформенные приватные ключи.",
                    ),
                ),
        )
    }

    suspend fun saveCurrentUserId(userId: String)

    suspend fun saveServerPublicKey(publicKey: String)

    suspend fun chatPublicKey(chatId: String): String?

    suspend fun chatPrivateKey(chatId: String): String?

    suspend fun chatPrivateKeyRef(chatId: String): PrivateKeyRef? {
        return chatPrivateKey(chatId)?.let(PrivateKeyRef::Exported)
    }

    suspend fun saveChatKeyPair(chatId: String, publicKey: String, privateKey: String)

    suspend fun saveChatKeyPair(chatId: String, publicKey: String, privateKeyRef: PrivateKeyRef) {
        saveChatKeyPair(
            chatId = chatId,
            publicKey = publicKey,
            privateKey = privateKeyRef.exportedValueOrNull()
                ?: error(
                    chatLocalized(
                        en = "This chat key store cannot persist platform private key references.",
                        ru = "Это хранилище ключей чата не может сохранять ссылки на платформенные приватные ключи.",
                    ),
                ),
        )
    }

    suspend fun participantsFor(chatId: String): List<ChatParticipantKey>

    suspend fun saveParticipants(chatId: String, participants: List<ChatParticipantKey>)

    suspend fun clearChatState(chatId: String)

    suspend fun exportSnapshot(): ChatKeySnapshot

    suspend fun importSnapshot(snapshot: ChatKeySnapshot)

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
data class ChatKeySnapshot(
    val currentUserId: String,
    val currentUserPublicKey: String,
    val currentUserPrivateKeyRef: String,
    val serverPublicKey: String? = null,
    val chatKeys: List<StoredChatKeyPair> = emptyList(),
    val participantsByChat: List<StoredChatParticipants> = emptyList(),
)

@Serializable
data class StoredChatKeyPair(
    val chatId: String,
    val publicKey: String,
    val privateKeyRef: String,
)

@Serializable
data class StoredChatParticipants(
    val chatId: String,
    val participants: List<ChatParticipantKey>,
)

data class EncryptedRecipientPayload(
    val recipientId: String,
    val chunks: List<String>,
)
