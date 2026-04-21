package org.debs.kalog.feature.chat.data.crypto

import kotlinx.serialization.json.Json
import org.debs.kalog.core.preferences.KeyValueStorage
import org.debs.kalog.core.preferences.SecureKeyValueStorage

class SettingsChatKeyStore(
    private val keyValueStorage: KeyValueStorage,
    private val secureKeyValueStorage: SecureKeyValueStorage,
    private val json: Json,
) : ChatKeyStore {
    override suspend fun currentUserId(): String? = keyValueStorage.getStringOrNull(CURRENT_USER_ID)

    override suspend fun currentUserPublicKey(): String? = keyValueStorage.getStringOrNull(CURRENT_USER_PUBLIC_KEY)

    override suspend fun currentUserPrivateKey(): String? = secureKeyValueStorage.getStringOrNull(CURRENT_USER_PRIVATE_KEY)

    override suspend fun serverPublicKey(): String? = keyValueStorage.getStringOrNull(SERVER_PUBLIC_KEY)

    override suspend fun saveCurrentUserKeys(userId: String?, publicKey: String, privateKey: String) {
        if (userId != null) {
            keyValueStorage.putString(CURRENT_USER_ID, userId)
        }
        keyValueStorage.putString(CURRENT_USER_PUBLIC_KEY, publicKey)
        secureKeyValueStorage.putString(CURRENT_USER_PRIVATE_KEY, privateKey)
    }

    override suspend fun saveCurrentUserId(userId: String) {
        keyValueStorage.putString(CURRENT_USER_ID, userId)
    }

    override suspend fun saveServerPublicKey(publicKey: String) {
        keyValueStorage.putString(SERVER_PUBLIC_KEY, publicKey)
    }

    override suspend fun chatPublicKey(chatId: String): String? {
        return keyValueStorage.getStringOrNull(chatPublicKeyKey(chatId))
    }

    override suspend fun chatPrivateKey(chatId: String): String? {
        return secureKeyValueStorage.getStringOrNull(chatPrivateKeyKey(chatId))
    }

    override suspend fun saveChatKeyPair(chatId: String, publicKey: String, privateKey: String) {
        keyValueStorage.putString(chatPublicKeyKey(chatId), publicKey)
        secureKeyValueStorage.putString(chatPrivateKeyKey(chatId), privateKey)
    }

    override suspend fun participantsFor(chatId: String): List<ChatParticipantKey> {
        val serializedValue = keyValueStorage.getStringOrNull(participantsKey(chatId)).orEmpty()
        if (serializedValue.isBlank()) return emptyList()

        return runCatching {
            json.decodeFromString<List<ChatParticipantKey>>(serializedValue)
        }.getOrDefault(emptyList())
    }

    override suspend fun saveParticipants(chatId: String, participants: List<ChatParticipantKey>) {
        keyValueStorage.putString(
            key = participantsKey(chatId),
            value = json.encodeToString(participants),
        )
    }

    override suspend fun clearChatState(chatId: String) {
        keyValueStorage.remove(chatPublicKeyKey(chatId))
        secureKeyValueStorage.remove(chatPrivateKeyKey(chatId))
        keyValueStorage.remove(participantsKey(chatId))
    }

    override suspend fun clearAll() {
        keyValueStorage.clear()
        secureKeyValueStorage.clear()
    }

    private fun chatPublicKeyKey(chatId: String) = "$CHAT_PUBLIC_KEY_PREFIX.$chatId"

    private fun chatPrivateKeyKey(chatId: String) = "$CHAT_PRIVATE_KEY_PREFIX.$chatId"

    private fun participantsKey(chatId: String) = "$PARTICIPANTS_PREFIX.$chatId"

    private companion object {
        private const val CURRENT_USER_ID = "chat.keys.current_user.id"
        private const val CURRENT_USER_PUBLIC_KEY = "chat.keys.current_user.public"
        private const val CURRENT_USER_PRIVATE_KEY = "chat.keys.current_user.private"
        private const val SERVER_PUBLIC_KEY = "chat.keys.server.public"
        private const val CHAT_PUBLIC_KEY_PREFIX = "chat.keys.chat.public"
        private const val CHAT_PRIVATE_KEY_PREFIX = "chat.keys.chat.private"
        private const val PARTICIPANTS_PREFIX = "chat.keys.participants"
    }
}
