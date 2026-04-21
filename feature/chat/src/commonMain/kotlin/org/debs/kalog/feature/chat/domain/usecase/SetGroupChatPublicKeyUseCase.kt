package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class SetGroupChatPublicKeyUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, publicKey: String) {
        repository.setGroupChatPublicKey(chatId, publicKey)
    }
}
