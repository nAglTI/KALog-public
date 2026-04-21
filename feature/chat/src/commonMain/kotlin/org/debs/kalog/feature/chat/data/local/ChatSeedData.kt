package org.debs.kalog.feature.chat.data.local

import org.debs.kalog.feature.chat.domain.model.AvatarAccent
import org.debs.kalog.feature.chat.domain.model.DeliveryStatus

internal data class SeedChatThread(
    val id: String,
    val title: String,
    val subtitle: String,
    val avatarInitials: String,
    val avatarAccent: AvatarAccent,
    val unreadCount: Int,
    val participants: List<SeedChatParticipant>,
    val messages: List<SeedChatMessage>,
)

internal data class SeedChatParticipant(
    val userId: String,
    val displayName: String,
    val isCurrentUser: Boolean = false,
)

internal sealed interface SeedChatMessage {
    data class User(
        val id: String,
        val sender: String,
        val body: String,
        val timestamp: String,
        val isMine: Boolean,
        val deliveryStatus: DeliveryStatus? = null,
    ) : SeedChatMessage

    data class Service(
        val id: String,
        val body: String,
        val timestamp: String,
    ) : SeedChatMessage
}

internal fun defaultChatSeed(): List<SeedChatThread> {
    return listOf(
        SeedChatThread(
            id = "elena",
            title = "Elena Morozova",
            subtitle = "online",
            avatarInitials = "EM",
            avatarAccent = AvatarAccent.Sky,
            unreadCount = 2,
            participants = listOf(
                SeedChatParticipant(userId = "self", displayName = "You", isCurrentUser = true),
                SeedChatParticipant(userId = "elena", displayName = "Elena"),
            ),
            messages = listOf(
                SeedChatMessage.Service(
                    id = "elena-service-1",
                    body = "Secure session established. Fingerprints verified.",
                    timestamp = "09:12",
                ),
                SeedChatMessage.User(
                    id = "elena-user-1",
                    sender = "Elena",
                    body = "The laptop key matches the one from the printed backup.",
                    timestamp = "09:14",
                    isMine = false,
                ),
                SeedChatMessage.User(
                    id = "elena-user-2",
                    sender = "You",
                    body = "Great. I will move the release notes into the protected thread.",
                    timestamp = "09:16",
                    isMine = true,
                    deliveryStatus = DeliveryStatus.Read,
                ),
                SeedChatMessage.User(
                    id = "elena-user-3",
                    sender = "Elena",
                    body = "Send me the summary when you are ready.",
                    timestamp = "09:18",
                    isMine = false,
                ),
            ),
        ),
        SeedChatThread(
            id = "core-team",
            title = "Core Team",
            subtitle = "4 members, 3 online",
            avatarInitials = "CT",
            avatarAccent = AvatarAccent.Emerald,
            unreadCount = 0,
            participants = listOf(
                SeedChatParticipant(userId = "self", displayName = "You", isCurrentUser = true),
                SeedChatParticipant(userId = "dmitry", displayName = "Dmitry"),
                SeedChatParticipant(userId = "anna", displayName = "Anna"),
                SeedChatParticipant(userId = "nikita", displayName = "Nikita"),
            ),
            messages = listOf(
                SeedChatMessage.Service(
                    id = "core-service-1",
                    body = "Group key rotated. New RSA bundle is active.",
                    timestamp = "08:40",
                ),
                SeedChatMessage.User(
                    id = "core-user-1",
                    sender = "Dmitry",
                    body = "UI shell is stable on desktop. Android still needs spacing polish.",
                    timestamp = "08:44",
                    isMine = false,
                ),
                SeedChatMessage.User(
                    id = "core-user-2",
                    sender = "You",
                    body = "I am wiring the placeholder flows and the chat screen now.",
                    timestamp = "08:52",
                    isMine = true,
                    deliveryStatus = DeliveryStatus.Read,
                ),
                SeedChatMessage.User(
                    id = "core-user-3",
                    sender = "Anna",
                    body = "Push the shared UI once the repository layer is stable.",
                    timestamp = "08:55",
                    isMine = false,
                ),
            ),
        ),
        SeedChatThread(
            id = "ops-bridge",
            title = "Ops Bridge",
            subtitle = "last seen 2 min ago",
            avatarInitials = "OB",
            avatarAccent = AvatarAccent.Indigo,
            unreadCount = 1,
            participants = listOf(
                SeedChatParticipant(userId = "self", displayName = "You", isCurrentUser = true),
                SeedChatParticipant(userId = "roman", displayName = "Roman"),
            ),
            messages = listOf(
                SeedChatMessage.Service(
                    id = "ops-service-1",
                    body = "Encrypted backup exported for offline storage.",
                    timestamp = "Yesterday",
                ),
                SeedChatMessage.User(
                    id = "ops-user-1",
                    sender = "Roman",
                    body = "Can we keep the debug logs out of the secure channel preview?",
                    timestamp = "11:03",
                    isMine = false,
                ),
                SeedChatMessage.User(
                    id = "ops-user-2",
                    sender = "You",
                    body = "Yes. The preview layer will only expose sanitized text.",
                    timestamp = "11:05",
                    isMine = true,
                    deliveryStatus = DeliveryStatus.Read,
                ),
                SeedChatMessage.Service(
                    id = "ops-service-2",
                    body = "Pending messages will be wrapped by the crypto module once keys exist.",
                    timestamp = "11:06",
                ),
            ),
        ),
    )
}
