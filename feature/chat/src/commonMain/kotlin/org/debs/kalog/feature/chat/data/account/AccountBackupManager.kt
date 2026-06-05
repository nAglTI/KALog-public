package org.debs.kalog.feature.chat.data.account

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import org.debs.kalog.core.crypto.EncryptionService
import org.debs.kalog.core.crypto.PasswordProtectedAesGcmFile
import org.debs.kalog.core.crypto.PrivateKeyRef
import org.debs.kalog.core.preferences.KeyValueStorage
import org.debs.kalog.feature.chat.data.crypto.ChatKeySnapshot
import org.debs.kalog.feature.chat.data.crypto.ChatKeyStore
import org.debs.kalog.feature.chat.data.crypto.ChatParticipantKey
import org.debs.kalog.feature.chat.data.preferences.ChatPreferencesDataSource
import org.debs.kalog.feature.chat.domain.repository.ChatRepository
import org.debs.kalog.feature.chat.localization.chatLocalized

class AccountBackupManager(
    private val chatKeyStore: ChatKeyStore,
    private val chatPreferencesDataSource: ChatPreferencesDataSource,
    private val chatRepository: ChatRepository,
    private val keyValueStorage: KeyValueStorage,
    private val fileManager: AccountBackupFileManager,
    private val encryptionService: EncryptionService,
    private val json: Json,
) {
    fun observeStatus(): Flow<AccountBackupStatus> {
        return combine(
            chatKeyStore.observeKeyRevision(),
            keyValueStorage.observeString(LAST_BACKUP_REVISION, ""),
            keyValueStorage.observeString(LAST_BACKUP_FILE_NAME, ""),
            keyValueStorage.observeString(LAST_BACKUP_FILE_REF, ""),
            keyValueStorage.observeString(LAST_BACKUP_CREATED_AT, ""),
            keyValueStorage.observeString(LAST_BACKUP_DEVICE_BOUND_COUNT, "0"),
        ) { values ->
            val currentRevision = values[0].ifBlank { DEFAULT_REVISION }
            val backupRevision = values[1].ifBlank { DEFAULT_REVISION }
            val fileName = values[2].ifBlank { null }
            val fileRef = values[3].ifBlank { null }
            val createdAt = values[4].toLongOrNull()
            val deviceBoundCount = values[5].toIntOrNull()?.coerceAtLeast(0) ?: 0
            AccountBackupStatus(
                lastFileName = fileName,
                lastCreatedAtEpochMillis = createdAt,
                canShareLastBackup = fileName != null && fileRef != null && currentRevision == backupRevision,
                isLastBackupStale = fileName != null && currentRevision != backupRevision,
                deviceBoundPrivateKeyCount = deviceBoundCount,
            )
        }
    }

    suspend fun exportAccount(password: String): AccountBackupExportResult {
        require(password.length >= MIN_PASSWORD_LENGTH) {
            chatLocalized(
                en = "Backup password must be at least $MIN_PASSWORD_LENGTH characters long.",
                ru = "Пароль резервной копии должен быть не короче $MIN_PASSWORD_LENGTH символов.",
            )
        }
        val keySnapshot = chatKeyStore.exportSnapshot()
        val snapshot = keySnapshot.toPortableSnapshot()
        val exportedAt = Clock.System.now().toEpochMilliseconds()
        val payload = AccountBackupPayload(
            exportedAtEpochMillis = exportedAt,
            keys = snapshot,
            preferences = keySnapshot.toPreferencesBackup(),
        )
        val plaintext = json.encodeToString(payload).encodeToByteArray()
        val encryptedBytes = PasswordProtectedAesGcmFile.encrypt(
            plaintext = plaintext,
            password = password,
        )
        val fileName = buildFileName(snapshot.currentUserId, exportedAt)
        val file = requireNotNull(fileManager.saveBackupFile(fileName, encryptedBytes)) {
            chatLocalized(
                en = "Backup storage is unavailable on this platform.",
                ru = "Хранилище резервных копий недоступно на этой платформе.",
            )
        }
        val currentRevision = chatKeyStore.currentKeyRevision()
        val deviceBoundCount = snapshot.deviceBoundPrivateKeyCount()
        keyValueStorage.putString(LAST_BACKUP_REVISION, currentRevision)
        keyValueStorage.putString(LAST_BACKUP_FILE_NAME, file.fileName)
        keyValueStorage.putString(LAST_BACKUP_FILE_REF, file.platformRef)
        keyValueStorage.putString(LAST_BACKUP_CREATED_AT, exportedAt.toString())
        keyValueStorage.putString(LAST_BACKUP_DEVICE_BOUND_COUNT, deviceBoundCount.toString())
        return AccountBackupExportResult(
            file = file,
            deviceBoundPrivateKeyCount = deviceBoundCount,
        )
    }

    suspend fun shareLastBackup(): Boolean {
        val fileName = keyValueStorage.getStringOrNull(LAST_BACKUP_FILE_NAME)?.ifBlank { null } ?: return false
        val fileRef = keyValueStorage.getStringOrNull(LAST_BACKUP_FILE_REF)?.ifBlank { null } ?: return false
        val backupRevision = keyValueStorage.getStringOrNull(LAST_BACKUP_REVISION).orEmpty().ifBlank { DEFAULT_REVISION }
        if (chatKeyStore.currentKeyRevision() != backupRevision) return false
        return fileManager.shareBackupFile(AccountBackupFileRef(fileName = fileName, platformRef = fileRef))
    }

    suspend fun shareBackup(file: AccountBackupFileRef): Boolean {
        return fileManager.shareBackupFile(file)
    }

    suspend fun importAccountFromPicker(password: String): AccountBackupImportResult? {
        require(password.isNotBlank()) {
            chatLocalized(en = "Backup password is required.", ru = "Нужен пароль от резервной копии.")
        }
        val encryptedBytes = fileManager.pickBackupFileBytes() ?: return null
        val plaintext = PasswordProtectedAesGcmFile.decrypt(
            bytes = encryptedBytes,
            password = password,
        )
        val payload = json.decodeFromString<AccountBackupPayload>(plaintext.decodeToString())
        require(payload.schemaVersion == ACCOUNT_BACKUP_SCHEMA_VERSION) {
            chatLocalized(
                en = "Account backup version is not supported.",
                ru = "Версия резервной копии аккаунта не поддерживается.",
            )
        }
        val platformImport = payload.keys.toPlatformImport()
        try {
            chatRepository.clearAllData()
            chatKeyStore.importSnapshot(platformImport.snapshot)
            chatPreferencesDataSource.importBackup(payload.preferences)
        } catch (error: Throwable) {
            platformImport.deleteImportedPrivateKeys()
            throw error
        }
        return AccountBackupImportResult(
            userId = platformImport.snapshot.currentUserId,
            chatKeyCount = platformImport.snapshot.chatKeys.size,
            deviceBoundPrivateKeyCount = platformImport.snapshot.deviceBoundPrivateKeyCount(),
        )
    }

    private fun buildFileName(userId: String, exportedAtEpochMillis: Long): String {
        val safeUser = userId.take(8).ifBlank { "account" }
        return "mayday-account-$safeUser-$exportedAtEpochMillis.$ACCOUNT_BACKUP_FILE_EXTENSION"
    }

    private suspend fun ChatKeySnapshot.toPortableSnapshot(): ChatKeySnapshot {
        return copy(
            currentUserPrivateKeyRef = PrivateKeyRef.deserialize(currentUserPrivateKeyRef)
                .exportForBackup()
                .serialize(),
            chatKeys = chatKeys.map { keyPair ->
                keyPair.copy(
                    privateKeyRef = PrivateKeyRef.deserialize(keyPair.privateKeyRef)
                        .exportForBackup()
                        .serialize(),
                )
            },
        )
    }

    private suspend fun PrivateKeyRef.exportForBackup(): PrivateKeyRef.Exported {
        return encryptionService.exportPrivateKey(this)
    }

    private suspend fun ChatKeySnapshot.toPlatformImport(): PlatformSnapshotImport {
        val importedPrivateKeys = mutableListOf<PrivateKeyRef>()
        return try {
            val currentUserImportedPrivateKey = PrivateKeyRef.deserialize(currentUserPrivateKeyRef)
                .importFromBackup(
                    publicKey = currentUserPublicKey,
                    importedPrivateKeys = importedPrivateKeys,
                )
            val importedChatKeys = chatKeys.map { keyPair ->
                keyPair.copy(
                    privateKeyRef = PrivateKeyRef.deserialize(keyPair.privateKeyRef)
                        .importFromBackup(
                            publicKey = keyPair.publicKey,
                            importedPrivateKeys = importedPrivateKeys,
                        )
                        .serialize(),
                )
            }
            PlatformSnapshotImport(
                snapshot = copy(
                    currentUserPrivateKeyRef = currentUserImportedPrivateKey.serialize(),
                    chatKeys = importedChatKeys,
                ),
                importedPrivateKeys = importedPrivateKeys.toList(),
            )
        } catch (error: Throwable) {
            importedPrivateKeys.forEach { privateKeyRef ->
                runCatching { encryptionService.deletePrivateKey(privateKeyRef) }
            }
            throw error
        }
    }

    private suspend fun PrivateKeyRef.importFromBackup(
        publicKey: String,
        importedPrivateKeys: MutableList<PrivateKeyRef>,
    ): PrivateKeyRef {
        return when (this) {
            is PrivateKeyRef.Exported -> encryptionService.importPrivateKey(
                publicKey = publicKey,
                privateKey = this,
            ).also { importedPrivateKey ->
                importedPrivateKeys += importedPrivateKey
            }
            is PrivateKeyRef.PlatformAlias -> this
        }
    }

    private suspend fun PlatformSnapshotImport.deleteImportedPrivateKeys() {
        importedPrivateKeys.forEach { privateKeyRef ->
            runCatching { encryptionService.deletePrivateKey(privateKeyRef) }
        }
    }

    private suspend fun ChatKeySnapshot.toPreferencesBackup(): AccountPreferencesBackup {
        val participantNicknames = participantsByChat
            .flatMap { participants -> participants.participants }
            .mapNotNull { participant -> participant.toBackupNickname() }
            .toMap()
        val chatTitles = chatKeys
            .mapNotNull { keyPair ->
                val title = chatPreferencesDataSource.getChatTitle(keyPair.chatId)?.trim().orEmpty()
                if (title.isBlank()) null else keyPair.chatId to title
            }
            .toMap()
        return AccountPreferencesBackup(
            nickname = chatPreferencesDataSource.getNickname().trim(),
            userNicknames = participantNicknames,
            chatTitles = chatTitles,
        )
    }

    private suspend fun ChatParticipantKey.toBackupNickname(): Pair<String, String>? {
        if (isCurrentUser) return null
        val savedNickname = chatPreferencesDataSource.getUserNickname(userId)?.trim().orEmpty()
        val nickname = savedNickname.ifBlank {
            displayName.trim().takeIf { name -> name.isNotBlank() && name != userId }.orEmpty()
        }
        return if (nickname.isBlank()) null else userId to nickname
    }

    private suspend fun ChatPreferencesDataSource.importBackup(preferences: AccountPreferencesBackup) {
        saveNickname(preferences.nickname.trim())
        preferences.userNicknames.forEach { (userId, nickname) ->
            val normalizedNickname = nickname.trim()
            if (userId.isNotBlank() && normalizedNickname.isNotBlank()) {
                saveUserNickname(userId, normalizedNickname)
            }
        }
        preferences.chatTitles.forEach { (chatId, title) ->
            val normalizedTitle = title.trim()
            if (chatId.isNotBlank() && normalizedTitle.isNotBlank()) {
                saveChatTitle(chatId, normalizedTitle)
            }
        }
    }

    private fun ChatKeySnapshot.deviceBoundPrivateKeyCount(): Int {
        val refs = listOf(currentUserPrivateKeyRef) + chatKeys.map { key -> key.privateKeyRef }
        return refs.count { serialized ->
            runCatching { PrivateKeyRef.deserialize(serialized) }
                .getOrNull() is PrivateKeyRef.PlatformAlias
        }
    }

    private companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val DEFAULT_REVISION = "0"
        private const val LAST_BACKUP_REVISION = "account.backup.last.revision"
        private const val LAST_BACKUP_FILE_NAME = "account.backup.last.file_name"
        private const val LAST_BACKUP_FILE_REF = "account.backup.last.file_ref"
        private const val LAST_BACKUP_CREATED_AT = "account.backup.last.created_at"
        private const val LAST_BACKUP_DEVICE_BOUND_COUNT = "account.backup.last.device_bound_count"
    }
}

private data class PlatformSnapshotImport(
    val snapshot: ChatKeySnapshot,
    val importedPrivateKeys: List<PrivateKeyRef>,
)
