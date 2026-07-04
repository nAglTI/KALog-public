package org.debs.kalog.core.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.ChaCha20Poly1305
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface EncryptionService {
    val algorithmLabel: String
    val maxPayloadBytesPerChunk: Int

    suspend fun generateKeyPair(): GeneratedKeyPair

    suspend fun generateAttachmentKey(): GeneratedAttachmentKey

    suspend fun encrypt(message: String, publicKey: String): String

    suspend fun decrypt(message: String, privateKey: String): String

    suspend fun decrypt(message: String, privateKeyRef: PrivateKeyRef): String {
        return decrypt(message, privateKeyRef.requireExportedValue())
    }

    suspend fun encryptAttachment(bytes: ByteArray, key: String): ByteArray

    suspend fun decryptAttachment(bytes: ByteArray, key: String): ByteArray

    suspend fun encryptToChunks(
        message: String,
        publicKey: String,
        chunkSizeBytes: Int = maxPayloadBytesPerChunk,
    ): List<String>

    suspend fun decryptFromChunks(chunks: List<String>, privateKey: String): String

    suspend fun decryptFromChunks(chunks: List<String>, privateKeyRef: PrivateKeyRef): String {
        return decryptFromChunks(chunks, privateKeyRef.requireExportedValue())
    }

    suspend fun exportPrivateKey(privateKeyRef: PrivateKeyRef): PrivateKeyRef.Exported {
        return PrivateKeyRef.Exported(privateKeyRef.requireExportedValue())
    }

    suspend fun importPrivateKey(
        publicKey: String,
        privateKey: PrivateKeyRef.Exported,
    ): PrivateKeyRef = privateKey

    suspend fun deletePrivateKey(privateKeyRef: PrivateKeyRef) = Unit
}

expect fun createEncryptionService(): EncryptionService

data class GeneratedKeyPair(
    val publicKey: String,
    val privateKeyRef: PrivateKeyRef,
) {
    constructor(publicKey: String, privateKey: String) : this(
        publicKey = publicKey,
        privateKeyRef = PrivateKeyRef.Exported(privateKey),
    )

    val privateKey: String
        get() = privateKeyRef.requireExportedValue()
}

sealed interface PrivateKeyRef {
    fun serialize(): String

    data class Exported(
        val value: String,
    ) : PrivateKeyRef {
        override fun serialize(): String = "$EXPORTED_PREFIX$value"
    }

    data class PlatformAlias(
        val provider: String,
        val alias: String,
    ) : PrivateKeyRef {
        override fun serialize(): String = "$PLATFORM_PREFIX$provider:$alias"
    }

    companion object {
        fun deserialize(value: String): PrivateKeyRef {
            return when {
                value.startsWith(EXPORTED_PREFIX) -> Exported(value.removePrefix(EXPORTED_PREFIX))
                value.startsWith(PLATFORM_PREFIX) -> {
                    val body = value.removePrefix(PLATFORM_PREFIX)
                    val separatorIndex = body.indexOf(':')
                    require(separatorIndex > 0 && separatorIndex < body.lastIndex) {
                        "Invalid platform private key reference."
                    }
                    PlatformAlias(
                        provider = body.substring(0, separatorIndex),
                        alias = body.substring(separatorIndex + 1),
                    )
                }
                else -> error("Unsupported private key reference format.")
            }
        }
    }
}

fun PrivateKeyRef.exportedValueOrNull(): String? {
    return (this as? PrivateKeyRef.Exported)?.value
}

fun PrivateKeyRef.requireExportedValue(): String {
    return exportedValueOrNull()
        ?: error("This encryption service cannot use platform private key references.")
}

private const val EXPORTED_PREFIX = "exported:"
private const val PLATFORM_PREFIX = "platform:"

data class GeneratedAttachmentKey(
    val key: String,
    val sizeBits: Int,
    val algorithmLabel: String,
)

class RsaOaepEncryptionService : EncryptionService {
    override val algorithmLabel: String = "RSA-OAEP / SHA-256"
    override val maxPayloadBytesPerChunk: Int = 190

    override suspend fun generateKeyPair(): GeneratedKeyPair {
        return RsaOaepCryptoManager.generateKeyPair()
    }

    override suspend fun generateAttachmentKey(): GeneratedAttachmentKey {
        return ChaCha20Poly1305CryptoManager.generateKey()
    }

    override suspend fun encrypt(message: String, publicKey: String): String {
        return RsaOaepCryptoManager.encrypt(message, publicKey)
    }

    override suspend fun decrypt(message: String, privateKey: String): String {
        return RsaOaepCryptoManager.decrypt(message, privateKey)
    }

    override suspend fun encryptAttachment(bytes: ByteArray, key: String): ByteArray {
        return ChaCha20Poly1305CryptoManager.encrypt(bytes, key)
    }

    override suspend fun decryptAttachment(bytes: ByteArray, key: String): ByteArray {
        return ChaCha20Poly1305CryptoManager.decrypt(bytes, key)
    }

    override suspend fun encryptToChunks(
        message: String,
        publicKey: String,
        chunkSizeBytes: Int,
    ): List<String> {
        return RsaOaepCryptoManager.encryptToChunks(message, publicKey, chunkSizeBytes)
    }

