package org.debs.kalog.feature.chat.data.crypto

import kotlin.coroutines.cancellation.CancellationException
import org.debs.kalog.core.crypto.EncryptionService

class ChatMessageCipher(
    private val encryptionService: EncryptionService,
    private val keyStore: ChatKeyStore,
) {
    suspend fun encryptOutgoing(chatId: String, message: String): List<EncryptedRecipientPayload> {
        return keyStore.participantsFor(chatId).map { participant ->
            if (participant.publicKey.isBlank()) {
                throw ChatEncryptionException("Public key is missing for recipient ${participant.userId}.")
            }

            try {
                EncryptedRecipientPayload(
                    recipientId = participant.userId,
                    chunks = encryptionService.encryptToChunks(message, participant.publicKey),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                throw ChatEncryptionException("Failed to encrypt message for recipient ${participant.userId}.", error)
            }
        }
    }

    suspend fun decryptIncoming(chatId: String, chunks: List<String>, isEncrypted: Boolean): String {
        if (!isEncrypted) return chunks.joinToString(separator = "")

        val privateKey = keyStore.chatPrivateKey(chatId) ?: return chunks.joinToString(separator = "")
        return runCatching { encryptionService.decryptFromChunks(chunks, privateKey) }
            .getOrElse { chunks.joinToString(separator = "") }
    }
}

class ChatEncryptionException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
