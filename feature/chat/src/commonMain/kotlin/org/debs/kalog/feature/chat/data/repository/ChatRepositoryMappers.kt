package org.debs.kalog.feature.chat.data.repository

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.debs.kalog.core.crypto.EncryptionService
import org.debs.kalog.feature.chat.data.currentUserDisplayName
import org.debs.kalog.feature.chat.data.isCurrentUserDisplayName
import org.debs.kalog.feature.chat.data.cache.CachedChatAttachment
import org.debs.kalog.feature.chat.data.cache.ChatAttachmentFileCache
import org.debs.kalog.feature.chat.data.cache.NoOpChatAttachmentFileCache
import org.debs.kalog.feature.chat.data.crypto.ChatDecryptionResult
import org.debs.kalog.feature.chat.data.crypto.ChatMessageCipher
import org.debs.kalog.feature.chat.data.local.LocalChatMessage
import org.debs.kalog.feature.chat.data.local.LocalChatThread
import org.debs.kalog.feature.chat.data.preferences.ChatPreferencesDataSource
import org.debs.kalog.feature.chat.data.remote.ChatRemoteDataSource
import org.debs.kalog.feature.chat.data.remote.NicknameProvidedServiceData
import org.debs.kalog.feature.chat.data.remote.RemoteChatUser
import org.debs.kalog.feature.chat.data.remote.RemoteMessage
import org.debs.kalog.feature.chat.data.remote.ServiceMessageData
import org.debs.kalog.feature.chat.domain.model.AvatarAccent
import org.debs.kalog.feature.chat.domain.model.AvatarSpec
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentKind
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentLoadState
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentPart
import org.debs.kalog.feature.chat.domain.model.ChatMessage
import org.debs.kalog.feature.chat.domain.model.ChatThread
import org.debs.kalog.feature.chat.domain.model.ChatType
import org.debs.kalog.feature.chat.domain.model.DeliveryStatus
import org.debs.kalog.feature.chat.domain.model.InvitationStatus
import org.debs.kalog.feature.chat.localization.chatLocalized
import kotlin.time.Instant
internal data class MessagePaginationState(
    val nextOffset: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
)

@Serializable
internal data class OutgoingMessagePayload(
    @SerialName("messageText") val messageText: String,
    @SerialName("attachments") val attachments: List<OutgoingAttachmentPayload>,
)

@Serializable
internal data class OutgoingAttachmentPayload(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("key") val key: String,
    @SerialName("name") val name: String? = null,
    @SerialName("mimeType") val mimeType: String? = null,
    @SerialName("size") val sizeBytes: Long? = null,
    @SerialName("chunkSize") val chunkSizeBytes: Long? = null,
    @SerialName("partIds") val partIds: List<String> = emptyList(),
    @SerialName("parts") val parts: List<AttachmentPartPayload> = emptyList(),
)

@Serializable
internal data class AttachmentPartPayload(
    @SerialName("id") val id: String,
    @SerialName("index") val index: Int,
    @SerialName("size") val sizeBytes: Long? = null,
    @SerialName("key") val key: String? = null,
)

@Serializable
private data class IncomingMessagePayload(
    @SerialName("messageText") val messageText: String = "",
    @SerialName("message") val legacyMessage: String? = null,
    @SerialName("attachments") val attachments: List<IncomingAttachmentPayload> = emptyList(),
)

@Serializable
private data class IncomingAttachmentPayload(
    @SerialName("id") val id: String = "",
    @SerialName("uuid") val legacyUuid: String = "",
    @SerialName("type") val type: String = "file",
    @SerialName("key") val key: String = "",
    @SerialName("name") val name: String? = null,
    @SerialName("mimeType") val mimeType: String? = null,
    @SerialName("size") val sizeBytes: Long? = null,
    @SerialName("chunkSize") val chunkSizeBytes: Long? = null,
    @SerialName("partIds") val partIds: List<String> = emptyList(),
    @SerialName("parts") val parts: List<AttachmentPartPayload> = emptyList(),
)

