package org.debs.kalog.core.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.ChaCha20Poly1305
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
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

    suspend fun encryptToChunks(
        message: String,
        publicKey: String,
        chunkSizeBytes: Int = maxPayloadBytesPerChunk,
    ): List<String>

    suspend fun decryptFromChunks(chunks: List<String>, privateKey: String): String
}

data class GeneratedKeyPair(
    val publicKey: String,
    val privateKey: String,
)

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
    private const val ALGORITHM_LABEL = "ChaCha20-Poly1305"

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun generateKey(): GeneratedAttachmentKey = withContext(Dispatchers.Default) {
        val key = CryptographyProvider.Default
            .get(ChaCha20Poly1305)
            .keyGenerator()
            .generateKey()

        GeneratedAttachmentKey(
            key = Base64.encode(key.encodeToByteArrayBlocking(ChaCha20Poly1305.Key.Format.RAW)),
            sizeBits = KEY_SIZE_BITS,
            algorithmLabel = ALGORITHM_LABEL,
        )
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
