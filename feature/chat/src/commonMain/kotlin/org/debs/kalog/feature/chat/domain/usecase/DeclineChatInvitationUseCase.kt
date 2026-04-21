package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class DeclineChatInvitationUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String) {
        repository.declineChatInvitation(chatId)
    }
}