internal data class DecodedUserMessagePayload(
    val messageText: String,
    val attachments: List<ChatAttachment>,
)

internal fun undecryptableUserMessagePayload(): DecodedUserMessagePayload {
    return DecodedUserMessagePayload(
        messageText = undecryptableMessageBody(),
        attachments = emptyList(),
    )
}

internal suspend fun ChatMessageCipher.decryptIncomingBody(
    chatId: String,
    chunks: List<String>,
    isEncrypted: Boolean,
): String? {
    return when (val result = decryptIncoming(chatId = chatId, chunks = chunks, isEncrypted = isEncrypted)) {
        is ChatDecryptionResult.Decrypted -> result.body
        ChatDecryptionResult.Undecryptable -> null
    }
}

internal fun String.toIncomingMessagePayload(): DecodedUserMessagePayload {
    val payload = runCatching {
        Json.decodeFromString<IncomingMessagePayload>(this)
    }.getOrNull()

    if (payload == null) {
        return DecodedUserMessagePayload(
            messageText = this,
            attachments = emptyList(),
        )
    }

    return DecodedUserMessagePayload(
        messageText = payload.messageText.ifBlank { payload.legacyMessage.orEmpty() },
        attachments = payload.attachments.map { attachment ->
            val compactParts = attachment.partIds.mapIndexedNotNull { index, partId ->
                partId.takeIf(String::isNotBlank)?.let { id ->
                    ChatAttachmentPart(
                        id = id,
                        index = index,
                        sizeBytes = attachment.inferredPartPlainSize(index),
                    )
                }
            }
            val legacyParts = attachment.parts.mapNotNull { part ->
                part.id.takeIf(String::isNotBlank)?.let { partId ->
                    ChatAttachmentPart(
                        id = partId,
                        index = part.index,
                        sizeBytes = part.sizeBytes,
                        key = part.key,
                    )
                }
            }
            val parts = compactParts.ifEmpty { legacyParts }
            val attachmentId = attachment.id
                .ifBlank { attachment.legacyUuid }
                .ifBlank { parts.firstOrNull()?.id.orEmpty() }
            ChatAttachment(
                id = attachmentId,
                kind = attachment.type.toChatAttachmentKind(),
                name = attachment.name ?: attachmentId,
                mimeType = attachment.mimeType,
                sizeBytes = attachment.sizeBytes,
                encryptionKeyId = attachmentId,
                decryptionKey = attachment.key.ifBlank { null },
                chunkSizeBytes = attachment.chunkSizeBytes,
                parts = parts,
            )
        }.filter { attachment -> attachment.id.isNotBlank() },
    )
}

private fun IncomingAttachmentPayload.inferredPartPlainSize(index: Int): Long? {
    val chunkSize = chunkSizeBytes ?: return null
    val totalSize = sizeBytes ?: return null
    if (index < 0 || chunkSize <= 0L || totalSize < 0L) return null
    val chunkStart = chunkSize * index
    if (chunkStart >= totalSize) return 0L
    return minOf(chunkSize, totalSize - chunkStart)
}

internal fun ChatAttachment.inferredPartPlainSize(index: Int): Long? {
    val chunkSize = chunkSizeBytes ?: return null
    val totalSize = sizeBytes ?: return null
    if (index < 0 || chunkSize <= 0L || totalSize < 0L) return null
    val chunkStart = chunkSize * index
    if (chunkStart >= totalSize) return 0L
    return minOf(chunkSize, totalSize - chunkStart)
}

internal fun ChatAttachmentKind.toMessagePayloadType(): String {
    return when (this) {
        ChatAttachmentKind.File -> "file"
        ChatAttachmentKind.Image -> "photo"
        ChatAttachmentKind.Video -> "video"
        ChatAttachmentKind.Audio -> "audio"
        ChatAttachmentKind.Voice -> "voice"
    }
}

private fun String.toChatAttachmentKind(): ChatAttachmentKind {
    return when (lowercase()) {
        "photo", "image" -> ChatAttachmentKind.Image
        "video" -> ChatAttachmentKind.Video
        "audio", "music", "sound" -> ChatAttachmentKind.Audio
        "voice" -> ChatAttachmentKind.Voice
        else -> ChatAttachmentKind.File
    }
}

