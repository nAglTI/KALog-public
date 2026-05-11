package org.debs.kalog.feature.chat.data.cache

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
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
import org.koin.core.context.GlobalContext

actual fun createChatAttachmentFileCache(): ChatAttachmentFileCache = AndroidChatAttachmentFileCache()

private class AndroidChatAttachmentFileCache(
    private val cacheDirectory: File = File(requireApplicationContext().noBackupFilesDir, "kalog-secure-media-cache"),
) : ChatAttachmentFileCache {
    init {
        cacheDirectory.mkdirs()
        deleteLegacyPlaintextCache()
    }

    override suspend fun get(attachmentId: String): CachedChatAttachment? = null

    override suspend fun get(attachment: ChatAttachment): CachedChatAttachment? = withContext(Dispatchers.IO) {
        val key = attachment.decryptionKey ?: attachment.parts.firstNotNullOfOrNull(ChatAttachmentPart::key) ?: return@withContext null
        val safeId = attachment.id.safeFileName()
        if (attachment.parts.isEmpty()) {
            val file = encryptedFile(safeId)
            if (!file.isFile) return@withContext null
            return@withContext SecureAndroidAttachmentStore.register(
                attachmentId = attachment.id,
                fileName = attachment.name,
                mimeType = attachment.mimeType,
                plainSizeBytes = attachment.sizeBytes ?: encryptedPayloadPlainSize(file.length()),
                chunks = listOf(
                    SecureAndroidAttachmentChunkSpec(
                        file = file,
                        decryptionKey = key,
                        plainSizeBytes = attachment.sizeBytes ?: encryptedPayloadPlainSize(file.length()),
                    ),
                ),
            )
        }

        val chunkSpecs = attachment.parts
            .sortedBy(ChatAttachmentPart::index)
            .mapNotNull { part ->
                val file = encryptedPartFile(safeId, part.index)
                if (!file.isFile) return@mapNotNull null
                SecureAndroidAttachmentChunkSpec(
                    file = file,
                    decryptionKey = part.key ?: key,
                    plainSizeBytes = part.sizeBytes
                        ?: attachment.inferredPartPlainSize(part.index)
                        ?: encryptedPayloadPlainSize(file.length()),
                )
            }
        if (chunkSpecs.size != attachment.parts.size || chunkSpecs.isEmpty()) return@withContext null
        SecureAndroidAttachmentStore.register(
            attachmentId = attachment.id,
            fileName = attachment.name,
            mimeType = attachment.mimeType,
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
        PlainAndroidAttachmentMemoryStore.put(attachmentId, bytes)
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
        SecureAndroidAttachmentStore.register(
            attachmentId = attachmentId,
            fileName = fileName,
            mimeType = mimeType,
            plainSizeBytes = plainSizeBytes ?: encryptedPayloadPlainSize(encryptedBytes.size.toLong()),
            chunks = listOf(
                SecureAndroidAttachmentChunkSpec(
                    file = file,
                    decryptionKey = decryptionKey,
                    plainSizeBytes = plainSizeBytes ?: encryptedPayloadPlainSize(encryptedBytes.size.toLong()),
                ),
            ),
        )
    }

    override suspend fun readBytes(localUri: String): ByteArray? = withContext(Dispatchers.IO) {
        when {
            SecureAndroidAttachmentStore.isSecureUri(localUri) -> SecureAndroidAttachmentStore.readAll(localUri)
            PlainAndroidAttachmentMemoryStore.isMemoryUri(localUri) -> PlainAndroidAttachmentMemoryStore.readAll(localUri)
            else -> readExternalBytes(localUri)
        }
    }

    override suspend fun readBytes(localUri: String, offset: Long, length: Int): ByteArray? = withContext(Dispatchers.IO) {
        when {
            SecureAndroidAttachmentStore.isSecureUri(localUri) -> SecureAndroidAttachmentStore.read(localUri, offset, length)
            PlainAndroidAttachmentMemoryStore.isMemoryUri(localUri) -> PlainAndroidAttachmentMemoryStore.read(localUri, offset, length)
            else -> readExternalBytes(localUri, offset, length)
        }
    }

    override suspend fun putFromChunks(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        chunks: suspend (suspend (ByteArray) -> Unit) -> Unit,
    ): CachedChatAttachment = withContext(Dispatchers.IO) {
        val output = ByteArrayOutputStream()
        chunks { bytes -> output.write(bytes) }
        PlainAndroidAttachmentMemoryStore.put(attachmentId, output.toByteArray())
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
        val specs = mutableListOf<SecureAndroidAttachmentChunkSpec>()
        chunks { part ->
            val file = encryptedPartFile(safeId, part.index)
            file.writeBytes(part.encryptedBytes)
            file.setLastModified(System.currentTimeMillis())
            specs += SecureAndroidAttachmentChunkSpec(
                file = file,
                decryptionKey = part.decryptionKey,
                plainSizeBytes = part.plainSizeBytes ?: encryptedPayloadPlainSize(part.encryptedBytes.size.toLong()),
            )
        }
        SecureAndroidAttachmentStore.register(
            attachmentId = attachmentId,
            fileName = fileName,
            mimeType = mimeType,
            plainSizeBytes = plainSizeBytes ?: specs.sumOf { it.plainSizeBytes },
            chunks = specs,
        )
    }

    override suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        SecureAndroidAttachmentStore.clear()
        PlainAndroidAttachmentMemoryStore.clear()
        cacheDirectory.listFiles().orEmpty().count { file -> file.deleteRecursively() }
    }

    override suspend fun clearOlderThan(ageMillis: Long): Int = withContext(Dispatchers.IO) {
        val cutoffMillis = System.currentTimeMillis() - ageMillis
        val deleted = cacheDirectory.listFiles()
            .orEmpty()
            .filter { file -> file.lastModified() < cutoffMillis }
            .count { file -> file.deleteRecursively() }
        if (deleted > 0) SecureAndroidAttachmentStore.clear()
        deleted
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

internal object SecureAndroidAttachmentStore {
    private const val SCHEME = "kalog-secure-attachment"
    private val entries = ConcurrentHashMap<String, SecureAndroidAttachmentEntry>()

    fun register(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        plainSizeBytes: Long,
        chunks: List<SecureAndroidAttachmentChunkSpec>,
    ): CachedChatAttachment {
        val token = attachmentId.safeFileName()
        var offset = 0L
        val entryChunks = chunks.mapIndexed { index, chunk ->
            SecureAndroidAttachmentChunk(
                index = index,
                file = chunk.file,
                decryptionKey = chunk.decryptionKey,
                plainSizeBytes = chunk.plainSizeBytes,
                startOffset = offset,
            ).also {
                offset += chunk.plainSizeBytes
            }
        }
        val safeSize = plainSizeBytes.takeIf { it >= 0L } ?: offset
        entries[token] = SecureAndroidAttachmentEntry(
            token = token,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = safeSize,
            chunks = entryChunks,
        )
        return CachedChatAttachment(
            localUri = "$SCHEME://$token",
            sizeBytes = safeSize,
        )
    }

    fun isSecureUri(localUri: String?): Boolean = localUri?.startsWith("$SCHEME://") == true

    fun mimeType(localUri: String): String? = entry(localUri)?.mimeType

    fun sizeBytes(localUri: String): Long? = entry(localUri)?.sizeBytes

    fun openReader(localUri: String): SecureAndroidAttachmentReader? {
        return entry(localUri)?.let(::SecureAndroidAttachmentReader)
    }

    fun readAll(localUri: String): ByteArray? {
        val entry = entry(localUri) ?: return null
        val output = ByteArrayOutputStream(entry.sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val reader = SecureAndroidAttachmentReader(entry)
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
        val read = SecureAndroidAttachmentReader(entry).readAt(offset, bytes, 0, targetSize)
        return if (read <= 0) ByteArray(0) else bytes.copyOf(read)
    }

    fun clear() {
        entries.clear()
    }

    private fun entry(localUri: String): SecureAndroidAttachmentEntry? {
        val token = Uri.parse(localUri).host ?: return null
        return entries[token]
    }
}

internal class SecureAndroidAttachmentReader(
    private val entry: SecureAndroidAttachmentEntry,
) {
    private var cachedChunkIndex: Int = -1
    private var cachedChunkBytes: ByteArray = ByteArray(0)

    fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size == 0) return 0
        if (position < 0L || position >= entry.sizeBytes) return -1
        val chunk = entry.chunkFor(position) ?: return -1
        val chunkBytes = loadChunk(chunk)
        val startInChunk = (position - chunk.startOffset).toInt().coerceAtLeast(0)
        if (startInChunk >= chunkBytes.size) return -1
        val bytesToCopy = minOf(size, chunkBytes.size - startInChunk, (entry.sizeBytes - position).toInt())
        chunkBytes.copyInto(buffer, destinationOffset = offset, startIndex = startInChunk, endIndex = startInChunk + bytesToCopy)
        return bytesToCopy
    }

    private fun loadChunk(chunk: SecureAndroidAttachmentChunk): ByteArray {
        if (cachedChunkIndex == chunk.index) return cachedChunkBytes
        val encryptedBytes = chunk.file.readBytes()
        cachedChunkBytes = runBlocking {
            ChaCha20Poly1305CryptoManager.decrypt(encryptedBytes, chunk.decryptionKey)
        }
        cachedChunkIndex = chunk.index
        return cachedChunkBytes
    }
}

internal data class SecureAndroidAttachmentEntry(
    val token: String,
    val fileName: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val chunks: List<SecureAndroidAttachmentChunk>,
) {
    fun chunkFor(position: Long): SecureAndroidAttachmentChunk? {
        return chunks.lastOrNull { chunk -> position >= chunk.startOffset }
            ?.takeIf { chunk -> position < chunk.startOffset + chunk.plainSizeBytes }
    }
}

internal data class SecureAndroidAttachmentChunk(
    val index: Int,
    val file: File,
    val decryptionKey: String,
    val plainSizeBytes: Long,
    val startOffset: Long,
)

internal data class SecureAndroidAttachmentChunkSpec(
    val file: File,
    val decryptionKey: String,
    val plainSizeBytes: Long,
)

private object PlainAndroidAttachmentMemoryStore {
    private const val SCHEME = "kalog-memory-attachment"
    private val entries = ConcurrentHashMap<String, ByteArray>()

    fun put(attachmentId: String, bytes: ByteArray): CachedChatAttachment {
        val token = attachmentId.safeFileName()
        entries[token] = bytes
        return CachedChatAttachment(
            localUri = "$SCHEME://$token",
            sizeBytes = bytes.size.toLong(),
        )
    }

    fun isMemoryUri(localUri: String?): Boolean = localUri?.startsWith("$SCHEME://") == true

    fun readAll(localUri: String): ByteArray? = entries[Uri.parse(localUri).host]

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

private fun readExternalBytes(localUri: String): ByteArray? {
    val uri = Uri.parse(localUri)
    return when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> requireApplicationContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ContentResolver.SCHEME_FILE -> File(URI(localUri)).takeIf(File::isFile)?.readBytes()
        else -> runCatching { File(URI(localUri)).takeIf(File::isFile)?.readBytes() }.getOrNull()
    }
}

private fun readExternalBytes(localUri: String, offset: Long, length: Int): ByteArray? {
    val uri = Uri.parse(localUri)
    return when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> {
            requireApplicationContext().contentResolver.openInputStream(uri)?.use { input ->
                var remainingSkip = offset
                while (remainingSkip > 0L) {
                    val skipped = input.skip(remainingSkip)
                    if (skipped <= 0L) return ByteArray(0)
                    remainingSkip -= skipped
                }
                input.readNBytesCompat(length)
            }
        }
        ContentResolver.SCHEME_FILE -> readFileBytes(File(URI(localUri)), offset, length)
        else -> runCatching { readFileBytes(File(URI(localUri)), offset, length) }.getOrNull()
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

private fun java.io.InputStream.readNBytesCompat(length: Int): ByteArray {
    val buffer = ByteArray(length)
    var totalRead = 0
    while (totalRead < length) {
        val read = read(buffer, totalRead, length - totalRead)
        if (read < 0) break
        totalRead += read
    }
    return buffer.copyOf(totalRead)
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

private fun String.safeFileName(): String {
    return replace(Regex("[^A-Za-z0-9._-]"), "_")
}

private fun requireApplicationContext(): Context {
    return GlobalContext.get().get<Context>().applicationContext
}

private fun deleteLegacyPlaintextCache() {
    val legacyDirectory = File(System.getProperty("java.io.tmpdir"), "kalog-media-cache")
    if (legacyDirectory.isDirectory) {
        legacyDirectory.deleteRecursively()
    }
}

private const val CHACHA_NONCE_BYTES = 12L
private const val POLY1305_TAG_BYTES = 16L
private const val SECURE_ATTACHMENT_READ_BUFFER_BYTES = 64 * 1024
