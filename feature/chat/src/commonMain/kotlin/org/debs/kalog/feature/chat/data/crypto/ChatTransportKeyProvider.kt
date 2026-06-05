package org.debs.kalog.feature.chat.data.crypto

import org.debs.kalog.core.crypto.PrivateKeyRef
import org.debs.kalog.core.network.security.TransportKeyProvider

class ChatTransportKeyProvider(
    private val chatKeyStore: ChatKeyStore,
) : TransportKeyProvider {
    override suspend fun currentUserId(): String? = chatKeyStore.currentUserId()

    override suspend fun serverPublicKey(): String? = chatKeyStore.serverPublicKey()

    override suspend fun clientPrivateKey(): String? = chatKeyStore.currentUserPrivateKey()

    override suspend fun clientPrivateKeyRef(): PrivateKeyRef? = chatKeyStore.currentUserPrivateKeyRef()
}
