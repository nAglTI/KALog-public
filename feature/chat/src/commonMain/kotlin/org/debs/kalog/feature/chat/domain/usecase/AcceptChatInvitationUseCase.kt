package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class AcceptChatInvitationUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String) {
        repository.acceptChatInvitation(chatId)
    }
}
