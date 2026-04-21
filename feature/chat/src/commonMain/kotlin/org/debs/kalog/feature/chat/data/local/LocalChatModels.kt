package org.debs.kalog.feature.chat.data.local

import org.debs.kalog.feature.chat.domain.model.AvatarAccent
import org.debs.kalog.feature.chat.domain.model.DeliveryStatus

internal data class LocalChatThread(
    val id: String,
    val title: String,
    val subtitle: String,
    val typeRaw: String,
    val avatarInitials: String,
    val avatarAccent: AvatarAccent,
    val unreadCount: Int,
    val messages: List<LocalChatMessage>,
    val invitationStatus: String = "none",
) {
    val lastMessagePosition: Long
        get() = messages.maxOfOrNull(LocalChatMessage::position) ?: 0L
}

internal data class LocalChatMessage(
    val id: String,
    val chatId: String,
    val sender: String?,
    val encryptedChunks: List<String>,
    val timestamp: String,
    val isService: Boolean,
    val isMine: Boolean?,
    val deliveryStatus: DeliveryStatus?,
    val position: Long,
    val messageType: String,
    val fromUserId: String?,
    val toUserId: String?,
)
