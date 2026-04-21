package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class ObserveChatDetailsUseCase(
    private val repository: ChatRepository,
) {
    operator fun invoke(chatId: String) = repository.observeChat(chatId)
}
