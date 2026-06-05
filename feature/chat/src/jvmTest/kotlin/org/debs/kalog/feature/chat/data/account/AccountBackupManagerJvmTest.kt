package org.debs.kalog.feature.chat.data.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.debs.kalog.core.crypto.EncryptionService
import org.debs.kalog.core.crypto.GeneratedAttachmentKey
import org.debs.kalog.core.crypto.GeneratedKeyPair
import org.debs.kalog.core.crypto.PasswordProtectedAesGcmFile
import org.debs.kalog.core.crypto.PrivateKeyRef
import org.debs.kalog.core.preferences.KeyValueStorage
import org.debs.kalog.feature.chat.data.crypto.ChatKeySnapshot
import org.debs.kalog.feature.chat.data.crypto.ChatKeyStore
import org.debs.kalog.feature.chat.data.crypto.ChatParticipantKey
import org.debs.kalog.feature.chat.data.crypto.StoredChatKeyPair
import org.debs.kalog.feature.chat.data.crypto.StoredChatParticipants
import org.debs.kalog.feature.chat.data.preferences.AppThemeMode
import org.debs.kalog.feature.chat.data.preferences.ChatPreferencesDataSource
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatParticipant
import org.debs.kalog.feature.chat.domain.model.ChatThread
import org.debs.kalog.feature.chat.domain.model.PreparedChatAttachment
import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class AccountBackupManagerJvmTest {
    @Test
    fun importDoesNotTouchKeyStoreAndDeletesStagedKeysWhenAnyKeyImportFails() = runBlocking {
        val snapshot = backupSnapshot()
        val encryptionService = FakeEncryptionService(failOnImportNumber = 2)
        val keyStore = FakeChatKeyStore()
        val repository = FakeChatRepository()
        val manager = AccountBackupManager(
            chatKeyStore = keyStore,
            chatPreferencesDataSource = FakeChatPreferencesDataSource(),
            chatRepository = repository,
            keyValueStorage = FakeKeyValueStorage(),
            fileManager = FakeAccountBackupFileManager(
                bytes = encryptedBackup(snapshot),
            ),
            encryptionService = encryptionService,
            json = Json,
        )

        assertFailsWith<IllegalStateException> {
            manager.importAccountFromPicker(BACKUP_PASSWORD)
        }

        assertNull(keyStore.importedSnapshot)
        assertEquals(0, repository.clearAllDataCalls)
        assertEquals(
            listOf<PrivateKeyRef>(PrivateKeyRef.PlatformAlias(provider = "test-provider", alias = "alias-1")),
            encryptionService.deletedPrivateKeys,
        )
    }

    @Test
    fun importStoresPlatformPrivateKeyRefsAfterEveryKeyIsImported() = runBlocking {
        val snapshot = backupSnapshot()
        val keyStore = FakeChatKeyStore()
        val repository = FakeChatRepository()
        val manager = AccountBackupManager(
            chatKeyStore = keyStore,
            chatPreferencesDataSource = FakeChatPreferencesDataSource(),
            chatRepository = repository,
            keyValueStorage = FakeKeyValueStorage(),
            fileManager = FakeAccountBackupFileManager(
                bytes = encryptedBackup(snapshot),
            ),
            encryptionService = FakeEncryptionService(),
            json = Json,
        )

        manager.importAccountFromPicker(BACKUP_PASSWORD)

        val importedSnapshot = requireNotNull(keyStore.importedSnapshot)
        assertTrue(importedSnapshot.currentUserPrivateKeyRef.startsWith("platform:test-provider:"))
        assertTrue(importedSnapshot.chatKeys.all { keyPair ->
            keyPair.privateKeyRef.startsWith("platform:test-provider:")
        })
        assertEquals(1, repository.clearAllDataCalls)
    }

    @Test
    fun exportIncludesNicknamesAndChatTitles() = runBlocking {
        val snapshot = backupSnapshot()
        val fileManager = FakeAccountBackupFileManager()
        val manager = AccountBackupManager(
            chatKeyStore = FakeChatKeyStore(snapshotForExport = snapshot),
            chatPreferencesDataSource = FakeChatPreferencesDataSource(
                nickname = "android",
                userNicknames = mutableMapOf("abobus-user-id" to "abobus1"),
                chatTitles = mutableMapOf("chat-1" to "abobus1"),
            ),
            chatRepository = FakeChatRepository(),
            keyValueStorage = FakeKeyValueStorage(),
            fileManager = fileManager,
            encryptionService = FakeEncryptionService(),
            json = Json,
        )

        manager.exportAccount(BACKUP_PASSWORD)

        val encryptedBytes = requireNotNull(fileManager.savedBytes)
        val plaintext = PasswordProtectedAesGcmFile.decrypt(encryptedBytes, BACKUP_PASSWORD)
        val payload = Json.decodeFromString<AccountBackupPayload>(plaintext.decodeToString())
        assertEquals("android", payload.preferences.nickname)
        assertEquals("abobus1", payload.preferences.userNicknames["abobus-user-id"])
        assertEquals("abobus1", payload.preferences.chatTitles["chat-1"])
    }

    @Test
    fun importClearsLocalDataAndRestoresPreferencesAfterKeyStagingSucceeds() = runBlocking {
        val preferences = AccountPreferencesBackup(
            nickname = "android",
            userNicknames = mapOf("abobus-user-id" to "abobus1"),
            chatTitles = mapOf("chat-1" to "abobus1"),
        )
        val repository = FakeChatRepository()
        val preferencesDataSource = FakeChatPreferencesDataSource()
        val manager = AccountBackupManager(
            chatKeyStore = FakeChatKeyStore(),
            chatPreferencesDataSource = preferencesDataSource,
            chatRepository = repository,
            keyValueStorage = FakeKeyValueStorage(),
            fileManager = FakeAccountBackupFileManager(
                bytes = encryptedBackup(backupSnapshot(), preferences),
            ),
            encryptionService = FakeEncryptionService(),
            json = Json,
        )

        manager.importAccountFromPicker(BACKUP_PASSWORD)

        assertEquals(1, repository.clearAllDataCalls)
        assertEquals("android", preferencesDataSource.nickname)
        assertEquals("abobus1", preferencesDataSource.userNicknames["abobus-user-id"])
        assertEquals("abobus1", preferencesDataSource.chatTitles["chat-1"])
    }

    private suspend fun encryptedBackup(
        snapshot: ChatKeySnapshot,
        preferences: AccountPreferencesBackup = AccountPreferencesBackup(),
    ): ByteArray {
        val payload = AccountBackupPayload(
            exportedAtEpochMillis = 1L,
            keys = snapshot,
            preferences = preferences,
        )
        return PasswordProtectedAesGcmFile.encrypt(
            plaintext = Json.encodeToString(payload).encodeToByteArray(),
            password = BACKUP_PASSWORD,
        )
    }

    private fun backupSnapshot(): ChatKeySnapshot {
        return ChatKeySnapshot(
            currentUserId = "user-id",
            currentUserPublicKey = "user-public-key",
            currentUserPrivateKeyRef = PrivateKeyRef.Exported("user-private-key").serialize(),
            serverPublicKey = "server-public-key",
            chatKeys = listOf(
                StoredChatKeyPair(
                    chatId = "chat-1",
                    publicKey = "chat-1-public-key",
                    privateKeyRef = PrivateKeyRef.Exported("chat-1-private-key").serialize(),
                ),
                StoredChatKeyPair(
                    chatId = "chat-2",
                    publicKey = "chat-2-public-key",
                    privateKeyRef = PrivateKeyRef.Exported("chat-2-private-key").serialize(),
                ),
            ),
            participantsByChat = listOf(
                StoredChatParticipants(
                    chatId = "chat-1",
                    participants = listOf(
                        ChatParticipantKey(
                            userId = "user-id",
                            displayName = "Me",
                            publicKey = "user-public-key",
                            isCurrentUser = true,
                        ),
                        ChatParticipantKey(
                            userId = "abobus-user-id",
                            displayName = "abobus1",
                            publicKey = "abobus-public-key",
                            isCurrentUser = false,
                        ),
                    ),
                ),
            ),
        )
    }

    private companion object {
        private const val BACKUP_PASSWORD = "backup-password"
    }
}

