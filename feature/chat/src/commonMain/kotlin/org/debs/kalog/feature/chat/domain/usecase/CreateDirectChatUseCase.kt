package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class CreateDirectChatUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(targetUserId: String): String {
        return repository.createDirectChat(targetUserId)
    }
}
