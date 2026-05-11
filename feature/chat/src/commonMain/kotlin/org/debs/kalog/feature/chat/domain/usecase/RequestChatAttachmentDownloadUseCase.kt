package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class RequestChatAttachmentDownloadUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, attachmentId: String) {
        if (chatId.isBlank() || attachmentId.isBlank()) return
        repository.requestAttachmentDownload(chatId, attachmentId)
    }
}
