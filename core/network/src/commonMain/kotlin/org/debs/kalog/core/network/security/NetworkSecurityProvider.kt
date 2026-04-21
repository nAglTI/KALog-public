package org.debs.kalog.core.network.security

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
        // FIXME: Do not silently fall back to plaintext in production when transport keys are missing.
        val publicKey = transportKeyProvider.serverPublicKey() ?: return SecurePayload(body = body, isEncrypted = false)
        return runCatching { encryptionService.encryptToChunks(body, publicKey) }
            .fold(
                onSuccess = { SecurePayload(chunks = it, isEncrypted = true) },
                // FIXME: Encryption failures should fail closed instead of sending the original request body.
                onFailure = { SecurePayload(body = body, isEncrypted = false) },
            )
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
