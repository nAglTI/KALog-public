package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class StartChatSessionUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke() {
        repository.startSession()
    }
}
