package org.debs.kalog.feature.chat.data.account

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.FilenameFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun createAccountBackupFileManager(): AccountBackupFileManager = JvmAccountBackupFileManager()

private class JvmAccountBackupFileManager : AccountBackupFileManager {
    override val canPickBackupFile: Boolean = true

    override suspend fun saveBackupFile(fileName: String, bytes: ByteArray): AccountBackupFileRef? = withContext(Dispatchers.IO) {
        val directory = backupDirectory()
        directory.mkdirs()
        val file = directory.resolve(fileName)
        file.writeBytes(bytes)
        AccountBackupFileRef(fileName = file.name, platformRef = file.absolutePath)
    }

    override suspend fun shareBackupFile(file: AccountBackupFileRef): Boolean = withContext(Dispatchers.IO) {
        val backupFile = File(file.platformRef)
        if (!backupFile.isFile) return@withContext false
        revealFile(backupFile)
    }

    override suspend fun pickBackupFileBytes(): ByteArray? = withContext(Dispatchers.IO) {
        if (GraphicsEnvironment.isHeadless()) return@withContext null
        val dialog = FileDialog(null as Frame?, "Import Mayday account", FileDialog.LOAD).apply {
            filenameFilter = FilenameFilter { _, name ->
                name.endsWith(".$ACCOUNT_BACKUP_FILE_EXTENSION", ignoreCase = true)
            }
        }
        dialog.isVisible = true
        val selectedFileName = dialog.file ?: return@withContext null
        val selectedDirectory = dialog.directory ?: return@withContext null
        val file = File(selectedDirectory, selectedFileName)
        file.takeIf(File::isFile)?.readBytes()
    }

    private fun backupDirectory(): File {
        val home = File(System.getProperty("user.home"))
        val downloads = home.resolve("Downloads")
        return if (downloads.isDirectory || downloads.mkdirs()) {
            downloads.resolve("Mayday Chat Backups")
        } else {
            home.resolve("Mayday Chat Backups")
        }
    }

    private fun revealFile(file: File): Boolean {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val directory = file.parentFile ?: return false
        val command = when {
            osName.contains("windows") -> listOf("explorer.exe", directory.absolutePath)
            osName.contains("mac") -> listOf("open", "-R", file.absolutePath)
            else -> emptyList()
        }
        if (command.isNotEmpty()) {
            val started = runCatching { ProcessBuilder(command).start() }.isSuccess
            if (started) return true
        }

        return runCatching {
            if (!Desktop.isDesktopSupported()) return false
            Desktop.getDesktop().open(directory)
            true
        }.getOrDefault(false)
    }
}
