package org.debs.kalog.feature.chat.data.cache

import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun createChatAttachmentFileCache(): ChatAttachmentFileCache = JvmChatAttachmentFileCache()

private class JvmChatAttachmentFileCache(
    private val cacheDirectory: File = File(System.getProperty("java.io.tmpdir"), "kalog-media-cache"),
) : ChatAttachmentFileCache {
    override suspend fun get(attachmentId: String): CachedChatAttachment? = withContext(Dispatchers.IO) {
        val file = cacheDirectory.listFiles()
            ?.firstOrNull { cachedFile -> cachedFile.nameWithoutExtension == attachmentId.safeFileName() }
            ?: return@withContext null

        CachedChatAttachment(
            localUri = file.toURI().toString(),
            sizeBytes = file.length(),
        )
    }

    override suspend fun put(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        bytes: ByteArray,
    ): CachedChatAttachment = withContext(Dispatchers.IO) {
        cacheDirectory.mkdirs()
        val file = File(cacheDirectory, "${attachmentId.safeFileName()}${fileName.extensionOrEmpty(mimeType)}")
        file.writeBytes(bytes)
        file.setLastModified(System.currentTimeMillis())
        CachedChatAttachment(
            localUri = file.toURI().toString(),
            sizeBytes = bytes.size.toLong(),
        )
    }

    override suspend fun readBytes(localUri: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { File(java.net.URI(localUri)).takeIf(File::isFile)?.readBytes() }.getOrNull()
    }

    override suspend fun readBytes(localUri: String, offset: Long, length: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(java.net.URI(localUri)).takeIf(File::isFile) ?: return@runCatching null
            if (offset >= file.length()) return@runCatching ByteArray(0)
            val bytesToRead = minOf(length.toLong(), file.length() - offset).toInt()
            val bytes = ByteArray(bytesToRead)
            RandomAccessFile(file, "r").use { input ->
                input.seek(offset)
                input.readFully(bytes)
            }
            bytes
        }.getOrNull()
    }

    override suspend fun putFromChunks(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        chunks: suspend (suspend (ByteArray) -> Unit) -> Unit,
    ): CachedChatAttachment = withContext(Dispatchers.IO) {
        cacheDirectory.mkdirs()
        val file = File(cacheDirectory, "${attachmentId.safeFileName()}${fileName.extensionOrEmpty(mimeType)}")
        var sizeBytes = 0L
        file.outputStream().use { output ->
            chunks { bytes ->
                output.write(bytes)
                sizeBytes += bytes.size
            }
        }
        file.setLastModified(System.currentTimeMillis())
        CachedChatAttachment(
            localUri = file.toURI().toString(),
            sizeBytes = sizeBytes,
        )
    }

    override suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        val files = cacheDirectory.listFiles().orEmpty()
        files.count { file -> file.deleteRecursively() }
    }

    override suspend fun clearOlderThan(ageMillis: Long): Int = withContext(Dispatchers.IO) {
        val cutoffMillis = System.currentTimeMillis() - ageMillis
        cacheDirectory.listFiles()
            .orEmpty()
            .filter { file -> file.lastModified() < cutoffMillis }
            .count { file -> file.deleteRecursively() }
    }
}

private fun String.safeFileName(): String {
    return replace(Regex("[^A-Za-z0-9._-]"), "_")
}

private fun String?.extensionOrEmpty(mimeType: String?): String {
    val explicitExtension = this
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { extension -> extension.isNotBlank() && extension.length <= 8 }
    if (explicitExtension != null) return ".$explicitExtension"

    return when (mimeType?.lowercase()) {
        "image/jpeg" -> ".jpg"
        "image/png" -> ".png"
        "image/gif" -> ".gif"
        "image/webp" -> ".webp"
        "video/mp4" -> ".mp4"
        "video/webm" -> ".webm"
        "audio/mpeg" -> ".mp3"
        "audio/mp4" -> ".m4a"
        "audio/ogg" -> ".ogg"
        else -> ""
    }
}
