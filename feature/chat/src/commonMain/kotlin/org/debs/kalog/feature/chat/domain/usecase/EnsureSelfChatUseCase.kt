package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class EnsureSelfChatUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(): String {
        return repository.ensureSelfChat()
    }
}