private class FakeAccountBackupFileManager(
    private val bytes: ByteArray = ByteArray(0),
) : AccountBackupFileManager {
    var savedBytes: ByteArray? = null
        private set

    override val canPickBackupFile: Boolean = true

    override suspend fun saveBackupFile(fileName: String, bytes: ByteArray): AccountBackupFileRef {
        savedBytes = bytes
        return AccountBackupFileRef(fileName = fileName, platformRef = "memory://$fileName")
    }

    override suspend fun shareBackupFile(file: AccountBackupFileRef): Boolean = false

    override suspend fun pickBackupFileBytes(): ByteArray = bytes
}

private class FakeEncryptionService(
    private val failOnImportNumber: Int? = null,
) : EncryptionService {
    private var importCount = 0
    val deletedPrivateKeys = mutableListOf<PrivateKeyRef>()

    override val algorithmLabel: String = "fake"
    override val maxPayloadBytesPerChunk: Int = 1

    override suspend fun generateKeyPair(): GeneratedKeyPair = error("Unused")

    override suspend fun generateAttachmentKey(): GeneratedAttachmentKey = error("Unused")

    override suspend fun encrypt(message: String, publicKey: String): String = error("Unused")

    override suspend fun decrypt(message: String, privateKey: String): String = error("Unused")

    override suspend fun encryptAttachment(bytes: ByteArray, key: String): ByteArray = error("Unused")

    override suspend fun decryptAttachment(bytes: ByteArray, key: String): ByteArray = error("Unused")

    override suspend fun encryptToChunks(
        message: String,
        publicKey: String,
        chunkSizeBytes: Int,
    ): List<String> = error("Unused")

    override suspend fun decryptFromChunks(chunks: List<String>, privateKey: String): String = error("Unused")

    override suspend fun importPrivateKey(
        publicKey: String,
        privateKey: PrivateKeyRef.Exported,
    ): PrivateKeyRef {
        importCount += 1
        if (importCount == failOnImportNumber) {
            error("Key import failed")
        }
        return PrivateKeyRef.PlatformAlias(
            provider = "test-provider",
            alias = "alias-$importCount",
        )
    }

    override suspend fun deletePrivateKey(privateKeyRef: PrivateKeyRef) {
        deletedPrivateKeys += privateKeyRef
    }
}

