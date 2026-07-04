package org.debs.kalog.feature.chat.data.cache

import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.debs.kalog.core.crypto.ChaCha20Poly1305CryptoManager
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentPart

actual fun createChatAttachmentFileCache(): ChatAttachmentFileCache = JvmChatAttachmentFileCache()

private class JvmChatAttachmentFileCache(
    private val cacheDirectory: File = File(System.getProperty("java.io.tmpdir"), "kalog-secure-media-cache"),
) : ChatAttachmentFileCache {
    init {
        cacheDirectory.mkdirs()
        File(System.getProperty("java.io.tmpdir"), "kalog-media-cache")
            .takeIf(File::isDirectory)
            ?.deleteRecursively()
    }

    override suspend fun get(attachmentId: String): CachedChatAttachment? = withContext(Dispatchers.IO) {
        null
    }

    override suspend fun get(attachment: ChatAttachment): CachedChatAttachment? = withContext(Dispatchers.IO) {
        val key = attachment.decryptionKey ?: attachment.parts.firstNotNullOfOrNull(ChatAttachmentPart::key) ?: return@withContext null
        val safeId = attachment.id.safeFileName()
        if (attachment.parts.isEmpty()) {
            val file = encryptedFile(safeId)
            if (!file.isFile) return@withContext null
            return@withContext SecureJvmAttachmentStore.register(
                attachmentId = attachment.id,
                plainSizeBytes = attachment.sizeBytes ?: encryptedPayloadPlainSize(file.length()),
                chunks = listOf(
                    SecureJvmAttachmentChunkSpec(
                        file = file,
                        decryptionKey = key,
                        plainSizeBytes = attachment.sizeBytes ?: encryptedPayloadPlainSize(file.length()),
                    ),
                ),
            )
        }
        val chunkSpecs = attachment.parts.sortedBy(ChatAttachmentPart::index).mapNotNull { part ->
            val file = encryptedPartFile(safeId, part.index)
            if (!file.isFile) return@mapNotNull null
            SecureJvmAttachmentChunkSpec(
                file = file,
                decryptionKey = part.key ?: key,
                plainSizeBytes = part.sizeBytes ?: attachment.inferredPartPlainSize(part.index)
                    ?: encryptedPayloadPlainSize(file.length()),
            )
        }
        if (chunkSpecs.size != attachment.parts.size || chunkSpecs.isEmpty()) return@withContext null
        SecureJvmAttachmentStore.register(
            attachmentId = attachment.id,
            plainSizeBytes = attachment.sizeBytes ?: chunkSpecs.sumOf { it.plainSizeBytes },
            chunks = chunkSpecs,
        )
    }

    override suspend fun put(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        bytes: ByteArray,
    ): CachedChatAttachment = withContext(Dispatchers.IO) {
        PlainJvmAttachmentMemoryStore.put(attachmentId, bytes)
    }

    override suspend fun putEncrypted(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        encryptedBytes: ByteArray,
        plainSizeBytes: Long?,
        decryptionKey: String,
    ): CachedChatAttachment = withContext(Dispatchers.IO) {
        cacheDirectory.mkdirs()
        val safeId = attachmentId.safeFileName()
        deleteEncryptedFiles(safeId)
        val file = encryptedFile(safeId)
        file.writeBytes(encryptedBytes)
        file.setLastModified(System.currentTimeMillis())
        SecureJvmAttachmentStore.register(
            attachmentId = attachmentId,
            plainSizeBytes = plainSizeBytes ?: encryptedPayloadPlainSize(encryptedBytes.size.toLong()),
            chunks = listOf(
                SecureJvmAttachmentChunkSpec(
                    file = file,
                    decryptionKey = decryptionKey,
                    plainSizeBytes = plainSizeBytes ?: encryptedPayloadPlainSize(encryptedBytes.size.toLong()),
                ),
            ),
        )
    }

    override suspend fun readBytes(localUri: String): ByteArray? = withContext(Dispatchers.IO) {
        when {
            SecureJvmAttachmentStore.isSecureUri(localUri) -> SecureJvmAttachmentStore.readAll(localUri)
            PlainJvmAttachmentMemoryStore.isMemoryUri(localUri) -> PlainJvmAttachmentMemoryStore.readAll(localUri)
            else -> runCatching { File(URI(localUri)).takeIf(File::isFile)?.readBytes() }.getOrNull()
        }
    }

    override suspend fun readBytes(localUri: String, offset: Long, length: Int): ByteArray? = withContext(Dispatchers.IO) {
        when {
            SecureJvmAttachmentStore.isSecureUri(localUri) -> SecureJvmAttachmentStore.read(localUri, offset, length)
            PlainJvmAttachmentMemoryStore.isMemoryUri(localUri) -> PlainJvmAttachmentMemoryStore.read(localUri, offset, length)
            else -> runCatching { readFileBytes(File(URI(localUri)), offset, length) }.getOrNull()
        }
    }

    override suspend fun putFromChunks(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        chunks: suspend (suspend (ByteArray) -> Unit) -> Unit,
    ): CachedChatAttachment = withContext(Dispatchers.IO) {
        val output = java.io.ByteArrayOutputStream()
        chunks { bytes -> output.write(bytes) }
        PlainJvmAttachmentMemoryStore.put(attachmentId, output.toByteArray())
    }

    override suspend fun putEncryptedFromChunks(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        plainSizeBytes: Long?,
        chunks: suspend (suspend (EncryptedCachedAttachmentPart) -> Unit) -> Unit,
    ): CachedChatAttachment = withContext(Dispatchers.IO) {
        cacheDirectory.mkdirs()
        val safeId = attachmentId.safeFileName()
        deleteEncryptedFiles(safeId)
        val specs = mutableListOf<SecureJvmAttachmentChunkSpec>()
        chunks { part ->
            val file = encryptedPartFile(safeId, part.index)
            file.writeBytes(part.encryptedBytes)
            file.setLastModified(System.currentTimeMillis())
            specs += SecureJvmAttachmentChunkSpec(
                file = file,
                decryptionKey = part.decryptionKey,
                plainSizeBytes = part.plainSizeBytes ?: encryptedPayloadPlainSize(part.encryptedBytes.size.toLong()),
            )
        }
        SecureJvmAttachmentStore.register(
            attachmentId = attachmentId,
            plainSizeBytes = plainSizeBytes ?: specs.sumOf { it.plainSizeBytes },
            chunks = specs,
        )
    }

    override suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        SecureJvmAttachmentStore.clear()
        PlainJvmAttachmentMemoryStore.clear()
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

    private fun encryptedFile(safeId: String): File = File(cacheDirectory, "$safeId.enc")

    private fun encryptedPartFile(safeId: String, index: Int): File = File(cacheDirectory, "$safeId.part-$index.enc")

    private fun deleteEncryptedFiles(safeId: String) {
        cacheDirectory.listFiles()
            .orEmpty()
            .filter { file -> file.name == "$safeId.enc" || file.name.startsWith("$safeId.part-") }
            .forEach { file -> file.delete() }
    }
}