internal suspend fun LocalChatThread.toDomain(
    hasMoreMessages: Boolean = false,
    isLoadingMoreMessages: Boolean = false,
    chatMessageCipher: ChatMessageCipher,
    chatPreferencesDataSource: ChatPreferencesDataSource? = null,
    remoteDataSource: ChatRemoteDataSource? = null,
    encryptionService: EncryptionService? = null,
    attachmentFileCache: ChatAttachmentFileCache = NoOpChatAttachmentFileCache,
    cachedAttachmentFiles: Map<String, CachedChatAttachment> = emptyMap(),
    attachmentLoadStates: Map<String, ChatAttachmentLoadState> = emptyMap(),
    loadAttachmentFiles: Boolean = false,
): ChatThread {
    return ChatThread(
        id = id,
        title = title,
        subtitle = subtitle,
        type = typeRaw.toChatType(),
        avatar = AvatarSpec(
            initials = buildAvatarInitials(title).takeUnless { it == "--" } ?: avatarInitials,
            accent = avatarAccent,
        ),
        unreadCount = unreadCount,
        messages = messages.mapNotNull { message ->
            message.toDomain(
                chatMessageCipher = chatMessageCipher,
                chatPreferencesDataSource = chatPreferencesDataSource,
                remoteDataSource = remoteDataSource,
                encryptionService = encryptionService,
                attachmentFileCache = attachmentFileCache,
                cachedAttachmentFiles = cachedAttachmentFiles,
                attachmentLoadStates = attachmentLoadStates,
                loadAttachmentFiles = loadAttachmentFiles,
            )
        },
        hasMoreMessages = hasMoreMessages,
        isLoadingMoreMessages = isLoadingMoreMessages,
        invitationStatus = invitationStatus.toInvitationStatus(),
    )
}

private fun String.toInvitationStatus(): InvitationStatus {
    return when (this) {
        "pending" -> InvitationStatus.Pending
        "accepted" -> InvitationStatus.Accepted
        else -> InvitationStatus.None
    }
}

private suspend fun LocalChatMessage.toDomain(
    chatMessageCipher: ChatMessageCipher,
    chatPreferencesDataSource: ChatPreferencesDataSource? = null,
    remoteDataSource: ChatRemoteDataSource? = null,
    encryptionService: EncryptionService? = null,
    attachmentFileCache: ChatAttachmentFileCache = NoOpChatAttachmentFileCache,
    cachedAttachmentFiles: Map<String, CachedChatAttachment> = emptyMap(),
    attachmentLoadStates: Map<String, ChatAttachmentLoadState> = emptyMap(),
    loadAttachmentFiles: Boolean = false,
): ChatMessage? {
    if (isService) {
        val debugMode = chatPreferencesDataSource?.isDebugModeEnabled() == true
        val body = decodeServiceMessageBody(
            chatId = chatId,
            chunks = encryptedChunks,
            chatMessageCipher = chatMessageCipher,
            chatPreferencesDataSource = chatPreferencesDataSource,
            debugMode = debugMode,
        ) ?: return null
        return ChatMessage.Service(
            id = id,
            body = body,
            timestamp = timestamp.toDisplayTimestamp(),
        )
    }

    val decryptedBody = if (encryptedChunks.isEmpty()) {
        ""
    } else {
        chatMessageCipher.decryptIncomingBody(
            chatId = chatId,
            chunks = encryptedChunks,
            isEncrypted = true,
        )
    }
    val messagePayload = decryptedBody
        ?.toIncomingMessagePayload()
        ?: undecryptableUserMessagePayload()
    val attachments = if (loadAttachmentFiles && remoteDataSource != null && encryptionService != null) {
        messagePayload.attachments.map { attachment ->
            attachment.withCachedDecryptedFile(
                remoteDataSource = remoteDataSource,
                encryptionService = encryptionService,
                attachmentFileCache = attachmentFileCache,
            )
        }
    } else {
        messagePayload.attachments.map { attachment ->
            cachedAttachmentFiles[attachment.id]?.let { cachedFile ->
                attachment.withCachedFile(cachedFile.localUri, cachedFile.sizeBytes)
            } ?: attachment.withLoadState(attachmentLoadStates[attachment.id])
        }
    }
    val resolvedSender = if (isMine == true || isCurrentUserDisplayName(sender)) {
        currentUserDisplayName()
    } else {
        val userId = fromUserId ?: sender.orEmpty()
        chatPreferencesDataSource?.getUserNickname(userId) ?: sender.orEmpty()
    }
    return ChatMessage.User(
        id = id,
        sender = resolvedSender,
        body = messagePayload.messageText,
        timestamp = timestamp.toDisplayTimestamp(),
        isMine = isMine == true,
        deliveryStatus = deliveryStatus ?: DeliveryStatus.Sent,
        attachments = attachments,
    )
}

