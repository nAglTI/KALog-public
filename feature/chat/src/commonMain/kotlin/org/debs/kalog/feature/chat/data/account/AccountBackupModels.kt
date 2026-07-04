package org.debs.kalog.feature.chat.data.account

import kotlinx.serialization.Serializable
import org.debs.kalog.feature.chat.data.crypto.ChatKeySnapshot

@Serializable
data class AccountBackupPayload(
    val schemaVersion: Int = ACCOUNT_BACKUP_SCHEMA_VERSION,
    val exportedAtEpochMillis: Long,
    val keys: ChatKeySnapshot,
    val preferences: AccountPreferencesBackup = AccountPreferencesBackup(),
)

@Serializable
data class AccountPreferencesBackup(
    val nickname: String = "",
    val userNicknames: Map<String, String> = emptyMap(),
    val chatTitles: Map<String, String> = emptyMap(),
)

data class AccountBackupFileRef(
    val fileName: String,
    val platformRef: String,
)

data class AccountBackupStatus(
    val lastFileName: String? = null,
    val lastCreatedAtEpochMillis: Long? = null,
    val canShareLastBackup: Boolean = false,
    val isLastBackupStale: Boolean = false,
    val deviceBoundPrivateKeyCount: Int = 0,
)

data class AccountBackupExportResult(
    val file: AccountBackupFileRef,
    val deviceBoundPrivateKeyCount: Int,
)

data class AccountBackupImportResult(
    val userId: String,
    val chatKeyCount: Int,
    val deviceBoundPrivateKeyCount: Int,
)

interface AccountBackupFileManager {
    val canPickBackupFile: Boolean

    suspend fun saveBackupFile(fileName: String, bytes: ByteArray): AccountBackupFileRef?

    suspend fun shareBackupFile(file: AccountBackupFileRef): Boolean

    suspend fun pickBackupFileBytes(): ByteArray?
}

internal const val ACCOUNT_BACKUP_SCHEMA_VERSION = 1
internal const val ACCOUNT_BACKUP_FILE_EXTENSION = "mcbak"
internal const val ACCOUNT_BACKUP_MIME_TYPE = "application/octet-stream"
