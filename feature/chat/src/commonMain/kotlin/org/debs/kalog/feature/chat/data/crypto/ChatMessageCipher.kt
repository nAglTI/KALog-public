package org.debs.kalog.feature.chat.data.crypto

import kotlin.coroutines.cancellation.CancellationException
import org.debs.kalog.core.crypto.EncryptionService
import org.debs.kalog.feature.chat.localization.chatLocalized

class ChatMessageCipher(
    private val encryptionService: EncryptionService,
    private val keyStore: ChatKeyStore,
) {
    suspend fun encryptOutgoing(chatId: String, message: String): List<EncryptedRecipientPayload> {
        return keyStore.participantsFor(chatId).map { participant ->
            if (participant.publicKey.isBlank()) {
                throw ChatEncryptionException(
                    chatLocalized(
                        en = "Public key is missing for recipient ${participant.userId}.",
                        ru = "Не найден публичный ключ получателя ${participant.userId}.",
                    ),
                )
            }

            try {
                EncryptedRecipientPayload(
                    recipientId = participant.userId,
                    chunks = encryptionService.encryptToChunks(message, participant.publicKey),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                throw ChatEncryptionException(
                    chatLocalized(
                        en = "Failed to encrypt message for recipient ${participant.userId}.",
                        ru = "Не удалось зашифровать сообщение для получателя ${participant.userId}.",
                    ),
                    error,
                )
            }
        }
    }

    suspend fun decryptIncoming(chatId: String, chunks: List<String>, isEncrypted: Boolean): ChatDecryptionResult {
        if (!isEncrypted) return ChatDecryptionResult.Decrypted(chunks.joinToString(separator = ""))

        val privateKeyRef = keyStore.chatPrivateKeyRef(chatId) ?: return ChatDecryptionResult.Undecryptable
        if (chunks.isEmpty()) return ChatDecryptionResult.Decrypted("")

        return try {
            ChatDecryptionResult.Decrypted(encryptionService.decryptFromChunks(chunks, privateKeyRef))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            ChatDecryptionResult.Undecryptable
        }
    }
}

sealed interface ChatDecryptionResult {
    data class Decrypted(val body: String) : ChatDecryptionResult

    data object Undecryptable : ChatDecryptionResult
}

class ChatEncryptionException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