private class FakeChatKeyStore(
    private val snapshotForExport: ChatKeySnapshot? = null,
) : ChatKeyStore {
    var importedSnapshot: ChatKeySnapshot? = null

    override fun observeKeyRevision(): Flow<String> = flowOf("0")

    override suspend fun currentKeyRevision(): String = "0"

    override suspend fun currentUserId(): String? = null

    override suspend fun currentUserPublicKey(): String? = null

    override suspend fun currentUserPrivateKey(): String? = null

    override suspend fun serverPublicKey(): String? = null

    override suspend fun saveCurrentUserKeys(userId: String?, publicKey: String, privateKey: String) = Unit

    override suspend fun saveCurrentUserId(userId: String) = Unit

    override suspend fun saveServerPublicKey(publicKey: String) = Unit

    override suspend fun chatPublicKey(chatId: String): String? = null

    override suspend fun chatPrivateKey(chatId: String): String? = null

    override suspend fun saveChatKeyPair(chatId: String, publicKey: String, privateKey: String) = Unit

    override suspend fun participantsFor(chatId: String): List<ChatParticipantKey> = emptyList()

    override suspend fun saveParticipants(chatId: String, participants: List<ChatParticipantKey>) = Unit

    override suspend fun clearChatState(chatId: String) = Unit

    override suspend fun exportSnapshot(): ChatKeySnapshot = snapshotForExport ?: error("Unused")

    override suspend fun importSnapshot(snapshot: ChatKeySnapshot) {
        importedSnapshot = snapshot
    }

    override suspend fun clearAll() = Unit
}

