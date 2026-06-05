package org.debs.kalog.feature.chat.data.local

import org.debs.kalog.feature.chat.domain.model.AvatarAccent
import org.debs.kalog.feature.chat.domain.model.DeliveryStatus
import org.debs.kalog.feature.chat.localization.chatLocalized

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
            title = chatLocalized(en = "Elena Morozova", ru = "Елена Морозова"),
            subtitle = chatLocalized(en = "online", ru = "в сети"),
            avatarInitials = chatLocalized(en = "EM", ru = "ЕМ"),
            avatarAccent = AvatarAccent.Sky,
            unreadCount = 2,
            participants = listOf(
                SeedChatParticipant(userId = "self", displayName = chatLocalized(en = "You", ru = "Вы"), isCurrentUser = true),
                SeedChatParticipant(userId = "elena", displayName = chatLocalized(en = "Elena", ru = "Елена")),
            ),
            messages = listOf(
                SeedChatMessage.Service(
                    id = "elena-service-1",
                    body = chatLocalized(
                        en = "Secure session established. Fingerprints verified.",
                        ru = "Защищённая сессия установлена. Отпечатки ключей проверены.",
                    ),
                    timestamp = "09:12",
                ),
                SeedChatMessage.User(
                    id = "elena-user-1",
                    sender = chatLocalized(en = "Elena", ru = "Елена"),
                    body = chatLocalized(
                        en = "The laptop key matches the one from the printed backup.",
                        ru = "Ключ ноутбука совпадает с ключом из распечатанной копии.",
                    ),
                    timestamp = "09:14",
                    isMine = false,
                ),
                SeedChatMessage.User(
                    id = "elena-user-2",
                    sender = chatLocalized(en = "You", ru = "Вы"),
                    body = chatLocalized(
                        en = "Great. I will move the release notes into the protected thread.",
                        ru = "Отлично. Перенесу заметки к релизу в защищённую переписку.",
                    ),
                    timestamp = "09:16",
                    isMine = true,
                    deliveryStatus = DeliveryStatus.Read,
                ),
                SeedChatMessage.User(
                    id = "elena-user-3",
                    sender = chatLocalized(en = "Elena", ru = "Елена"),
                    body = chatLocalized(
                        en = "Send me the summary when you are ready.",
                        ru = "Отправьте мне сводку, когда будете готовы.",
                    ),
                    timestamp = "09:18",
                    isMine = false,
                ),
            ),
        ),
        SeedChatThread(
            id = "core-team",
            title = chatLocalized(en = "Core Team", ru = "Команда ядра"),
            subtitle = chatLocalized(en = "4 members, 3 online", ru = "4 участника, 3 в сети"),
            avatarInitials = chatLocalized(en = "CT", ru = "КЯ"),
            avatarAccent = AvatarAccent.Emerald,
            unreadCount = 0,
            participants = listOf(
                SeedChatParticipant(userId = "self", displayName = chatLocalized(en = "You", ru = "Вы"), isCurrentUser = true),
                SeedChatParticipant(userId = "dmitry", displayName = chatLocalized(en = "Dmitry", ru = "Дмитрий")),
                SeedChatParticipant(userId = "anna", displayName = chatLocalized(en = "Anna", ru = "Анна")),
                SeedChatParticipant(userId = "nikita", displayName = chatLocalized(en = "Nikita", ru = "Никита")),
            ),
            messages = listOf(
                SeedChatMessage.Service(
                    id = "core-service-1",
                    body = chatLocalized(
                        en = "Group key rotated. New RSA bundle is active.",
                        ru = "Групповой ключ обновлён. Новый RSA-набор активен.",
                    ),
                    timestamp = "08:40",
                ),
                SeedChatMessage.User(
                    id = "core-user-1",
                    sender = chatLocalized(en = "Dmitry", ru = "Дмитрий"),
                    body = chatLocalized(
                        en = "UI shell is stable on desktop. Android still needs spacing polish.",
                        ru = "Интерфейс стабилен на компьютере. На Android ещё нужно выровнять отступы.",
                    ),
                    timestamp = "08:44",
                    isMine = false,
                ),
                SeedChatMessage.User(
                    id = "core-user-2",
                    sender = chatLocalized(en = "You", ru = "Вы"),
                    body = chatLocalized(
                        en = "I am wiring the placeholder flows and the chat screen now.",
                        ru = "Сейчас подключаю временные сценарии и экран чата.",
                    ),
                    timestamp = "08:52",
                    isMine = true,
                    deliveryStatus = DeliveryStatus.Read,
                ),
                SeedChatMessage.User(
                    id = "core-user-3",
                    sender = chatLocalized(en = "Anna", ru = "Анна"),
                    body = chatLocalized(
                        en = "Push the shared UI once the repository layer is stable.",
                        ru = "Выкладывайте общий интерфейс, когда слой репозитория будет стабилен.",
                    ),
                    timestamp = "08:55",
                    isMine = false,
                ),
            ),
        ),
        SeedChatThread(
            id = "ops-bridge",
            title = chatLocalized(en = "Ops Bridge", ru = "Дежурный мост"),
            subtitle = chatLocalized(en = "last seen 2 min ago", ru = "был в сети 2 мин назад"),
            avatarInitials = chatLocalized(en = "OB", ru = "ДМ"),
            avatarAccent = AvatarAccent.Indigo,
            unreadCount = 1,
            participants = listOf(
                SeedChatParticipant(userId = "self", displayName = chatLocalized(en = "You", ru = "Вы"), isCurrentUser = true),
                SeedChatParticipant(userId = "roman", displayName = chatLocalized(en = "Roman", ru = "Роман")),
            ),
            messages = listOf(
                SeedChatMessage.Service(
                    id = "ops-service-1",
                    body = chatLocalized(
                        en = "Encrypted backup exported for offline storage.",
                        ru = "Зашифрованная копия экспортирована для офлайн-хранения.",
                    ),
                    timestamp = chatLocalized(en = "Yesterday", ru = "Вчера"),
                ),
                SeedChatMessage.User(
                    id = "ops-user-1",
                    sender = chatLocalized(en = "Roman", ru = "Роман"),
                    body = chatLocalized(
                        en = "Can we keep the debug logs out of the secure channel preview?",
                        ru = "Можно убрать отладочные логи из превью защищённого канала?",
                    ),
                    timestamp = "11:03",
                    isMine = false,
                ),
                SeedChatMessage.User(
                    id = "ops-user-2",
                    sender = chatLocalized(en = "You", ru = "Вы"),
                    body = chatLocalized(
                        en = "Yes. The preview layer will only expose sanitized text.",
                        ru = "Да. Слой превью будет показывать только очищенный текст.",
                    ),
                    timestamp = "11:05",
                    isMine = true,
                    deliveryStatus = DeliveryStatus.Read,
                ),
                SeedChatMessage.Service(
                    id = "ops-service-2",
                    body = chatLocalized(
                        en = "Pending messages will be wrapped by the crypto module once keys exist.",
                        ru = "Ожидающие сообщения будут упакованы модулем шифрования, когда появятся ключи.",
                    ),
                    timestamp = "11:06",
                ),
            ),
        ),
    )
}
