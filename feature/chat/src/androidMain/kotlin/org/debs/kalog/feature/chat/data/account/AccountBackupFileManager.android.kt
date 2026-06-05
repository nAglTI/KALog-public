package org.debs.kalog.feature.chat.data.account

import org.debs.kalog.feature.chat.presentation.platform.AndroidChatPlatformBridge

actual fun createAccountBackupFileManager(): AccountBackupFileManager = AndroidAccountBackupFileManager()

private class AndroidAccountBackupFileManager : AccountBackupFileManager {
    override val canPickBackupFile: Boolean = true

    override suspend fun saveBackupFile(fileName: String, bytes: ByteArray): AccountBackupFileRef? {
        return AndroidChatPlatformBridge.saveAccountBackupFile(fileName, bytes)
    }

    override suspend fun shareBackupFile(file: AccountBackupFileRef): Boolean {
        return AndroidChatPlatformBridge.shareAccountBackupFile(file)
    }

    override suspend fun pickBackupFileBytes(): ByteArray? {
        return AndroidChatPlatformBridge.pickAccountBackupFileBytes()
    }
}

