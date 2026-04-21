package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class ObserveChatsUseCase(
    private val repository: ChatRepository,
) {
    operator fun invoke() = repository.observeChats()
}
