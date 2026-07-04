package org.debs.kalog.feature.chat.data.account

actual fun createAccountBackupFileManager(): AccountBackupFileManager = IosAccountBackupFileManager()

private class IosAccountBackupFileManager : AccountBackupFileManager {
    override val canPickBackupFile: Boolean = false

    override suspend fun saveBackupFile(fileName: String, bytes: ByteArray): AccountBackupFileRef? = null

    override suspend fun shareBackupFile(file: AccountBackupFileRef): Boolean = false

    override suspend fun pickBackupFileBytes(): ByteArray? = null
}

