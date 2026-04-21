package org.debs.kalog.core.network.security

interface TransportKeyProvider {
    suspend fun currentUserId(): String?

    suspend fun serverPublicKey(): String?

    suspend fun clientPrivateKey(): String?
}

class NoOpTransportKeyProvider : TransportKeyProvider {
    override suspend fun currentUserId(): String? = null

    override suspend fun serverPublicKey(): String? = null

    override suspend fun clientPrivateKey(): String? = null
}
