package org.debs.kalog.feature.chat.data.cache

import org.debs.kalog.feature.chat.domain.model.ChatAttachment

interface ChatAttachmentFileCache {
    suspend fun get(attachmentId: String): CachedChatAttachment?

    suspend fun get(attachment: ChatAttachment): CachedChatAttachment? = get(attachment.id)

    suspend fun put(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        bytes: ByteArray,
    ): CachedChatAttachment

    suspend fun putEncrypted(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        encryptedBytes: ByteArray,
        plainSizeBytes: Long?,
        decryptionKey: String,
    ): CachedChatAttachment

    suspend fun readBytes(localUri: String): ByteArray?

    suspend fun readBytes(localUri: String, offset: Long, length: Int): ByteArray?

    suspend fun putFromChunks(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        chunks: suspend (suspend (ByteArray) -> Unit) -> Unit,
    ): CachedChatAttachment

    suspend fun putEncryptedFromChunks(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        plainSizeBytes: Long?,
        chunks: suspend (suspend (EncryptedCachedAttachmentPart) -> Unit) -> Unit,
    ): CachedChatAttachment

    suspend fun clearAll(): Int

    suspend fun clearOlderThan(ageMillis: Long): Int
}

data class CachedChatAttachment(
    val localUri: String,
    val sizeBytes: Long,
)

data class EncryptedCachedAttachmentPart(
    val id: String,
    val index: Int,
    val encryptedBytes: ByteArray,
    val plainSizeBytes: Long?,
    val decryptionKey: String,
)

expect fun createChatAttachmentFileCache(): ChatAttachmentFileCache

object NoOpChatAttachmentFileCache : ChatAttachmentFileCache {
    override suspend fun get(attachmentId: String): CachedChatAttachment? = null

    override suspend fun put(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        bytes: ByteArray,
    ): CachedChatAttachment {
        return CachedChatAttachment(
            localUri = "memory://$attachmentId",
            sizeBytes = bytes.size.toLong(),
        )
    }

    override suspend fun putEncrypted(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        encryptedBytes: ByteArray,
        plainSizeBytes: Long?,
        decryptionKey: String,
    ): CachedChatAttachment {
        return CachedChatAttachment(
            localUri = "memory://$attachmentId",
            sizeBytes = plainSizeBytes ?: encryptedBytes.size.toLong(),
        )
    }

    override suspend fun readBytes(localUri: String): ByteArray? = null

    override suspend fun readBytes(localUri: String, offset: Long, length: Int): ByteArray? = null

    override suspend fun putFromChunks(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        chunks: suspend (suspend (ByteArray) -> Unit) -> Unit,
    ): CachedChatAttachment {
        var sizeBytes = 0L
        chunks { bytes -> sizeBytes += bytes.size }
        return CachedChatAttachment(
            localUri = "memory://$attachmentId",
            sizeBytes = sizeBytes,
        )
    }

    override suspend fun putEncryptedFromChunks(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        plainSizeBytes: Long?,
        chunks: suspend (suspend (EncryptedCachedAttachmentPart) -> Unit) -> Unit,
    ): CachedChatAttachment {
        var sizeBytes = 0L
        chunks { part -> sizeBytes += part.plainSizeBytes ?: part.encryptedBytes.size.toLong() }
        return CachedChatAttachment(
            localUri = "memory://$attachmentId",
            sizeBytes = plainSizeBytes ?: sizeBytes,
        )
    }

    override suspend fun clearAll(): Int = 0

    override suspend fun clearOlderThan(ageMillis: Long): Int = 0
}
