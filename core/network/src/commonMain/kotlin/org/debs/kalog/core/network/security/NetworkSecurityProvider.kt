package org.debs.kalog.core.network.security

import kotlin.coroutines.cancellation.CancellationException
import org.debs.kalog.core.crypto.EncryptionService
import org.debs.kalog.core.network.model.SecurePayload

interface NetworkSecurityProvider {
    suspend fun protectRequest(body: String): SecurePayload

    suspend fun unprotectResponse(payload: SecurePayload): String
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

    override suspend fun unprotectResponse(payload: SecurePayload): String {
        if (!payload.isEncrypted) return payload.body.orEmpty()

        // FIXME: Treat missing private keys as a hard failure for protected responses in production builds.
        val privateKey = transportKeyProvider.clientPrivateKey() ?: return payload.body.orEmpty()
        return runCatching { encryptionService.decryptFromChunks(payload.chunks, privateKey) }
            // FIXME: Decryption failures should not silently fall back to the raw response body.
            .getOrElse { payload.body.orEmpty() }
    }
}

class TransportEncryptionException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
