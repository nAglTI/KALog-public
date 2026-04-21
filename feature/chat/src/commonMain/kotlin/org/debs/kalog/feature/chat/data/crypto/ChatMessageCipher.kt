package org.debs.kalog.feature.chat.data.crypto

import org.debs.kalog.core.crypto.EncryptionService

class ChatMessageCipher(
    private val encryptionService: EncryptionService,
    private val keyStore: ChatKeyStore,
) {
    suspend fun encryptOutgoing(chatId: String, message: String): List<EncryptedRecipientPayload> {
        return keyStore.participantsFor(chatId).mapNotNull { participant ->
            runCatching {
                EncryptedRecipientPayload(
                    recipientId = participant.userId,
                    chunks = encryptionService.encryptToChunks(message, participant.publicKey),
                )
            }.getOrNull()
        }
    }

    suspend fun decryptIncoming(chatId: String, chunks: List<String>, isEncrypted: Boolean): String {
        if (!isEncrypted) return chunks.joinToString(separator = "")

        val privateKey = keyStore.chatPrivateKey(chatId) ?: return chunks.joinToString(separator = "")
        return runCatching { encryptionService.decryptFromChunks(chunks, privateKey) }
            .getOrElse { chunks.joinToString(separator = "") }
    }
}
