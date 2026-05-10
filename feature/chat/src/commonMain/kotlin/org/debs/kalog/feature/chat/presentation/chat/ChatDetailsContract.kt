package org.debs.kalog.feature.chat.presentation.chat

import org.debs.kalog.feature.chat.domain.model.AvatarAccent
import org.debs.kalog.feature.chat.domain.model.AvatarSpec
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatMessage
import org.debs.kalog.feature.chat.domain.model.ChatType
import org.debs.kalog.feature.chat.domain.model.InvitationStatus
import org.debs.kalog.feature.chat.domain.model.PreparedChatAttachment

data class ChatDetailsUiState(
    val title: String = "",
    val subtitle: String = "",
    val type: ChatType = ChatType.Unknown,
    val avatar: AvatarSpec = AvatarSpec(initials = "--", accent = AvatarAccent.Sky),
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val pendingAttachments: List<PreparedChatAttachment> = emptyList(),
    val attachmentUploadProgress: AttachmentUploadProgress? = null,
    val isSendingMessage: Boolean = false,
    val isPreparingAttachment: Boolean = false,
    val isInvitingUser: Boolean = false,
    val hasMoreMessages: Boolean = false,
    val isLoadingMoreMessages: Boolean = false,
    val invitationStatus: InvitationStatus = InvitationStatus.None,
    val isProcessingInvitation: Boolean = false,
) {
    val canSend: Boolean
        get() = (draft.isNotBlank() || pendingAttachments.isNotEmpty()) &&
            !isSendingMessage &&
            !isPreparingAttachment &&
            invitationStatus != InvitationStatus.Pending

    val canInviteUsers: Boolean
        get() = type == ChatType.Group && invitationStatus != InvitationStatus.Pending

    val isPendingInvitation: Boolean
        get() = invitationStatus == InvitationStatus.Pending
}

data class AttachmentUploadProgress(
    val fileName: String,
    val bytesSent: Long,
    val totalBytes: Long,
)

sealed interface ChatDetailsEvent {
    data class DraftChanged(val value: String) : ChatDetailsEvent

    data object SendClicked : ChatDetailsEvent

    data object AttachFileClicked : ChatDetailsEvent

    data object PickImageClicked : ChatDetailsEvent

    data object RecordVoiceClicked : ChatDetailsEvent

    data class AttachmentDraftSelected(val attachment: ChatAttachment) : ChatDetailsEvent

    data class AttachmentDraftsSelected(val attachments: List<ChatAttachment>) : ChatDetailsEvent

    data class RemoveAttachmentDraft(val attachmentId: String) : ChatDetailsEvent

    data object LoadMoreMessagesClicked : ChatDetailsEvent

    data class InviteUserConfirmed(val userId: String) : ChatDetailsEvent

    data object AcceptInvitationClicked : ChatDetailsEvent

    data object DeclineInvitationClicked : ChatDetailsEvent
}

sealed interface ChatDetailsEffect {
    data object InviteUserCompleted : ChatDetailsEffect

    data object InvitationDeclined : ChatDetailsEffect

    data class ShowError(val message: String) : ChatDetailsEffect

    data class ShowMessage(val message: String) : ChatDetailsEffect
}
