package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class CreateGroupChatUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(publicKey: String? = null): String {
        return repository.createGroupChat(publicKey)
    }
}
