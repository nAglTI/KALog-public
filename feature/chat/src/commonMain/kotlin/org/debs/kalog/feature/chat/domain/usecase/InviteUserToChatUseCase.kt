package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class InviteUserToChatUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, userId: String) {
        repository.inviteUserToChat(chatId, userId)
    }
}
