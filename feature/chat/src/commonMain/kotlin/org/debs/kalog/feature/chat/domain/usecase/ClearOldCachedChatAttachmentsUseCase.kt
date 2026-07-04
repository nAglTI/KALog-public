package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class ClearOldCachedChatAttachmentsUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(ageMillis: Long): Int {
        require(ageMillis >= 0L) { "Cache age must not be negative." }
        return repository.clearCachedAttachmentsOlderThan(ageMillis)
    }
}
