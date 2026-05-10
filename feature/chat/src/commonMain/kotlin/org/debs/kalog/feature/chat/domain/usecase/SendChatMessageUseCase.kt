package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.model.PreparedChatAttachment
import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class SendChatMessageUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(
        chatId: String,
        plainText: String,
        attachments: List<PreparedChatAttachment> = emptyList(),
    ): Boolean {
        val message = plainText.trim()
        if (message.isEmpty() && attachments.isEmpty()) return false

        repository.sendMessage(chatId, message, attachments)
        return true
    }
}
