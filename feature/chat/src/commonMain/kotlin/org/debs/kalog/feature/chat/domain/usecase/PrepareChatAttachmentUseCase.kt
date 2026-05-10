package org.debs.kalog.feature.chat.domain.usecase

import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.PreparedChatAttachment
import org.debs.kalog.feature.chat.domain.repository.ChatRepository

class PrepareChatAttachmentUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, attachment: ChatAttachment): PreparedChatAttachment {
        require(attachment.id.isNotBlank()) { "Attachment id must not be blank." }
        require(attachment.name.isNotBlank()) { "Attachment name must not be blank." }

        return repository.prepareAttachment(chatId, attachment)
    }
}