private suspend fun ChatAttachment.withCachedDecryptedFile(
    remoteDataSource: ChatRemoteDataSource,
    encryptionService: EncryptionService,
    attachmentFileCache: ChatAttachmentFileCache,
): ChatAttachment {
    val cachedFile = attachmentFileCache.get(this)
    if (cachedFile != null) {
        return withCachedFile(cachedFile.localUri, cachedFile.sizeBytes)
    }
    return this
}

private fun ChatAttachment.withCachedFile(
    localUri: String,
    cachedSizeBytes: Long,
): ChatAttachment {
    return copy(
        localUri = localUri,
        sizeBytes = sizeBytes ?: cachedSizeBytes,
        contentBytes = null,
        loadState = ChatAttachmentLoadState.Ready,
    )
}

private fun ChatAttachment.withLoadState(
    explicitState: ChatAttachmentLoadState?,
): ChatAttachment {
    return copy(loadState = explicitState ?: defaultLoadState())
}

private fun ChatAttachment.defaultLoadState(): ChatAttachmentLoadState {
    return if (requiresExplicitDownload()) {
        ChatAttachmentLoadState.WaitingForTap
    } else {
        ChatAttachmentLoadState.NotStarted
    }
}

internal fun ChatAttachment.requiresExplicitDownload(): Boolean {
    return parts.isNotEmpty() ||
        (sizeBytes != null && sizeBytes > EXPLICIT_ATTACHMENT_DOWNLOAD_THRESHOLD_BYTES)
}

internal fun ChatAttachment.shouldScheduleAutomaticAttachmentLoad(
    knownLoadStates: Map<String, ChatAttachmentLoadState>,
): Boolean {
    return when (knownLoadStates[id]) {
        null,
        ChatAttachmentLoadState.NotStarted -> true
        ChatAttachmentLoadState.WaitingForTap,
        ChatAttachmentLoadState.CheckingCache,
        ChatAttachmentLoadState.Downloading,
        ChatAttachmentLoadState.Downloaded,
        ChatAttachmentLoadState.Decrypting,
        ChatAttachmentLoadState.Ready,
        ChatAttachmentLoadState.Failed -> false
    }
}

