package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class OpenChatUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String) {
        repository.openChat(chatId)
    }
}
