package org.debs.kalog.core.network.client

import io.ktor.client.HttpClient
import org.debs.kalog.core.network.model.SecurePayload
import org.debs.kalog.core.network.model.SecureRequestEnvelope
import org.debs.kalog.core.network.security.NetworkSecurityProvider
import org.debs.kalog.core.network.security.NetworkDecryptionResult
import org.debs.kalog.core.network.security.TransportKeyProvider

interface SecureApiClient {
    val httpClient: HttpClient

    suspend fun prepareRequestBody(body: String): SecurePayload

    suspend fun prepareEncryptedRequest(body: String): SecureRequestEnvelope

    suspend fun unwrapResponseBody(payload: SecurePayload): NetworkDecryptionResult
}

class KtorSecureApiClient(
    override val httpClient: HttpClient,
    private val networkSecurityProvider: NetworkSecurityProvider,
    private val transportKeyProvider: TransportKeyProvider,
) : SecureApiClient {
    override suspend fun prepareRequestBody(body: String): SecurePayload {
        return networkSecurityProvider.protectRequest(body)
    }

    override suspend fun prepareEncryptedRequest(body: String): SecureRequestEnvelope {
        val payload = prepareRequestBody(body)
        check(payload.isEncrypted && payload.chunks.isNotEmpty()) {
            "Encrypted transport payload is not available."
        }

        val currentUserId = checkNotNull(transportKeyProvider.currentUserId()) {
            "Current user id is not initialized."
        }

        return SecureRequestEnvelope(
            id = currentUserId,
            data = payload.chunks,
        )
    }

    override suspend fun unwrapResponseBody(payload: SecurePayload): NetworkDecryptionResult {
        return networkSecurityProvider.unprotectResponse(payload)
    }
}
