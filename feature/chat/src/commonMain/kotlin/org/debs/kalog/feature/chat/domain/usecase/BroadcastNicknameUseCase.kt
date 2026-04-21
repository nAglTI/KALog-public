package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class BroadcastNicknameUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke() {
        repository.broadcastNicknameToAllChats()
    }
}