private class FakeChatPreferencesDataSource(
    var nickname: String = "",
    val userNicknames: MutableMap<String, String> = mutableMapOf(),
    val chatTitles: MutableMap<String, String> = mutableMapOf(),
) : ChatPreferencesDataSource {
    override fun observeLastOpenedChatId(): Flow<String?> = flowOf(null)

    override suspend fun currentOpenedChatId(): String? = null

    override suspend fun saveLastOpenedChatId(chatId: String) = Unit

    override suspend fun clearLastOpenedChatId() = Unit

    override suspend fun getLastPollTimestamp(): String? = null

    override suspend fun saveLastPollTimestamp(timestamp: String) = Unit

    override suspend fun clearLastPollTimestamp() = Unit

    override fun observeNickname(): Flow<String> = flowOf(nickname)

    override suspend fun getNickname(): String = nickname

    override suspend fun saveNickname(nickname: String) {
        this.nickname = nickname
    }

    override suspend fun getUserNickname(userId: String): String? = userNicknames[userId]

    override suspend fun saveUserNickname(userId: String, nickname: String) {
        userNicknames[userId] = nickname
    }

    override suspend fun getChatTitle(chatId: String): String? = chatTitles[chatId]

    override suspend fun saveChatTitle(chatId: String, title: String) {
        chatTitles[chatId] = title
    }

    override suspend fun clearAll() {
        nickname = ""
        userNicknames.clear()
        chatTitles.clear()
    }

    override suspend fun isDebugModeEnabled(): Boolean = false

    override suspend fun setDebugModeEnabled(enabled: Boolean) = Unit

    override suspend fun getMediaCacheRetentionDays(): Int = 7

    override suspend fun saveMediaCacheRetentionDays(days: Int) = Unit

    override fun observeThemeMode(): Flow<AppThemeMode> = flowOf(AppThemeMode.System)

    override suspend fun getThemeMode(): AppThemeMode = AppThemeMode.System

    override suspend fun saveThemeMode(themeMode: AppThemeMode) = Unit

    override fun observeDesktopAutostartEnabled(): Flow<Boolean> = flowOf(true)

    override suspend fun isDesktopAutostartEnabled(): Boolean = true

    override suspend fun setDesktopAutostartEnabled(enabled: Boolean) = Unit

    override suspend fun isAccountOnboardingCompleted(): Boolean = false

    override suspend fun setAccountOnboardingCompleted(completed: Boolean) = Unit
}

private class FakeChatRepository : ChatRepository {
    var clearAllDataCalls = 0
        private set

    override suspend fun startSession() = Unit

    override suspend fun runSyncLoop() = Unit

    override suspend fun getCurrentUserId(): String? = null

    override suspend fun clearAllData() {
        clearAllDataCalls += 1
    }

    override suspend fun clearCachedAttachments(): Int = 0

    override suspend fun clearCachedAttachmentsOlderThan(ageMillis: Long): Int = 0

    override fun observeChats(): Flow<List<ChatThread>> = flowOf(emptyList())

    override fun observeChat(chatId: String): Flow<ChatThread?> = flowOf(null)

    override suspend fun closeChat() = Unit

    override suspend fun openChat(chatId: String) = Unit

    override suspend fun loadMoreMessages(chatId: String): Boolean = false

    override suspend fun sendMessage(
        chatId: String,
        plainText: String,
        attachments: List<PreparedChatAttachment>,
    ) = Unit

    override suspend fun prepareAttachment(
        chatId: String,
        attachment: ChatAttachment,
        onUploadProgress: (bytesSent: Long, totalBytes: Long) -> Unit,
    ): PreparedChatAttachment = error("Unused")

    override suspend fun requestAttachmentDownload(chatId: String, attachmentId: String) = Unit

    override suspend fun createDirectChat(targetUserId: String): String = error("Unused")

    override suspend fun createGroupChat(publicKey: String?): String = error("Unused")

    override suspend fun inviteUserToChat(chatId: String, userId: String) = Unit

    override suspend fun leaveGroupChat(chatId: String) = Unit

    override suspend fun setGroupChatPublicKey(chatId: String, publicKey: String) = Unit

    override suspend fun renameChatLocally(chatId: String, newTitle: String) = Unit

    override suspend fun getChatParticipants(chatId: String): List<ChatParticipant> = emptyList()

    override suspend fun findDirectChatWith(userId: String): String? = null

    override suspend fun acceptChatInvitation(chatId: String) = Unit

    override suspend fun declineChatInvitation(chatId: String) = Unit

    override suspend fun broadcastNicknameToAllChats() = Unit
}

private class FakeKeyValueStorage : KeyValueStorage {
    override fun observeString(key: String, defaultValue: String): Flow<String> = flowOf(defaultValue)

    override fun observeBoolean(key: String, defaultValue: Boolean): Flow<Boolean> = flowOf(defaultValue)

    override suspend fun getStringOrNull(key: String): String? = null

    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue

    override suspend fun putString(key: String, value: String) = Unit

    override suspend fun putBoolean(key: String, value: Boolean) = Unit

    override suspend fun remove(key: String) = Unit

    override suspend fun clear() = Unit
}
