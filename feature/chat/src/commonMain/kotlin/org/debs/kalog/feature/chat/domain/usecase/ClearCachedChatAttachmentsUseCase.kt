package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class ClearCachedChatAttachmentsUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(): Int {
        return repository.clearCachedAttachments()
    }
}
