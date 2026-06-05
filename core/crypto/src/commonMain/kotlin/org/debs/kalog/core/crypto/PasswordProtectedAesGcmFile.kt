package org.debs.kalog.core.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PasswordProtectedAesGcmFile {
    suspend fun encrypt(
        plaintext: ByteArray,
        password: String,
    ): ByteArray = withContext(Dispatchers.Default) {
        require(password.isNotBlank()) { "Backup password is required." }

        val salt = CryptographyRandom.nextBytes(SALT_SIZE_BYTES)
        val iv = CryptographyRandom.nextBytes(IV_SIZE_BYTES)
        val header = buildHeader(salt = salt, iv = iv)
        val ciphertext = encryptPayload(
            plaintext = plaintext,
            password = password,
            salt = salt,
            iv = iv,
            associatedData = header,
        )

        header + ciphertext
    }

    suspend fun decrypt(
        bytes: ByteArray,
        password: String,
    ): ByteArray = withContext(Dispatchers.Default) {
        require(password.isNotBlank()) { "Backup password is required." }
        require(bytes.size > HEADER_SIZE_BYTES) { "Backup file is too small." }
        require(bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Unsupported backup file." }
        require(bytes[MAGIC.size].toInt() == FORMAT_VERSION) { "Unsupported backup version." }

        val iterations = bytes.readIntLe(offset = MAGIC.size + 1)
        require(iterations == PBKDF2_ITERATIONS) { "Unsupported backup key derivation settings." }

        val salt = bytes.copyOfRange(SALT_OFFSET, SALT_OFFSET + SALT_SIZE_BYTES)
        val iv = bytes.copyOfRange(IV_OFFSET, IV_OFFSET + IV_SIZE_BYTES)
        val header = bytes.copyOfRange(0, HEADER_SIZE_BYTES)
        val ciphertext = bytes.copyOfRange(HEADER_SIZE_BYTES, bytes.size)

        decryptPayload(
            ciphertext = ciphertext,
            password = password,
            salt = salt,
            iv = iv,
            associatedData = header,
        )
    }

    @OptIn(DelicateCryptographyApi::class)
    private suspend fun encryptPayload(
        plaintext: ByteArray,
        password: String,
        salt: ByteArray,
        iv: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        val key = aesKey(password = password, salt = salt)
        return key.cipher().encryptWithIv(iv, plaintext, associatedData)
    }

    @OptIn(DelicateCryptographyApi::class)
    private suspend fun decryptPayload(
        ciphertext: ByteArray,
        password: String,
        salt: ByteArray,
        iv: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        val key = aesKey(password = password, salt = salt)
        return key.cipher().decryptWithIv(iv, ciphertext, associatedData)
    }

    private suspend fun aesKey(
        password: String,
        salt: ByteArray,
    ): AES.GCM.Key {
        val rawKey = CryptographyProvider.Default
            .get(PBKDF2)
            .secretDerivation(
                digest = SHA256,
                iterations = PBKDF2_ITERATIONS,
                outputSize = AES_KEY_SIZE_BYTES.bytes,
                salt = salt,
            )
            .deriveSecretToByteArray(password.encodeToByteArray())

        return CryptographyProvider.Default
            .get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, rawKey)
    }

    private fun buildHeader(
        salt: ByteArray,
        iv: ByteArray,
    ): ByteArray {
        require(salt.size == SALT_SIZE_BYTES) { "Invalid backup salt size." }
        require(iv.size == IV_SIZE_BYTES) { "Invalid backup IV size." }
        return buildList<Byte> {
            addAll(MAGIC.asList())
            add(FORMAT_VERSION.toByte())
            addIntLe(PBKDF2_ITERATIONS)
            addAll(salt.asList())
            addAll(iv.asList())
        }.toByteArray()
    }

    private fun MutableList<Byte>.addIntLe(value: Int) {
        add((value and 0xFF).toByte())
        add(((value ushr 8) and 0xFF).toByte())
        add(((value ushr 16) and 0xFF).toByte())
        add(((value ushr 24) and 0xFF).toByte())
    }

    private fun ByteArray.readIntLe(offset: Int): Int {
        require(offset >= 0 && offset + Int.SIZE_BYTES <= size) { "Invalid backup integer offset." }
        return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
    }

    private val MAGIC = byteArrayOf('M'.code.toByte(), 'C'.code.toByte(), 'A'.code.toByte(), 'B'.code.toByte())
    private const val FORMAT_VERSION = 1
    private const val PBKDF2_ITERATIONS = 210_000
    private const val AES_KEY_SIZE_BYTES = 32
    private const val SALT_SIZE_BYTES = 16
    private const val IV_SIZE_BYTES = 12
    private const val SALT_OFFSET = 4 + 1 + 4
    private const val IV_OFFSET = SALT_OFFSET + SALT_SIZE_BYTES
    private const val HEADER_SIZE_BYTES = IV_OFFSET + IV_SIZE_BYTES
}
