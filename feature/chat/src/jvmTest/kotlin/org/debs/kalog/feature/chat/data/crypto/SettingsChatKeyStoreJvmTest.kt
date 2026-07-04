package org.debs.kalog.feature.chat.data.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.debs.kalog.core.crypto.PrivateKeyRef
import org.debs.kalog.core.preferences.KeyValueStorage
import org.debs.kalog.core.preferences.SecureKeyValueStorage

class SettingsChatKeyStoreJvmTest {
    @Test
    fun removesInvalidStoredPrivateKeyReference() = runBlocking {
        val keyValueStorage = InMemoryKeyValueStorage()
        val secureKeyValueStorage = InMemorySecureKeyValueStorage()
        val keyStore = SettingsChatKeyStore(keyValueStorage, secureKeyValueStorage, Json)

        secureKeyValueStorage.putString(CURRENT_USER_PRIVATE_KEY, "raw-private-key")

        assertNull(keyStore.currentUserPrivateKeyRef())
        assertNull(secureKeyValueStorage.getStringOrNull(CURRENT_USER_PRIVATE_KEY))
    }

    @Test
    fun readsStoredExportedPrivateKeyReference() = runBlocking {
        val keyValueStorage = InMemoryKeyValueStorage()
        val secureKeyValueStorage = InMemorySecureKeyValueStorage()
        val keyStore = SettingsChatKeyStore(keyValueStorage, secureKeyValueStorage, Json)

        keyStore.saveCurrentUserKeys(
            userId = "user-id",
            publicKey = "public-key",
            privateKeyRef = PrivateKeyRef.Exported("private-key"),
        )

        assertEquals(PrivateKeyRef.Exported("private-key"), keyStore.currentUserPrivateKeyRef())
        assertEquals("private-key", keyStore.currentUserPrivateKey())
        assertNull(keyValueStorage.getStringOrNull(CURRENT_USER_PRIVATE_KEY))
        assertEquals(
            PrivateKeyRef.Exported("private-key").serialize(),
            secureKeyValueStorage.getStringOrNull(CURRENT_USER_PRIVATE_KEY),
        )
    }

    @Test
    fun storesPlatformPrivateKeyReferenceOutsideSecureStorage() = runBlocking {
        val keyValueStorage = InMemoryKeyValueStorage()
        val secureKeyValueStorage = InMemorySecureKeyValueStorage()
        val keyStore = SettingsChatKeyStore(keyValueStorage, secureKeyValueStorage, Json)
        val privateKeyRef = PrivateKeyRef.PlatformAlias(
            provider = "macos-seckey",
            alias = "org.debs.kalog.test.rsa.chat",
        )

        keyStore.saveChatKeyPair(
            chatId = "chat-id",
            publicKey = "chat-public-key",
            privateKeyRef = privateKeyRef,
        )

        assertEquals(privateKeyRef, keyStore.chatPrivateKeyRef("chat-id"))
        assertEquals(
            privateKeyRef.serialize(),
            keyValueStorage.getStringOrNull(CHAT_PRIVATE_KEY),
        )
        assertNull(secureKeyValueStorage.getStringOrNull(CHAT_PRIVATE_KEY))
    }

    @Test
    fun migratesStoredPlatformPrivateKeyReferenceFromSecureStorage() = runBlocking {
        val keyValueStorage = InMemoryKeyValueStorage()
        val secureKeyValueStorage = InMemorySecureKeyValueStorage()
        val keyStore = SettingsChatKeyStore(keyValueStorage, secureKeyValueStorage, Json)
        val privateKeyRef = PrivateKeyRef.PlatformAlias(
            provider = "macos-seckey",
            alias = "org.debs.kalog.test.rsa.legacy",
        )
        secureKeyValueStorage.putString(CHAT_PRIVATE_KEY, privateKeyRef.serialize())

        assertEquals(privateKeyRef, keyStore.chatPrivateKeyRef("chat-id"))
        assertEquals(
            privateKeyRef.serialize(),
            keyValueStorage.getStringOrNull(CHAT_PRIVATE_KEY),
        )
        assertNull(secureKeyValueStorage.getStringOrNull(CHAT_PRIVATE_KEY))
    }

