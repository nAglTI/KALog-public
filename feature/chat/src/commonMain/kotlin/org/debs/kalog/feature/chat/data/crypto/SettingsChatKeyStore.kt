package org.debs.kalog.feature.chat.data.crypto

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import org.debs.kalog.core.crypto.PrivateKeyRef
import org.debs.kalog.core.crypto.exportedValueOrNull
import org.debs.kalog.core.preferences.KeyValueStorage
import org.debs.kalog.core.preferences.SecureKeyValueStorage
import org.debs.kalog.feature.chat.localization.chatLocalized
import kotlin.time.Clock
import kotlin.random.Random

class SettingsChatKeyStore(
    private val keyValueStorage: KeyValueStorage,
    private val secureKeyValueStorage: SecureKeyValueStorage,
    private val json: Json,
) : ChatKeyStore {
    override fun observeKeyRevision(): Flow<String> = keyValueStorage.observeString(KEY_REVISION, DEFAULT_KEY_REVISION)

    override suspend fun currentKeyRevision(): String {
        return keyValueStorage.getStringOrNull(KEY_REVISION).orEmpty().ifBlank { DEFAULT_KEY_REVISION }
    }

    override suspend fun currentUserId(): String? = keyValueStorage.getStringOrNull(CURRENT_USER_ID)

    override suspend fun currentUserPublicKey(): String? = keyValueStorage.getStringOrNull(CURRENT_USER_PUBLIC_KEY)

    override suspend fun currentUserPrivateKey(): String? = currentUserPrivateKeyRef()?.exportedValueOrNull()

    override suspend fun currentUserPrivateKeyRef(): PrivateKeyRef? {
        return privateKeyRefOrNull(CURRENT_USER_PRIVATE_KEY)
    }

    override suspend fun serverPublicKey(): String? = keyValueStorage.getStringOrNull(SERVER_PUBLIC_KEY)

    override suspend fun saveCurrentUserKeys(userId: String?, publicKey: String, privateKey: String) {
        saveCurrentUserKeys(
            userId = userId,
            publicKey = publicKey,
            privateKeyRef = PrivateKeyRef.Exported(privateKey),
        )
    }

    override suspend fun saveCurrentUserKeys(userId: String?, publicKey: String, privateKeyRef: PrivateKeyRef) {
        val previousUserId = currentUserId()
        val previousPublicKey = currentUserPublicKey()
        val previousPrivateKeyRef = currentUserPrivateKeyRef()
        if (userId != null) {
            keyValueStorage.putString(CURRENT_USER_ID, userId)
        }
        keyValueStorage.putString(CURRENT_USER_PUBLIC_KEY, publicKey)
        secureKeyValueStorage.putString(CURRENT_USER_PRIVATE_KEY, privateKeyRef.serialize())
        if (
            previousUserId != (userId ?: previousUserId) ||
            previousPublicKey != publicKey ||
            previousPrivateKeyRef != privateKeyRef
        ) {
            touchKeyRevision()
        }
    }

    override suspend fun saveCurrentUserId(userId: String) {
        val previousUserId = currentUserId()
        keyValueStorage.putString(CURRENT_USER_ID, userId)
        if (previousUserId != userId) {
            touchKeyRevision()
        }
    }

    override suspend fun saveServerPublicKey(publicKey: String) {
        val previousPublicKey = serverPublicKey()
        keyValueStorage.putString(SERVER_PUBLIC_KEY, publicKey)
        if (previousPublicKey != publicKey) {
            touchKeyRevision()
        }
    }

    override suspend fun chatPublicKey(chatId: String): String? {
        return keyValueStorage.getStringOrNull(chatPublicKeyKey(chatId))
    }

    override suspend fun chatPrivateKey(chatId: String): String? = chatPrivateKeyRef(chatId)?.exportedValueOrNull()

    override suspend fun chatPrivateKeyRef(chatId: String): PrivateKeyRef? {
        return privateKeyRefOrNull(chatPrivateKeyKey(chatId))
    }

    override suspend fun saveChatKeyPair(chatId: String, publicKey: String, privateKey: String) {
        saveChatKeyPair(
            chatId = chatId,
            publicKey = publicKey,
            privateKeyRef = PrivateKeyRef.Exported(privateKey),
        )
    }

    override suspend fun saveChatKeyPair(chatId: String, publicKey: String, privateKeyRef: PrivateKeyRef) {
        val previousPublicKey = chatPublicKey(chatId)
        val previousPrivateKeyRef = chatPrivateKeyRef(chatId)
        val trackedChanged = trackChatId(chatId)
        keyValueStorage.putString(chatPublicKeyKey(chatId), publicKey)
        secureKeyValueStorage.putString(chatPrivateKeyKey(chatId), privateKeyRef.serialize())
        if (trackedChanged || previousPublicKey != publicKey || previousPrivateKeyRef != privateKeyRef) {
            touchKeyRevision()
        }
    }

    override suspend fun selfChatId(): String? = keyValueStorage.getStringOrNull(SELF_CHAT_ID)

    override suspend fun saveSelfChatId(chatId: String) {
        val normalizedChatId = chatId.trim()
        if (normalizedChatId.isBlank()) return

        val previousChatId = selfChatId()
        keyValueStorage.putString(SELF_CHAT_ID, normalizedChatId)

        val publicKey = selfChatPublicKey()
        val privateKeyRef = selfChatPrivateKeyRef()
        if (publicKey != null && privateKeyRef != null) {
            saveChatKeyPair(normalizedChatId, publicKey, privateKeyRef)
        } else if (previousChatId != normalizedChatId) {
            touchKeyRevision()
        }
    }

    override suspend fun selfChatPublicKey(): String? = keyValueStorage.getStringOrNull(SELF_CHAT_PUBLIC_KEY)

    override suspend fun selfChatPrivateKeyRef(): PrivateKeyRef? {
        return privateKeyRefOrNull(SELF_CHAT_PRIVATE_KEY)
    }

    override suspend fun saveSelfChatKeyPair(publicKey: String, privateKeyRef: PrivateKeyRef) {
        val previousPublicKey = selfChatPublicKey()
        val previousPrivateKeyRef = selfChatPrivateKeyRef()
        keyValueStorage.putString(SELF_CHAT_PUBLIC_KEY, publicKey)
        secureKeyValueStorage.putString(SELF_CHAT_PRIVATE_KEY, privateKeyRef.serialize())
        selfChatId()?.takeIf(String::isNotBlank)?.let { chatId ->
            saveChatKeyPair(chatId, publicKey, privateKeyRef)
        }
        if (previousPublicKey != publicKey || previousPrivateKeyRef != privateKeyRef) {
            touchKeyRevision()
        }
    }

    override suspend fun deviceId(): String? = keyValueStorage.getStringOrNull(DEVICE_ID)

    override suspend fun saveDeviceId(deviceId: String) {
        val normalizedDeviceId = deviceId.trim()
        if (normalizedDeviceId.isBlank()) return
        keyValueStorage.putString(DEVICE_ID, normalizedDeviceId)
    }

    override suspend fun participantsFor(chatId: String): List<ChatParticipantKey> {
        val serializedValue = keyValueStorage.getStringOrNull(participantsKey(chatId)).orEmpty()
        if (serializedValue.isBlank()) return emptyList()

        return runCatching {
            json.decodeFromString<List<ChatParticipantKey>>(serializedValue)
        }.getOrDefault(emptyList())
    }

    override suspend fun saveParticipants(chatId: String, participants: List<ChatParticipantKey>) {
        val previousParticipants = participantsFor(chatId)
        val trackedChanged = trackChatId(chatId)
        keyValueStorage.putString(
            key = participantsKey(chatId),
            value = json.encodeToString(participants),
        )
        if (trackedChanged || previousParticipants != participants) {
            touchKeyRevision()
        }
    }

    override suspend fun clearChatState(chatId: String) {
        keyValueStorage.remove(chatPublicKeyKey(chatId))
        secureKeyValueStorage.remove(chatPrivateKeyKey(chatId))
        keyValueStorage.remove(participantsKey(chatId))
        untrackChatId(chatId)
        touchKeyRevision()
    }

    override suspend fun exportSnapshot(): ChatKeySnapshot {
        val userId = requireNotNull(currentUserId()?.ifBlank { null }) {
            chatLocalized(
                en = "Current user UUID is not initialized.",
                ru = "UUID текущего пользователя не инициализирован.",
            )
        }
        val publicKey = requireNotNull(currentUserPublicKey()?.ifBlank { null }) {
            chatLocalized(
                en = "Current user public key is not initialized.",
                ru = "Публичный ключ текущего пользователя не инициализирован.",
            )
        }
        val privateKeyRef = requireNotNull(currentUserPrivateKeyRef()) {
            chatLocalized(
                en = "Current user private key is not initialized.",
                ru = "Приватный ключ текущего пользователя не инициализирован.",
            )
        }
        val selfChatId = selfChatId()
        val selfChatPublicKey = selfChatPublicKey() ?: selfChatId?.let { chatPublicKey(it) }
        val selfChatPrivateKeyRef = selfChatPrivateKeyRef() ?: selfChatId?.let { chatPrivateKeyRef(it) }
        val chatIds = trackedChatIds()
        return ChatKeySnapshot(
            currentUserId = userId,
            currentUserPublicKey = publicKey,
            currentUserPrivateKeyRef = privateKeyRef.serialize(),
            serverPublicKey = serverPublicKey(),
            selfChatId = selfChatId,
            selfChatPublicKey = selfChatPublicKey,
            selfChatPrivateKeyRef = selfChatPrivateKeyRef?.serialize(),
            chatKeys = chatIds.mapNotNull { chatId ->
                val chatPublicKey = chatPublicKey(chatId) ?: return@mapNotNull null
                val chatPrivateKeyRef = chatPrivateKeyRef(chatId) ?: return@mapNotNull null
                StoredChatKeyPair(
                    chatId = chatId,
                    publicKey = chatPublicKey,
                    privateKeyRef = chatPrivateKeyRef.serialize(),
                )
            },
            participantsByChat = chatIds.mapNotNull { chatId ->
                val participants = participantsFor(chatId)
                if (participants.isEmpty()) {
                    null
                } else {
                    StoredChatParticipants(chatId = chatId, participants = participants)
                }
            },
        )
    }

    override suspend fun importSnapshot(snapshot: ChatKeySnapshot) {
        clearKnownKeyState()
        saveCurrentUserKeys(
            userId = snapshot.currentUserId,
            publicKey = snapshot.currentUserPublicKey,
            privateKeyRef = PrivateKeyRef.deserialize(snapshot.currentUserPrivateKeyRef),
        )
        snapshot.serverPublicKey?.takeIf(String::isNotBlank)?.let { publicKey ->
            saveServerPublicKey(publicKey)
        }
        snapshot.selfChatId?.takeIf(String::isNotBlank)?.let { chatId ->
            saveSelfChatId(chatId)
        }
        if (!snapshot.selfChatPublicKey.isNullOrBlank() && !snapshot.selfChatPrivateKeyRef.isNullOrBlank()) {
            val privateKeyRef = PrivateKeyRef.deserialize(snapshot.selfChatPrivateKeyRef)
            saveSelfChatKeyPair(snapshot.selfChatPublicKey, privateKeyRef)
            snapshot.selfChatId?.takeIf(String::isNotBlank)?.let { chatId ->
                saveChatKeyPair(chatId, snapshot.selfChatPublicKey, privateKeyRef)
            }
        }
        snapshot.chatKeys.forEach { keyPair ->
            saveChatKeyPair(
                chatId = keyPair.chatId,
                publicKey = keyPair.publicKey,
                privateKeyRef = PrivateKeyRef.deserialize(keyPair.privateKeyRef),
            )
        }
        snapshot.participantsByChat.forEach { participants ->
            saveParticipants(chatId = participants.chatId, participants = participants.participants)
        }
        touchKeyRevision()
    }

    override suspend fun clearAll() {
        keyValueStorage.clear()
        secureKeyValueStorage.clear()
    }

    private fun chatPublicKeyKey(chatId: String) = "$CHAT_PUBLIC_KEY_PREFIX.$chatId"

    private fun chatPrivateKeyKey(chatId: String) = "$CHAT_PRIVATE_KEY_PREFIX.$chatId"

    private fun participantsKey(chatId: String) = "$PARTICIPANTS_PREFIX.$chatId"

    private suspend fun trackedChatIds(): List<String> {
        val serializedValue = keyValueStorage.getStringOrNull(CHAT_IDS).orEmpty()
        if (serializedValue.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<String>>(serializedValue)
        }.getOrDefault(emptyList())
    }

    private suspend fun trackChatId(chatId: String): Boolean {
        val previousIds = trackedChatIds()
        val updatedIds = (previousIds + chatId)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        if (updatedIds == previousIds) return false
        keyValueStorage.putString(CHAT_IDS, json.encodeToString(updatedIds))
        return true
    }

    private suspend fun untrackChatId(chatId: String) {
        val updatedIds = trackedChatIds().filterNot { storedChatId -> storedChatId == chatId }
        if (updatedIds.isEmpty()) {
            keyValueStorage.remove(CHAT_IDS)
        } else {
            keyValueStorage.putString(CHAT_IDS, json.encodeToString(updatedIds))
        }
    }

    private suspend fun clearKnownKeyState() {
        trackedChatIds().forEach { chatId ->
            keyValueStorage.remove(chatPublicKeyKey(chatId))
            secureKeyValueStorage.remove(chatPrivateKeyKey(chatId))
            keyValueStorage.remove(participantsKey(chatId))
        }
        keyValueStorage.remove(CHAT_IDS)
        keyValueStorage.remove(CURRENT_USER_ID)
        keyValueStorage.remove(CURRENT_USER_PUBLIC_KEY)
        secureKeyValueStorage.remove(CURRENT_USER_PRIVATE_KEY)
        keyValueStorage.remove(SERVER_PUBLIC_KEY)
        keyValueStorage.remove(SELF_CHAT_ID)
        keyValueStorage.remove(SELF_CHAT_PUBLIC_KEY)
        secureKeyValueStorage.remove(SELF_CHAT_PRIVATE_KEY)
        keyValueStorage.remove(DEVICE_ID)
    }

    private suspend fun touchKeyRevision() {
        keyValueStorage.putString(
            key = KEY_REVISION,
            value = "${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong()}",
        )
    }

    private suspend fun privateKeyRefOrNull(key: String): PrivateKeyRef? {
        val storedValue = secureKeyValueStorage.getStringOrNull(key) ?: return null
        return runCatching {
            PrivateKeyRef.deserialize(storedValue)
        }.getOrElse {
            secureKeyValueStorage.remove(key)
            null
        }
    }

    private companion object {
        private const val CURRENT_USER_ID = "chat.keys.current_user.id"
        private const val CURRENT_USER_PUBLIC_KEY = "chat.keys.current_user.public"
        private const val CURRENT_USER_PRIVATE_KEY = "chat.keys.current_user.private"
        private const val SERVER_PUBLIC_KEY = "chat.keys.server.public"
        private const val SELF_CHAT_ID = "chat.keys.self_chat.id"
        private const val SELF_CHAT_PUBLIC_KEY = "chat.keys.self_chat.public"
        private const val SELF_CHAT_PRIVATE_KEY = "chat.keys.self_chat.private"
        private const val DEVICE_ID = "chat.keys.device.id"
        private const val CHAT_PUBLIC_KEY_PREFIX = "chat.keys.chat.public"
        private const val CHAT_PRIVATE_KEY_PREFIX = "chat.keys.chat.private"
        private const val PARTICIPANTS_PREFIX = "chat.keys.participants"
        private const val CHAT_IDS = "chat.keys.chat.ids"
        private const val KEY_REVISION = "chat.keys.revision"
        private const val DEFAULT_KEY_REVISION = "0"
    }
}