private fun String.safeFileName(): String {
    return replace(Regex("[^A-Za-z0-9._-]"), "_")
}

internal object SecureJvmAttachmentStore {
    private const val SCHEME = "kalog-secure-attachment"
    private val entries = ConcurrentHashMap<String, SecureJvmAttachmentEntry>()

    fun register(
        attachmentId: String,
        plainSizeBytes: Long,
        chunks: List<SecureJvmAttachmentChunkSpec>,
    ): CachedChatAttachment {
        val token = attachmentId.safeFileName()
        var offset = 0L
        val entryChunks = chunks.mapIndexed { index, chunk ->
            SecureJvmAttachmentChunk(
                index = index,
                file = chunk.file,
                decryptionKey = chunk.decryptionKey,
                plainSizeBytes = chunk.plainSizeBytes,
                startOffset = offset,
            ).also { offset += chunk.plainSizeBytes }
        }
        entries[token] = SecureJvmAttachmentEntry(token, plainSizeBytes, entryChunks)
        return CachedChatAttachment("$SCHEME://$token", plainSizeBytes)
    }

    fun isSecureUri(localUri: String?): Boolean = localUri?.startsWith("$SCHEME://") == true

    fun readAll(localUri: String): ByteArray? {
        val entry = entry(localUri) ?: return null
        val output = java.io.ByteArrayOutputStream(entry.sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val reader = SecureJvmAttachmentReader(entry)
        val buffer = ByteArray(SECURE_ATTACHMENT_READ_BUFFER_BYTES)
        var position = 0L
        while (position < entry.sizeBytes) {
            val read = reader.readAt(position, buffer, 0, buffer.size)
            if (read <= 0) break
            output.write(buffer, 0, read)
            position += read
        }
        return output.toByteArray()
    }

    fun read(localUri: String, offset: Long, length: Int): ByteArray? {
        val entry = entry(localUri) ?: return null
        if (offset >= entry.sizeBytes) return ByteArray(0)
        val targetSize = minOf(length.toLong(), entry.sizeBytes - offset).toInt()
        val bytes = ByteArray(targetSize)
        val read = SecureJvmAttachmentReader(entry).readAt(offset, bytes, 0, targetSize)
        return if (read <= 0) ByteArray(0) else bytes.copyOf(read)
    }

    fun clear() {
        entries.clear()
    }

    private fun entry(localUri: String): SecureJvmAttachmentEntry? = entries[URI(localUri).host]
}

internal class SecureJvmAttachmentReader(
    private val entry: SecureJvmAttachmentEntry,
) {
    private var cachedChunkIndex = -1
    private var cachedChunkBytes = ByteArray(0)

    fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size == 0) return 0
        if (position < 0L || position >= entry.sizeBytes) return -1
        val chunk = entry.chunks.lastOrNull { position >= it.startOffset }
            ?.takeIf { position < it.startOffset + it.plainSizeBytes }
            ?: return -1
        val chunkBytes = loadChunk(chunk)
        val startInChunk = (position - chunk.startOffset).toInt().coerceAtLeast(0)
        if (startInChunk >= chunkBytes.size) return -1
        val bytesToCopy = minOf(size, chunkBytes.size - startInChunk, (entry.sizeBytes - position).toInt())
        chunkBytes.copyInto(buffer, offset, startInChunk, startInChunk + bytesToCopy)
        return bytesToCopy
    }

    private fun loadChunk(chunk: SecureJvmAttachmentChunk): ByteArray {
        if (cachedChunkIndex == chunk.index) return cachedChunkBytes
        cachedChunkBytes = runBlocking {
            ChaCha20Poly1305CryptoManager.decrypt(chunk.file.readBytes(), chunk.decryptionKey)
        }
        cachedChunkIndex = chunk.index
        return cachedChunkBytes
    }
}