    override suspend fun decryptFromChunks(chunks: List<String>, privateKey: String): String {
        return RsaOaepCryptoManager.decryptFromChunks(chunks, privateKey)
    }
}

object ChaCha20Poly1305CryptoManager {
    private const val KEY_SIZE_BITS = 256
    private const val NONCE_SIZE_BYTES = 12
    private const val ALGORITHM_LABEL = "ChaCha20-Poly1305"

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun generateKey(): GeneratedAttachmentKey = withContext(Dispatchers.Default) {
        GeneratedAttachmentKey(
            key = Base64.encode(CryptographyRandom.nextBytes(KEY_SIZE_BITS / 8)),
            sizeBits = KEY_SIZE_BITS,
            algorithmLabel = ALGORITHM_LABEL,
        )
    }

    @OptIn(DelicateCryptographyApi::class, ExperimentalEncodingApi::class)
    suspend fun encrypt(bytes: ByteArray, serializedKey: String): ByteArray = withContext(Dispatchers.Default) {
        val nonce = CryptographyRandom.nextBytes(NONCE_SIZE_BYTES)
        val encryptedBytes = decodeKey(serializedKey)
            .cipher()
            .encryptWithIv(nonce, bytes, byteArrayOf())

        nonce + encryptedBytes
    }

    @OptIn(DelicateCryptographyApi::class, ExperimentalEncodingApi::class)
    suspend fun decrypt(bytes: ByteArray, serializedKey: String): ByteArray = withContext(Dispatchers.Default) {
        require(bytes.size > NONCE_SIZE_BYTES) { "Encrypted attachment payload is missing nonce or ciphertext." }

        val nonce = bytes.copyOfRange(0, NONCE_SIZE_BYTES)
        val encryptedBytes = bytes.copyOfRange(NONCE_SIZE_BYTES, bytes.size)
        decodeKey(serializedKey)
            .cipher()
            .decryptWithIv(nonce, encryptedBytes, byteArrayOf())
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun decodeKey(serializedKey: String): ChaCha20Poly1305.Key = withContext(Dispatchers.Default) {
        val rawKey = Base64.decode(serializedKey)
        require(rawKey.size == KEY_SIZE_BITS / 8) {
            "ChaCha20-Poly1305 attachment key must be ${KEY_SIZE_BITS / 8} bytes, got ${rawKey.size}."
        }
        CryptographyProvider.Default
            .get(ChaCha20Poly1305)
            .keyDecoder()
            .decodeFromByteArray(ChaCha20Poly1305.Key.Format.RAW, rawKey)
    }
}

object RsaOaepCryptoManager {
    private const val KEY_SIZE = 2048

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun generateKeyPair(): GeneratedKeyPair = withContext(Dispatchers.Default) {
        val keyPair = CryptographyProvider.Default
            .get(RSA.OAEP)
            .keyPairGenerator(keySize = KEY_SIZE.bits, digest = SHA256)
            .generateKey()

        GeneratedKeyPair(
            publicKey = Base64.encode(keyPair.publicKey.encodeToByteArrayBlocking(RSA.PublicKey.Format.DER.PKCS1)),
            privateKey = Base64.encode(keyPair.privateKey.encodeToByteArrayBlocking(RSA.PrivateKey.Format.DER.PKCS1)),
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun encrypt(message: String, publicKey: String): String = withContext(Dispatchers.Default) {
        encryptChunk(message.encodeToByteArray(), publicKey)
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun decrypt(message: String, privateKey: String): String = withContext(Dispatchers.Default) {
        decryptChunk(message, privateKey).decodeToString()
    }

    suspend fun encryptToChunks(
        message: String,
        publicKey: String,
        chunkSizeBytes: Int,
    ): List<String> = withContext(Dispatchers.Default) {
        require(chunkSizeBytes > 0) { "Chunk size must be positive." }

        message.encodeToByteArray()
            .asList()
            .chunked(chunkSizeBytes)
            .map { it.toByteArray() }
            .map { chunk -> encryptChunk(chunk, publicKey) }
    }

    suspend fun decryptFromChunks(
        chunks: List<String>,
        privateKey: String,
    ): String = withContext(Dispatchers.Default) {
        chunks
            .flatMap { chunk -> decryptChunk(chunk, privateKey).asIterable() }
            .toByteArray()
            .decodeToString()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun decodePublicKey(key: String): RSA.OAEP.PublicKey = withContext(Dispatchers.Default) {
        CryptographyProvider.Default
            .get(RSA.OAEP)
            .publicKeyDecoder(SHA256)
            .decodeFromByteArray(RSA.PublicKey.Format.DER.PKCS1, Base64.decode(key))
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun decodePrivateKey(key: String): RSA.OAEP.PrivateKey = withContext(Dispatchers.Default) {
        CryptographyProvider.Default
            .get(RSA.OAEP)
            .privateKeyDecoder(SHA256)
            .decodeFromByteArray(RSA.PrivateKey.Format.DER.PKCS1, Base64.decode(key))
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun encryptChunk(message: ByteArray, publicKey: String): String = withContext(Dispatchers.Default) {
        Base64.encode(decodePublicKey(publicKey).encryptor().encrypt(message))
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun decryptChunk(message: String, privateKey: String): ByteArray = withContext(Dispatchers.Default) {
        decodePrivateKey(privateKey).decryptor().decrypt(Base64.decode(message))
    }
}