private suspend fun decodeServiceMessageBody(
    chatId: String,
    chunks: List<String>,
    chatMessageCipher: ChatMessageCipher,
    chatPreferencesDataSource: ChatPreferencesDataSource? = null,
    debugMode: Boolean = false,
): String? {
    if (chunks.isEmpty()) return if (debugMode) "" else null

    val eventType = chunks.first()
    val payloadChunks = chunks.drop(1)

    val payload = if (payloadChunks.isNotEmpty()) {
        chatMessageCipher.decryptIncomingBody(
            chatId = chatId,
            chunks = payloadChunks,
            isEncrypted = true,
        )?.ifBlank { null }
    } else {
        null
    }

    return when (eventType) {
        "user_added" -> if (debugMode) "user_added${payload?.let { " $it" }.orEmpty()}" else null

        "public_key_provided" -> {
            val data = payload?.let {
                runCatching { Json.decodeFromString<ServiceMessageData>(it) }.getOrNull()
            }
            val displayName = data?.userID?.let { userId ->
                chatPreferencesDataSource?.getUserNickname(userId) ?: userId.take(8)
            }
            if (displayName != null) {
                chatLocalized(
                    en = "$displayName joined the chat",
                    ru = "$displayName присоединился к чату",
                )
            } else {
                chatLocalized(
                    en = "User joined the chat",
                    ru = "Пользователь присоединился к чату",
                )
            }
        }

        "user_nickname_provided",
        "nickname_provided" -> {
            val data = payload?.let {
                runCatching { Json.decodeFromString<NicknameProvidedServiceData>(it) }.getOrNull()
            }
            if (data != null) {
                chatLocalized(
                    en = "${data.nickname} changed nickname",
                    ru = "${data.nickname} изменил никнейм",
                )
            } else {
                chatLocalized(
                    en = "User changed nickname",
                    ru = "Пользователь изменил никнейм",
                )
            }
        }

        else -> if (debugMode) "$eventType${payload?.let { " $it" }.orEmpty()}" else null
    }
}

internal fun RemoteMessage.toLocal(
    currentUserId: String,
    position: Long,
): LocalChatMessage {
    val isMine = fromUserId == currentUserId
    val isService = isServiceMessage()
    return LocalChatMessage(
        id = id,
        chatId = chatId,
        sender = if (isMine) currentUserDisplayName() else fromUserId,
        encryptedChunks = chunks,
        timestamp = createdAt,
        isService = isService,
        isMine = if (isService) null else isMine,
        deliveryStatus = if (isService) null else if (isMine) DeliveryStatus.Sent else DeliveryStatus.Read,
        position = position,
        messageType = type,
        fromUserId = fromUserId,
        toUserId = toUserId,
    )
}

internal fun RemoteMessage.isServiceMessage(): Boolean {
    return type.equals("service", ignoreCase = true) || type.equals("system", ignoreCase = true)
}

internal fun RemoteMessage.isNicknameServiceMessage(): Boolean {
    val eventType = chunks.firstOrNull()?.trim().orEmpty()
    return isServiceMessage() &&
        (eventType == "user_nickname_provided" || eventType == "nickname_provided")
}

internal fun String.toDisplayTimestamp(): String {
    if (isBlank()) return ""
    if (matches(SHORT_TIME_REGEX)) return this

    parseIsoTimestamp()?.let { dateTime ->
        return "${dateTime.day} ${dateTime.month.monthName()}, ${dateTime.timeString()}"
    }

    return this
}

private const val EXPLICIT_ATTACHMENT_DOWNLOAD_THRESHOLD_BYTES = 16L * 1024L * 1024L
internal const val AUTOMATIC_ATTACHMENT_SCHEDULE_BATCH_SIZE = 3
private fun undecryptableMessageBody(): String = chatLocalized(
    en = "Could not decrypt message.",
    ru = "Не удалось расшифровать сообщение.",
)

private fun String.parseIsoTimestamp(): LocalDateTime? {
    val normalized = trim()
    if (normalized.isEmpty()) return null

    runCatching {
        return Instant.parse(normalized).toLocalDateTime(TimeZone.currentSystemDefault())
    }

    runCatching {
        return LocalDateTime.parse(normalized)
    }

    val normalizedLocalDateTime = normalized
        .replace(' ', 'T')
        .removeSuffix("Z")

    runCatching {
        return LocalDateTime.parse(normalizedLocalDateTime)
    }

    ISO_TIMESTAMP_REGEX.find(normalized)?.destructured?.let { (year, month, day, hour, minute) ->
        return LocalDateTime.parse("${year}-${month}-${day}T${hour}:${minute}:00")
    }

    return null
}

