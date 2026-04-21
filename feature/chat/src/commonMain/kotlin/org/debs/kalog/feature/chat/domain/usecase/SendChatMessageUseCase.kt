package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class SendChatMessageUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, plainText: String): Boolean {
        val message = plainText.trim()
        if (message.isEmpty()) return false

        repository.sendMessage(chatId, message)
        return true
    }
}
