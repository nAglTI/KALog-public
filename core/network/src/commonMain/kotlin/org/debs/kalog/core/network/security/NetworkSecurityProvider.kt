package org.debs.kalog.core.network.security

import kotlin.coroutines.cancellation.CancellationException
import org.debs.kalog.core.crypto.EncryptionService
import org.debs.kalog.core.network.model.SecurePayload

interface NetworkSecurityProvider {
    suspend fun protectRequest(body: String): SecurePayload

    suspend fun unprotectResponse(payload: SecurePayload): NetworkDecryptionResult
}

sealed interface NetworkDecryptionResult {
    data class Decrypted(val body: String) : NetworkDecryptionResult

    data object Undecryptable : NetworkDecryptionResult
}

class CipherNetworkSecurityProvider(
    private val encryptionService: EncryptionService,
    private val transportKeyProvider: TransportKeyProvider,
) : NetworkSecurityProvider {
    override suspend fun protectRequest(body: String): SecurePayload {
        val publicKey = transportKeyProvider.serverPublicKey()
            ?: throw TransportEncryptionException("Server public key is not initialized.")

        val encryptedChunks = try {
            encryptionService.encryptToChunks(body, publicKey)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw TransportEncryptionException("Failed to encrypt request body.", error)
        }

        if (encryptedChunks.isEmpty()) {
            throw TransportEncryptionException("Encrypted request body is empty.")
        }

        return SecurePayload(chunks = encryptedChunks, isEncrypted = true)
    }

    override suspend fun unprotectResponse(payload: SecurePayload): NetworkDecryptionResult {
        if (!payload.isEncrypted) {
            return NetworkDecryptionResult.Decrypted(payload.body.orEmpty())
        }

        val privateKeyRef = transportKeyProvider.clientPrivateKeyRef()
            ?: return NetworkDecryptionResult.Undecryptable
        if (payload.chunks.isEmpty()) {
            return NetworkDecryptionResult.Decrypted("")
        }

        return try {
            NetworkDecryptionResult.Decrypted(encryptionService.decryptFromChunks(payload.chunks, privateKeyRef))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            NetworkDecryptionResult.Undecryptable
        }
    }
}

class TransportEncryptionException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