private fun Month.monthName(): String {
    return when (this) {
        Month.JANUARY -> chatLocalized(en = "Jan", ru = "янв")
        Month.FEBRUARY -> chatLocalized(en = "Feb", ru = "фев")
        Month.MARCH -> chatLocalized(en = "Mar", ru = "мар")
        Month.APRIL -> chatLocalized(en = "Apr", ru = "апр")
        Month.MAY -> chatLocalized(en = "May", ru = "мая")
        Month.JUNE -> chatLocalized(en = "Jun", ru = "июн")
        Month.JULY -> chatLocalized(en = "Jul", ru = "июл")
        Month.AUGUST -> chatLocalized(en = "Aug", ru = "авг")
        Month.SEPTEMBER -> chatLocalized(en = "Sep", ru = "сен")
        Month.OCTOBER -> chatLocalized(en = "Oct", ru = "окт")
        Month.NOVEMBER -> chatLocalized(en = "Nov", ru = "ноя")
        Month.DECEMBER -> chatLocalized(en = "Dec", ru = "дек")
    }
}

private fun LocalDateTime.timeString(): String {
    return "${hour.twoDigits()}:${minute.twoDigits()}"
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

internal fun latestTimestamp(candidates: Iterable<String>): String? {
    return candidates
        .map(String::trim)
        .filter(String::isNotEmpty)
        .maxWithOrNull(Comparator(::compareTimestamps))
}

private fun compareTimestamps(left: String, right: String): Int {
    val leftInstant = parseInstantOrNull(left)
    val rightInstant = parseInstantOrNull(right)
    return when {
        leftInstant != null && rightInstant != null -> leftInstant.compareTo(rightInstant)
        leftInstant != null -> 1
        rightInstant != null -> -1
        else -> left.compareTo(right)
    }
}

internal fun compareMessageChronology(left: RemoteMessage, right: RemoteMessage): Int {
    val leftInstant = parseInstantOrNull(left.createdAt)
    val rightInstant = parseInstantOrNull(right.createdAt)
    return when {
        leftInstant != null && rightInstant != null -> leftInstant.compareTo(rightInstant)
        else -> 0
    }
}

private fun parseInstantOrNull(value: String): Instant? {
    return runCatching { Instant.parse(value) }.getOrNull()
}

internal fun buildAvatarInitials(title: String): String {
    return title
        .split(" ")
        .filter(String::isNotBlank)
        .take(2)
        .joinToString(separator = "") { it.take(1).uppercase() }
        .ifBlank { "--" }
}

internal fun buildAvatarAccent(seed: String): AvatarAccent {
    val accents = AvatarAccent.entries
    val normalizedHash = seed.hashCode().toLong().let { if (it < 0) -it else it }
    return accents[(normalizedHash % accents.size).toInt()]
}

internal suspend fun buildPersonalChatTitle(
    type: String,
    users: List<RemoteChatUser>,
    currentUserId: String,
    chatPreferencesDataSource: ChatPreferencesDataSource,
): String {
    if (type.toChatType() != ChatType.Personal) return ""
    val otherUserId = users.firstOrNull { it.userId != currentUserId }?.userId ?: return ""
    return chatPreferencesDataSource.getUserNickname(otherUserId) ?: otherUserId
}

internal fun buildSubtitle(type: String, users: List<RemoteChatUser>): String {
    return when (type.toChatType()) {
        ChatType.Group -> chatLocalized(
            en = "Members: ${users.size.coerceAtLeast(1)}",
            ru = "Участников: ${users.size.coerceAtLeast(1)}",
        )
        ChatType.Personal -> chatLocalized(en = "Personal chat", ru = "Личный чат")
        ChatType.Unknown -> when {
            users.size > 2 -> chatLocalized(
                en = "Members: ${users.size}",
                ru = "Участников: ${users.size}",
            )
            type.isNotBlank() -> type
            else -> ""
        }
    }
}

internal fun String.toChatType(): ChatType {
    return when {
        contains("group", ignoreCase = true) -> ChatType.Group
        isBlank() -> ChatType.Unknown
        else -> ChatType.Personal
    }
}

private val SHORT_TIME_REGEX = Regex("""^\d{2}:\d{2}$""")
private val ISO_TIMESTAMP_REGEX = Regex("""(\d{4})-(\d{2})-(\d{2})[T\s].*?(\d{2}):(\d{2})""")
