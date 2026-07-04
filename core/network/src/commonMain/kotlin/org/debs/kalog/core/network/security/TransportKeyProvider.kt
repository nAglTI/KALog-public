package org.debs.kalog.core.network.security

import org.debs.kalog.core.crypto.PrivateKeyRef

interface TransportKeyProvider {
    suspend fun currentUserId(): String?

    suspend fun serverPublicKey(): String?

    suspend fun clientPrivateKey(): String?

    suspend fun clientPrivateKeyRef(): PrivateKeyRef? {
        return clientPrivateKey()?.let(PrivateKeyRef::Exported)
    }
}

class NoOpTransportKeyProvider : TransportKeyProvider {
    override suspend fun currentUserId(): String? = null

    override suspend fun serverPublicKey(): String? = null

    override suspend fun clientPrivateKey(): String? = null
}
