package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class LoadMoreChatMessagesUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String): Boolean {
        return repository.loadMoreMessages(chatId)
    }
}