internal data class SecureJvmAttachmentEntry(
    val token: String,
    val sizeBytes: Long,
    val chunks: List<SecureJvmAttachmentChunk>,
)

internal data class SecureJvmAttachmentChunk(
    val index: Int,
    val file: File,
    val decryptionKey: String,
    val plainSizeBytes: Long,
    val startOffset: Long,
)

internal data class SecureJvmAttachmentChunkSpec(
    val file: File,
    val decryptionKey: String,
    val plainSizeBytes: Long,
)

private object PlainJvmAttachmentMemoryStore {
    private const val SCHEME = "kalog-memory-attachment"
    private val entries = ConcurrentHashMap<String, ByteArray>()

    fun put(attachmentId: String, bytes: ByteArray): CachedChatAttachment {
        val token = attachmentId.safeFileName()
        entries[token] = bytes
        return CachedChatAttachment("$SCHEME://$token", bytes.size.toLong())
    }

    fun isMemoryUri(localUri: String?): Boolean = localUri?.startsWith("$SCHEME://") == true

    fun readAll(localUri: String): ByteArray? = entries[URI(localUri).host]

    fun read(localUri: String, offset: Long, length: Int): ByteArray? {
        val bytes = readAll(localUri) ?: return null
        if (offset >= bytes.size) return ByteArray(0)
        val startIndex = offset.toInt()
        val endIndex = minOf(startIndex + length, bytes.size)
        return bytes.copyOfRange(startIndex, endIndex)
    }

    fun clear() {
        entries.clear()
    }
}

private fun readFileBytes(file: File, offset: Long, length: Int): ByteArray? {
    if (!file.isFile) return null
    if (offset >= file.length()) return ByteArray(0)
    val bytesToRead = minOf(length.toLong(), file.length() - offset).toInt()
    val bytes = ByteArray(bytesToRead)
    RandomAccessFile(file, "r").use { input ->
        input.seek(offset)
        input.readFully(bytes)
    }
    return bytes
}

private fun ChatAttachment.inferredPartPlainSize(index: Int): Long? {
    val chunkSize = chunkSizeBytes ?: return null
    val totalSize = sizeBytes ?: return null
    if (index < 0 || chunkSize <= 0L || totalSize < 0L) return null
    val chunkStart = chunkSize * index
    if (chunkStart >= totalSize) return 0L
    return minOf(chunkSize, totalSize - chunkStart)
}

private fun encryptedPayloadPlainSize(encryptedSize: Long): Long {
    return (encryptedSize - CHACHA_NONCE_BYTES - POLY1305_TAG_BYTES).coerceAtLeast(0L)
}

private const val CHACHA_NONCE_BYTES = 12L
private const val POLY1305_TAG_BYTES = 16L
private const val SECURE_ATTACHMENT_READ_BUFFER_BYTES = 64 * 1024