    @Test
    fun exportsSnapshotAndUpdatesRevisionWhenChatKeysChange() = runBlocking {
        val keyValueStorage = InMemoryKeyValueStorage()
        val secureKeyValueStorage = InMemorySecureKeyValueStorage()
        val keyStore = SettingsChatKeyStore(keyValueStorage, secureKeyValueStorage, Json)

        val initialRevision = keyStore.currentKeyRevision()
        keyStore.saveCurrentUserKeys(
            userId = "user-id",
            publicKey = "user-public-key",
            privateKeyRef = PrivateKeyRef.Exported("user-private-key"),
        )
        keyStore.saveServerPublicKey("server-public-key")
        keyStore.saveChatKeyPair(
            chatId = "chat-id",
            publicKey = "chat-public-key",
            privateKeyRef = PrivateKeyRef.Exported("chat-private-key"),
        )
        keyStore.saveParticipants(
            chatId = "chat-id",
            participants = listOf(
                ChatParticipantKey(
                    userId = "user-id",
                    displayName = "Me",
                    publicKey = "user-public-key",
                    isCurrentUser = true,
                ),
            ),
        )

        val snapshot = keyStore.exportSnapshot()

        assertEquals("user-id", snapshot.currentUserId)
        assertEquals("server-public-key", snapshot.serverPublicKey)
        assertEquals(1, snapshot.chatKeys.size)
        assertEquals("chat-id", snapshot.chatKeys.first().chatId)
        assertEquals(1, snapshot.participantsByChat.size)
        assertTrue(initialRevision != keyStore.currentKeyRevision())
    }

    @Test
    fun exportsAndImportsSelfChatSyncKeys() = runBlocking {
        val source = SettingsChatKeyStore(InMemoryKeyValueStorage(), InMemorySecureKeyValueStorage(), Json)
        source.saveCurrentUserKeys(
            userId = "user-id",
            publicKey = "user-public-key",
            privateKeyRef = PrivateKeyRef.Exported("user-private-key"),
        )
        source.saveSelfChatId("self-chat-id")
        source.saveSelfChatKeyPair(
            publicKey = "self-public-key",
            privateKeyRef = PrivateKeyRef.Exported("self-private-key"),
        )

        val snapshot = source.exportSnapshot()
        val target = SettingsChatKeyStore(InMemoryKeyValueStorage(), InMemorySecureKeyValueStorage(), Json)

        target.importSnapshot(snapshot)

        assertEquals("self-chat-id", target.selfChatId())
        assertEquals("self-public-key", target.selfChatPublicKey())
        assertEquals(PrivateKeyRef.Exported("self-private-key"), target.selfChatPrivateKeyRef())
        assertEquals("self-public-key", target.chatPublicKey("self-chat-id"))
        assertEquals(PrivateKeyRef.Exported("self-private-key"), target.chatPrivateKeyRef("self-chat-id"))
    }

    private companion object {
        private const val CURRENT_USER_PRIVATE_KEY = "chat.keys.current_user.private"
        private const val CHAT_PRIVATE_KEY = "chat.keys.chat.private.chat-id"
    }
}

private class InMemoryKeyValueStorage : KeyValueStorage {
    private val values = mutableMapOf<String, String>()
    private val booleans = mutableMapOf<String, Boolean>()

    override fun observeString(key: String, defaultValue: String): Flow<String> {
        return flowOf(values[key] ?: defaultValue)
    }

    override fun observeBoolean(key: String, defaultValue: Boolean): Flow<Boolean> {
        return flowOf(booleans[key] ?: defaultValue)
    }

    override suspend fun getStringOrNull(key: String): String? = values[key]

    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return booleans[key] ?: defaultValue
    }

    override suspend fun putString(key: String, value: String) {
        values[key] = value
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        booleans[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
        booleans.remove(key)
    }

    override suspend fun clear() {
        values.clear()
        booleans.clear()
    }
}

private class InMemorySecureKeyValueStorage : SecureKeyValueStorage {
    private val values = mutableMapOf<String, String>()

    override suspend fun getStringOrNull(key: String): String? = values[key]

    override suspend fun putString(key: String, value: String) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    override suspend fun clear() {
        values.clear()
    }
}
