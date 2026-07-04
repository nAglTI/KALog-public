package org.debs.kalog.feature.chat.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.debs.kalog.core.crypto.EncryptionService
import org.debs.kalog.core.crypto.GeneratedAttachmentKey
import org.debs.kalog.core.crypto.GeneratedKeyPair
import org.debs.kalog.core.crypto.PrivateKeyRef
import org.debs.kalog.feature.chat.data.currentUserDisplayName
import org.debs.kalog.feature.chat.data.cache.ChatAttachmentFileCache
import org.debs.kalog.feature.chat.data.cache.EncryptedCachedAttachmentPart
import org.debs.kalog.feature.chat.data.cache.NoOpChatAttachmentFileCache
import org.debs.kalog.feature.chat.data.crypto.ChatKeyStore
import org.debs.kalog.feature.chat.data.crypto.ChatMessageCipher
import org.debs.kalog.feature.chat.data.crypto.ChatParticipantKey
import org.debs.kalog.feature.chat.data.local.ChatLocalDataSource
import org.debs.kalog.feature.chat.data.local.LocalChatMessage
import org.debs.kalog.feature.chat.data.local.LocalChatThread
import org.debs.kalog.feature.chat.data.local.isLocalEchoMessageId
import org.debs.kalog.feature.chat.data.preferences.ChatPreferencesDataSource
import org.debs.kalog.feature.chat.data.remote.ChatRemoteDataSource
import org.debs.kalog.feature.chat.data.remote.NicknameProvidedServiceData
import org.debs.kalog.feature.chat.data.remote.RemoteCreateSelfChatResult
import org.debs.kalog.feature.chat.data.remote.RemoteChatInfo
import org.debs.kalog.feature.chat.data.remote.RemoteChatSummary
import org.debs.kalog.feature.chat.data.remote.RemoteChatUser
import org.debs.kalog.feature.chat.data.remote.RemoteMessage
import org.debs.kalog.feature.chat.data.remote.RemotePolledMessages
import org.debs.kalog.feature.chat.data.remote.RemoteSendPayload
import org.debs.kalog.feature.chat.data.remote.ServiceMessageData
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentEncryptionSpec
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentPart
import org.debs.kalog.feature.chat.domain.model.ChatParticipant
import org.debs.kalog.feature.chat.domain.model.ChatType
import org.debs.kalog.feature.chat.domain.model.DeliveryStatus
import org.debs.kalog.feature.chat.domain.model.PreparedChatAttachment
import org.debs.kalog.feature.chat.domain.repository.ChatRepository
import org.debs.kalog.feature.chat.localization.chatLocalized
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Serializable
private data class AccountKeySyncServiceData(
    @SerialName("eventId") val eventId: String,
    @SerialName("originDeviceId") val originDeviceId: String,
    @SerialName("chatId") val chatId: String,
    @SerialName("chatType") val chatType: String,
    @SerialName("publicKey") val publicKey: String,
    @SerialName("privateKeyRef") val privateKeyRef: String,
    @SerialName("createdAtEpochMillis") val createdAtEpochMillis: Long,
)

@Serializable
private data class AccountKeySyncRequestServiceData(
    @SerialName("eventId") val eventId: String,
    @SerialName("originDeviceId") val originDeviceId: String,
    @SerialName("chatId") val chatId: String,
    @SerialName("createdAtEpochMillis") val createdAtEpochMillis: Long,
)

internal class OfflineFirstChatRepository(
    private val localDataSource: ChatLocalDataSource,
    private val remoteDataSource: ChatRemoteDataSource,
    private val chatMessageCipher: ChatMessageCipher,
    private val chatKeyStore: ChatKeyStore,
    private val encryptionService: EncryptionService,
    private val chatPreferencesDataSource: ChatPreferencesDataSource,
    private val json: Json,
    private val attachmentFileCache: ChatAttachmentFileCache = NoOpChatAttachmentFileCache,
) : ChatRepository {
    private val sessionMutex = Mutex()
    private val paginationStates = MutableStateFlow<Map<String, MessagePaginationState>>(emptyMap())
    private var sessionStarted = false
    internal var lastPollTimestamp: String? = null
    private var lastKnownChatsCatchUpAt: TimeMark? = null
    private val syncLoopJob = MutableStateFlow<Job?>(null)
    private val syncEnabled = MutableStateFlow(true)
    private val nicknameVersion = MutableStateFlow(0)
    private val attachmentDownloadCoordinator = ChatAttachmentDownloadCoordinator(
        remoteDataSource = remoteDataSource,
        encryptionService = encryptionService,
        attachmentFileCache = attachmentFileCache,
    )

    override suspend fun startSession() {
        syncEnabled.value = true

        sessionMutex.withLock {
            if (sessionStarted) return

            try {
                restoreLastPollTimestamp()
                bootstrapSession()
                sessionStarted = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Allow future retries when bootstrap fails.
            }
        }
    }

    override suspend fun runSyncLoop() {
        syncEnabled.value = true
        val loopJob = currentCoroutineContext()[Job]
        syncLoopJob.value = loopJob

        try {
            coroutineScope {
                val catchUpJob = launch { runKnownChatsCatchUpLoop() }
                try {
                    while (currentCoroutineContext().isActive && syncEnabled.value) {
                        val pollStartedAt = TimeSource.Monotonic.markNow()
                        try {
                            startSession()
                            ensureUserSession()
                            applyPolledMessages(
                                remoteDataSource.pollMessages(
                                    since = lastPollTimestamp ?: MIN_POLL_TIMESTAMP,
                                ),
                            )
                            catchUpKnownChatsIfNeeded()
                            throttleSuccessfulPollIteration(pollStartedAt)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            delay(CHAT_SYNC_RETRY_DELAY_MS)
                        }
                    }
                } finally {
                    catchUpJob.cancelAndJoin()
                }
            }
        } finally {
            if (syncLoopJob.value === loopJob) {
                syncLoopJob.value = null
            }
        }
    }

    override suspend fun getCurrentUserId(): String? {
        startSession()
        return chatKeyStore.currentUserId()
    }

    override suspend fun clearAllData() {
        syncEnabled.value = false
        val activeSyncJob = syncLoopJob.value
        val currentJob = currentCoroutineContext()[Job]
        if (activeSyncJob != null && activeSyncJob !== currentJob) {
            activeSyncJob.cancelAndJoin()
        }
        sessionStarted = false
        lastPollTimestamp = null
        lastKnownChatsCatchUpAt = null
        paginationStates.value = emptyMap()
        deleteStoredPrivateKeys(localDataSource.observeThreads().value.map(LocalChatThread::id))
        localDataSource.clearAll()
        chatPreferencesDataSource.clearAll()
        chatKeyStore.clearAll()
        attachmentDownloadCoordinator.clearAll()
        nicknameVersion.value = nicknameVersion.value + 1
    }

    override suspend fun clearCachedAttachments(): Int {
        return attachmentDownloadCoordinator.clearCachedAttachments()
    }

    override suspend fun clearCachedAttachmentsOlderThan(ageMillis: Long): Int {
        return attachmentDownloadCoordinator.clearCachedAttachmentsOlderThan(ageMillis)
    }

    override suspend fun closeChat() {
        attachmentDownloadCoordinator.cancelAll()
        chatPreferencesDataSource.clearLastOpenedChatId()
    }

    override fun observeChats() = combine(localDataSource.observeThreads(), nicknameVersion) { threads, _ ->
        threads.map { thread ->
            thread.toDomain(
                chatMessageCipher = chatMessageCipher,
                chatPreferencesDataSource = chatPreferencesDataSource,
                loadAttachmentFiles = false,
            )
        }
    }

    override fun observeChat(chatId: String) = combine(
        localDataSource.observeThreads(),
        paginationStates,
        nicknameVersion,
        attachmentDownloadCoordinator.cachedAttachmentFiles,
        attachmentDownloadCoordinator.attachmentLoadStates,
    ) { threads, states, _, cachedFiles, loadStates ->
        val thread = threads.firstOrNull { it.id == chatId } ?: return@combine null
        val paginationState = states[chatId] ?: MessagePaginationState()
        val domainThread = thread.toDomain(
            hasMoreMessages = paginationState.hasMore,
            isLoadingMoreMessages = paginationState.isLoading,
            chatMessageCipher = chatMessageCipher,
            chatPreferencesDataSource = chatPreferencesDataSource,
            cachedAttachmentFiles = cachedFiles,
            attachmentLoadStates = loadStates,
            loadAttachmentFiles = false,
        )
        attachmentDownloadCoordinator.scheduleAttachmentDownloads(domainThread)
        domainThread
    }

    override suspend fun requestAttachmentDownload(chatId: String, attachmentId: String) {
        val attachment = findAttachmentInChat(chatId, attachmentId) ?: return
        attachmentDownloadCoordinator.activateChat(chatId)
        attachmentDownloadCoordinator.scheduleAttachmentDownload(
            chatId = chatId,
            attachment = attachment,
            userInitiated = true,
        )
    }

    override suspend fun openChat(chatId: String) {
        attachmentDownloadCoordinator.activateChat(chatId)
        startSession()
        ensureUserSession()
        localDataSource.markChatOpened(chatId)
        chatPreferencesDataSource.saveLastOpenedChatId(chatId)
        syncChat(chatId)
        initializePaginationFromLoadedMessages(chatId)
    }

    override suspend fun refreshChatMessages(chatId: String): Boolean {
        startSession()
        ensureUserSession()

        if (chatKeyStore.participantsFor(chatId).isEmpty()) {
            syncChat(chatId, skipKeyRegistration = true)
        }
        if (!ensureChatKeyRegistered(chatId)) return false
        val history = remoteDataSource.getMessageHistory(
            chatId = chatId,
            offset = 0,
            limit = DEFAULT_MESSAGES_PAGE_SIZE,
        )
        if (history.isEmpty()) return false

        val thread = localDataSource.observeThreads().value.firstOrNull { it.id == chatId } ?: return false
        val existingMessageIds = thread.messages.mapTo(mutableSetOf(), LocalChatMessage::id)
        val latestLocalTimestamp = latestTimestamp(
            thread.messages
                .filterNot { message -> message.id.isLocalEchoMessageId() }
                .map(LocalChatMessage::timestamp),
        )
        val recentMessages = normalizeMessagesOldestFirst(history)
            .filter { message -> message.id !in existingMessageIds }
            .filter { message ->
                latestLocalTimestamp == null ||
                    isSameOrNewerTimestamp(message.createdAt, latestLocalTimestamp)
            }
        if (recentMessages.isEmpty()) return false

        handleHistoricalNicknameMessages(recentMessages)
        handleServiceMessages(recentMessages)

        val currentUserId = requireCurrentUserId()
        val senderIds = recentMessages.map(RemoteMessage::fromUserId)
            .filter { userId -> userId != currentUserId }
            .toSet()
        val chatIdsNeedingParticipantRefresh = if (hasMissingKeysForUsers(chatId, senderIds)) {
            setOf(chatId)
        } else {
            emptySet()
        }

        val nextPosition = localDataSource.nextMessagePosition(chatId)
        localDataSource.appendMessages(
            chatId = chatId,
            messages = recentMessages.mapIndexed { index, message ->
                message.toLocal(
                    currentUserId = currentUserId,
                    position = nextPosition + index,
                )
            },
        )
        refreshParticipantsSafely(chatIdsNeedingParticipantRefresh)
        if (chatPreferencesDataSource.currentOpenedChatId() == chatId) {
            localDataSource.markChatOpened(chatId)
        }
        return true
    }

    private suspend fun catchUpKnownChatsIfNeeded() {
        val lastCatchUpAt = lastKnownChatsCatchUpAt
        if (
            lastCatchUpAt != null &&
            lastCatchUpAt.elapsedNow() < KNOWN_CHATS_CATCH_UP_INTERVAL_MS.milliseconds
        ) {
            return
        }
        lastKnownChatsCatchUpAt = TimeSource.Monotonic.markNow()

        val chatIds = localDataSource.observeThreads().value
            .sortedByDescending(LocalChatThread::lastMessagePosition)
            .take(KNOWN_CHATS_CATCH_UP_LIMIT)
            .map(LocalChatThread::id)

        for (chatId in chatIds) {
            try {
                refreshChatMessages(chatId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Polling and the next catch-up pass can retry transient per-chat failures.
            }
        }
    }

    private suspend fun runKnownChatsCatchUpLoop() {
        while (currentCoroutineContext().isActive && syncEnabled.value) {
            try {
                startSession()
                ensureUserSession()
                catchUpKnownChatsIfNeeded()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Long-poll remains the primary sync path; catch-up retries on its own cadence.
            }
            delay(KNOWN_CHATS_CATCH_UP_LOOP_INTERVAL_MS)
        }
    }

    private suspend fun appendLocalEchoMessage(
        chatId: String,
        currentUserId: String,
        chunks: List<String>,
    ) {
        val nextPosition = localDataSource.nextMessagePosition(chatId)
        localDataSource.appendMessages(
            chatId = chatId,
            messages = listOf(
                LocalChatMessage(
                    id = newLocalEchoMessageId(),
                    chatId = chatId,
                    sender = currentUserDisplayName(),
                    encryptedChunks = chunks,
                    timestamp = Clock.System.now().toString(),
                    isService = false,
                    isMine = true,
                    deliveryStatus = DeliveryStatus.Sent,
                    position = nextPosition,
                    messageType = "default",
                    fromUserId = currentUserId,
                    toUserId = currentUserId,
                ),
            ),
        )
    }

    private suspend fun findAttachmentInChat(
        chatId: String,
        attachmentId: String,
    ): ChatAttachment? {
        val thread = localDataSource.observeThreads().value.firstOrNull { it.id == chatId } ?: return null
        thread.messages
            .filterNot { message -> message.isService }
            .forEach { message ->
                val payload = runCatching<DecodedUserMessagePayload?> {
                    val decryptedBody = if (message.encryptedChunks.isEmpty()) {
                        ""
                    } else {
                        chatMessageCipher.decryptIncomingBody(
                            chatId = chatId,
                            chunks = message.encryptedChunks,
                            isEncrypted = true,
                        ) ?: return@runCatching null
                    }
                    decryptedBody.toIncomingMessagePayload()
                }.getOrNull()
                payload?.attachments?.firstOrNull { attachment -> attachment.id == attachmentId }?.let { attachment ->
                    return attachment
                }
            }
        return null
    }

    override suspend fun loadMoreMessages(chatId: String): Boolean {
        startSession()
        ensureUserSession()

        val currentState = paginationStates.value[chatId] ?: MessagePaginationState()
        if (!currentState.hasMore || currentState.isLoading) return false

        val currentOffset = maxOf(
            currentState.nextOffset,
            loadedMessageCount(chatId),
        )
        updatePaginationState(chatId) { it.copy(isLoading = true) }
        return try {
            val history = remoteDataSource.getMessageHistory(
                chatId = chatId,
                offset = currentOffset,
                limit = DEFAULT_MESSAGES_PAGE_SIZE,
            )
            if (history.isEmpty()) {
                updatePaginationState(chatId) { state -> state.copy(hasMore = false, isLoading = false) }
                return false
            }

            val orderedMessages = normalizeMessagesOldestFirst(history)
            handleHistoricalNicknameMessages(orderedMessages)
            val currentUserId = requireCurrentUserId()
            if (loadedMessageCount(chatId) == 0) {
                localDataSource.replaceMessages(
                    chatId = chatId,
                    messages = orderedMessages.mapIndexed { index, message ->
                        message.toLocal(
                            currentUserId = currentUserId,
                            position = index.toLong() + 1,
                        )
                    },
                )
            } else {
                val startPosition = localDataSource.previousMessagePosition(chatId) - orderedMessages.size + 1
                localDataSource.prependMessages(
                    chatId = chatId,
                    messages = orderedMessages.mapIndexed { index, message ->
                        message.toLocal(
                            currentUserId = currentUserId,
                            position = startPosition + index,
                        )
                    },
                )
            }
            updatePaginationState(chatId) { state ->
                state.copy(
                    nextOffset = currentOffset + history.size,
                    hasMore = history.size >= DEFAULT_MESSAGES_PAGE_SIZE,
                    isLoading = false,
                )
            }
            true
        } catch (error: CancellationException) {
            updatePaginationState(chatId) { state -> state.copy(isLoading = false) }
            throw error
        } catch (_: Throwable) {
            updatePaginationState(chatId) { state -> state.copy(isLoading = false) }
            false
        }
    }

    override suspend fun sendMessage(
        chatId: String,
        plainText: String,
        attachments: List<PreparedChatAttachment>,
    ) {
        startSession()
        ensureUserSession()
        prepareChatParticipantsForSending(chatId)

        val payloads = chatMessageCipher.encryptOutgoing(chatId, buildMessagePayload(plainText, attachments))
        if (payloads.isEmpty()) return
        val currentUserId = requireCurrentUserId()
        val localEchoPayload = payloads.firstOrNull { payload -> payload.recipientId == currentUserId }

        remoteDataSource.sendMessage(
            chatId = chatId,
            payloads = payloads.map { payload ->
                RemoteSendPayload(
                    recipientId = payload.recipientId,
                    chunks = payload.chunks,
                )
            },
        )

        if (localEchoPayload != null) {
            appendLocalEchoMessage(
                chatId = chatId,
                currentUserId = currentUserId,
                chunks = localEchoPayload.chunks,
            )
        }
        localDataSource.markChatOpened(chatId)
    }

    override suspend fun prepareAttachment(
        chatId: String,
        attachment: ChatAttachment,
        onUploadProgress: (bytesSent: Long, totalBytes: Long) -> Unit,
    ): PreparedChatAttachment {
        startSession()
        ensureUserSession()

        val generatedKey = encryptionService.generateAttachmentKey()
        val sourceSizeBytes = attachment.contentBytes?.size?.toLong() ?: attachment.sizeBytes
        val shouldUseChunkedUpload = sourceSizeBytes != null &&
            sourceSizeBytes > ATTACHMENT_UPLOAD_CHUNK_BYTES &&
            (attachment.contentBytes != null || attachment.localUri != null)

        if (shouldUseChunkedUpload) {
            return prepareChunkedAttachment(
                chatId = chatId,
                attachment = attachment,
                key = generatedKey,
                totalBytes = sourceSizeBytes,
                onUploadProgress = onUploadProgress,
            )
        }

        val uploadReservation = remoteDataSource.initAttachmentUpload()
        val plainBytes = attachment.contentBytes ?: attachment.localUri?.let { localUri ->
            attachmentFileCache.readBytes(localUri)
        } ?: error(
            chatLocalized(
                en = "Unable to read attachment bytes.",
                ru = "Не удалось прочитать данные вложения.",
            ),
        )
        val encryptedBytes = encryptionService.encryptAttachment(plainBytes, generatedKey.key)
        attachmentFileCache.putEncrypted(
            attachmentId = uploadReservation.attachmentId,
            fileName = attachment.name,
            mimeType = attachment.mimeType,
            encryptedBytes = encryptedBytes,
            plainSizeBytes = plainBytes.size.toLong(),
            decryptionKey = generatedKey.key,
        )
        remoteDataSource.uploadAttachment(
            attachmentId = uploadReservation.attachmentId,
            uploadToken = uploadReservation.uploadToken,
            bytes = encryptedBytes,
            contentType = attachment.mimeType,
            onProgress = onUploadProgress,
        )

        val preparedAttachment = attachment.copy(
            id = uploadReservation.attachmentId,
            encryptionKeyId = uploadReservation.attachmentId,
            contentBytes = null,
            localUri = null,
        )
        return PreparedChatAttachment(
            attachment = preparedAttachment,
            encryption = ChatAttachmentEncryptionSpec(
                uuid = uploadReservation.attachmentId,
                key = generatedKey.key,
                algorithm = generatedKey.algorithmLabel,
                sizeBits = generatedKey.sizeBits,
            ),
        )
    }

    private suspend fun prepareChunkedAttachment(
        chatId: String,
        attachment: ChatAttachment,
        key: GeneratedAttachmentKey,
        totalBytes: Long,
        onUploadProgress: (bytesSent: Long, totalBytes: Long) -> Unit,
    ): PreparedChatAttachment {
        val firstReservation = remoteDataSource.initAttachmentUpload()
        val parts = mutableListOf<ChatAttachmentPart>()
        val encryptedCacheParts = mutableListOf<EncryptedCachedAttachmentPart>()
        var offset = 0L
        var uploadedBytes = 0L
        var index = 0

        while (offset < totalBytes) {
            val plainChunk = readAttachmentChunk(attachment, offset, ATTACHMENT_UPLOAD_CHUNK_BYTES)
                ?: error(
                    chatLocalized(
                        en = "Unable to read attachment chunk.",
                        ru = "Не удалось прочитать фрагмент вложения.",
                    ),
                )
            if (plainChunk.isEmpty()) break

            val reservation = if (index == 0) {
                firstReservation
            } else {
                remoteDataSource.initAttachmentUpload()
            }
            val encryptedBytes = encryptionService.encryptAttachment(plainChunk, key.key)
            val uploadedBeforeChunk = uploadedBytes
            remoteDataSource.uploadAttachment(
                attachmentId = reservation.attachmentId,
                uploadToken = reservation.uploadToken,
                bytes = encryptedBytes,
                contentType = attachment.mimeType,
            ) { bytesSent, encryptedTotalBytes ->
                val plainBytesSent = if (encryptedTotalBytes > 0L) {
                    ((bytesSent * plainChunk.size) / encryptedTotalBytes)
                        .coerceIn(0L, plainChunk.size.toLong())
                } else {
                    0L
                }
                onUploadProgress(
                    (uploadedBeforeChunk + plainBytesSent).coerceAtMost(totalBytes),
                    totalBytes,
                )
            }

            parts += ChatAttachmentPart(
                id = reservation.attachmentId,
                index = index,
                sizeBytes = plainChunk.size.toLong(),
            )
            encryptedCacheParts += EncryptedCachedAttachmentPart(
                id = reservation.attachmentId,
                index = index,
                encryptedBytes = encryptedBytes,
                plainSizeBytes = plainChunk.size.toLong(),
                decryptionKey = key.key,
            )
            uploadedBytes += plainChunk.size
            offset += plainChunk.size
            index += 1

            if (plainChunk.size < ATTACHMENT_UPLOAD_CHUNK_BYTES) break
        }

        check(parts.isNotEmpty()) {
            chatLocalized(
                en = "Attachment did not produce any upload chunks.",
                ru = "Вложение не удалось разбить на фрагменты для загрузки.",
            )
        }

        val preparedAttachment = attachment.copy(
            id = firstReservation.attachmentId,
            encryptionKeyId = firstReservation.attachmentId,
            sizeBytes = attachment.sizeBytes ?: totalBytes,
            contentBytes = null,
            localUri = null,
            chunkSizeBytes = ATTACHMENT_UPLOAD_CHUNK_BYTES.toLong(),
            parts = parts,
        )
        attachmentFileCache.putEncryptedFromChunks(
            attachmentId = firstReservation.attachmentId,
            fileName = attachment.name,
            mimeType = attachment.mimeType,
            plainSizeBytes = totalBytes,
        ) { append ->
            encryptedCacheParts.forEach { part -> append(part) }
        }
        return PreparedChatAttachment(
            attachment = preparedAttachment,
            encryption = ChatAttachmentEncryptionSpec(
                uuid = firstReservation.attachmentId,
                key = key.key,
                algorithm = key.algorithmLabel,
                sizeBits = key.sizeBits,
                parts = parts,
                chunkSizeBytes = ATTACHMENT_UPLOAD_CHUNK_BYTES.toLong(),
            ),
        )
    }

    private suspend fun readAttachmentChunk(
        attachment: ChatAttachment,
        offset: Long,
        length: Int,
    ): ByteArray? {
        attachment.contentBytes?.let { bytes ->
            if (offset >= bytes.size) return ByteArray(0)
            val startIndex = offset.toInt()
            val endIndex = minOf(startIndex + length, bytes.size)
            return bytes.copyOfRange(startIndex, endIndex)
        }

        return attachment.localUri?.let { localUri ->
            attachmentFileCache.readBytes(localUri, offset, length)
        }
    }

    override suspend fun createDirectChat(targetUserId: String): String {
        startSession()
        ensureUserSession()
        val chatKeyPair = generateChatKeyPair()

        val chat = remoteDataSource.createDirectChat(
            targetUserId = targetUserId,
            publicKey = chatKeyPair.publicKey,
        )
        chatKeyStore.saveChatKeyPair(chat.id, chatKeyPair.publicKey, chatKeyPair.privateKeyRef)
        syncChat(chat.id)
        localDataSource.updateInvitationStatus(chat.id, INVITATION_STATUS_ACCEPTED)
        publishChatKeySyncSafely(chat.id)
        return chat.id
    }

    override suspend fun createGroupChat(publicKey: String?): String {
        startSession()
        ensureUserSession()
        val generatedChatKeyPair = if (publicKey == null) generateChatKeyPair() else null

        val chat = remoteDataSource.createGroupChat(generatedChatKeyPair?.publicKey ?: publicKey.orEmpty())
        generatedChatKeyPair?.let { keyPair ->
            chatKeyStore.saveChatKeyPair(chat.id, keyPair.publicKey, keyPair.privateKeyRef)
        }
        syncChat(chat.id)
        localDataSource.updateInvitationStatus(chat.id, INVITATION_STATUS_ACCEPTED)
        publishChatKeySyncSafely(chat.id)
        return chat.id
    }

    override suspend fun ensureSelfChat(): String {
        startSession()
        ensureUserSession()
        return ensureSelfChatInternal()
    }

    override suspend fun inviteUserToChat(chatId: String, userId: String) {
        startSession()
        ensureUserSession()

        remoteDataSource.inviteUserToChat(chatId, userId)
        syncChat(chatId)
    }

    override suspend fun leaveGroupChat(chatId: String) {
        startSession()
        ensureUserSession()

        remoteDataSource.leaveGroupChat(chatId)
        removeLocalChat(chatId)
        paginationStates.value = paginationStates.value - chatId
    }

    override suspend fun setGroupChatPublicKey(chatId: String, publicKey: String) {
        startSession()
        ensureUserSession()

        remoteDataSource.setGroupChatPublicKey(chatId, publicKey)
        syncChatParticipants(chatId)
    }

    override suspend fun renameChatLocally(chatId: String, newTitle: String) {
        chatPreferencesDataSource.saveChatTitle(chatId, newTitle)
        localDataSource.updateChatTitle(chatId, newTitle)
    }

    override suspend fun getChatParticipants(chatId: String): List<ChatParticipant> {
        return chatKeyStore.participantsFor(chatId).map { participant ->
            val resolvedDisplayName = if (!participant.isCurrentUser) {
                chatPreferencesDataSource.getUserNickname(participant.userId)
                    ?: participant.displayName
            } else {
                participant.displayName
            }
            ChatParticipant(
                userId = participant.userId,
                displayName = resolvedDisplayName,
                isCurrentUser = participant.isCurrentUser,
            )
        }
    }

    override suspend fun acceptChatInvitation(chatId: String) {
        startSession()
        ensureUserSession()

        if (!ensureChatKeyRegistered(chatId)) return
        localDataSource.updateInvitationStatus(chatId, INVITATION_STATUS_ACCEPTED)

        val nickname = chatPreferencesDataSource.getNickname()
        if (nickname.isNotBlank()) {
            sendNicknameProvidedServiceMessage(chatId, nickname)
        }
    }

    override suspend fun declineChatInvitation(chatId: String) {
        startSession()
        ensureUserSession()

        remoteDataSource.leaveChat(chatId)
        removeLocalChat(chatId)
        paginationStates.value = paginationStates.value - chatId
    }

    override suspend fun broadcastNicknameToAllChats() {
        val nickname = chatPreferencesDataSource.getNickname()
        if (nickname.isBlank()) return

        startSession()
        ensureUserSession()

        val currentUserId = requireCurrentUserId()
        rememberUserNickname(currentUserId, nickname)

        val remoteChatIds = remoteDataSource.getChats().map { it.id }
        val localThreads = localDataSource.observeThreads().value
        val localChatIds = localThreads.map(LocalChatThread::id)
        val allChatIds = (localChatIds + remoteChatIds)
            .distinct()
            .filter { chatId -> chatId in remoteChatIds || remoteChatIds.isEmpty() }
        val pendingChatIds = localThreads
            .filter { thread -> thread.invitationStatus == INVITATION_STATUS_PENDING }
            .mapTo(mutableSetOf(), LocalChatThread::id)

        var sentCount = 0
        var lastError: Throwable? = null

        for (chatId in allChatIds.asReversed()) {
            if (chatId in pendingChatIds) continue
            try {
                if (sendNicknameProvidedServiceMessage(chatId, nickname)) {
                    sentCount++
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = error
            }
        }

        if (lastError != null) {
            throw lastError
        }
    }

    override suspend fun findDirectChatWith(userId: String): String? {
        val threads = localDataSource.observeThreads().value
        val currentUserId = chatKeyStore.currentUserId() ?: return null
        for (thread in threads) {
            if (thread.typeRaw.toChatType() != ChatType.Personal) continue
            val participants = chatKeyStore.participantsFor(thread.id)
            val hasTarget = participants.any { it.userId == userId }
            val hasSelf = participants.any { it.userId == currentUserId }
            if (hasTarget && hasSelf) return thread.id
        }
        return null
    }

    private suspend fun ensureChatKeyRegistered(chatId: String): Boolean {
        val currentUserId = chatKeyStore.currentUserId() ?: return false
        val thread = localDataSource.observeThreads().value.firstOrNull { it.id == chatId }
        if (thread?.typeRaw?.toChatType() == ChatType.Self) {
            val keyPair = ensureSelfChatKeyPair(chatId)
            chatKeyStore.saveSelfChatId(chatId)
            chatKeyStore.saveChatKeyPair(chatId, keyPair.publicKey, keyPair.privateKeyRef)
            return true
        }
        val participants = chatKeyStore.participantsFor(chatId)
        val currentUserParticipant = participants.firstOrNull { it.userId == currentUserId }
        val storedPublicKey = chatKeyStore.chatPublicKey(chatId)
        val storedPrivateKeyRef = chatKeyStore.chatPrivateKeyRef(chatId)
        val storedKeyPair = if (!storedPublicKey.isNullOrBlank() && storedPrivateKeyRef != null) {
            GeneratedKeyPair(
                publicKey = storedPublicKey,
                privateKeyRef = storedPrivateKeyRef,
            )
        } else {
            null
        }

        if (
            currentUserParticipant != null &&
            currentUserParticipant.publicKey.isNotBlank() &&
            storedKeyPair != null &&
            currentUserParticipant.publicKey == storedKeyPair.publicKey
        ) {
            return true
        }

        if (currentUserParticipant != null && currentUserParticipant.publicKey.isNotBlank() && storedKeyPair == null) {
            requestChatKeySyncSafely(chatId)
            return false
        }

        val keyPair = storedKeyPair ?: run {
            generateChatKeyPair().also { generatedKeyPair ->
                chatKeyStore.saveChatKeyPair(chatId, generatedKeyPair.publicKey, generatedKeyPair.privateKeyRef)
            }
        }

        remoteDataSource.setGroupChatPublicKey(chatId, keyPair.publicKey)
        syncChatParticipants(chatId)
        return true
    }

    private suspend fun prepareChatParticipantsForSending(chatId: String) {
        check(ensureChatKeyRegistered(chatId)) {
            chatLocalized(
                en = "Chat key sync is pending.",
                ru = "РЎРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ РєР»СЋС‡Р° С‡Р°С‚Р° РµС‰Рµ РЅРµ Р·Р°РІРµСЂС€РµРЅР°.",
            )
        }
        if (hasParticipantsWithMissingKeys(chatId)) {
            syncChatParticipants(chatId)
        }
    }

    private suspend fun bootstrapSession() {
        ensureUserSession()
        try {
            syncChats()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Polling and per-chat sync can recover even if the initial chat list sync fails.
        }
    }

    private suspend fun ensureUserSession() {
        val keysRegenerated = ensureTransportKeys()
        if (!keysRegenerated && chatKeyStore.currentUserId() != null && chatKeyStore.serverPublicKey() != null) return

        val session = remoteDataSource.start(requireTransportPublicKey())
        chatKeyStore.saveCurrentUserKeys(
            userId = session.userId,
            publicKey = requireTransportPublicKey(),
            privateKeyRef = requireTransportPrivateKeyRef(),
        )
        chatKeyStore.saveServerPublicKey(session.serverPublicKey)
    }

    private suspend fun ensureTransportKeys(): Boolean {
        val publicKey = chatKeyStore.currentUserPublicKey()
        val privateKeyRef = chatKeyStore.currentUserPrivateKeyRef()
        if (publicKey != null && privateKeyRef != null) return false

        val keyPair = encryptionService.generateKeyPair()
        chatKeyStore.saveCurrentUserKeys(
            userId = chatKeyStore.currentUserId(),
            publicKey = keyPair.publicKey,
            privateKeyRef = keyPair.privateKeyRef,
        )
        return true
    }

    private suspend fun ensureSelfChatInternal(): String {
        chatKeyStore.selfChatId()?.takeIf(String::isNotBlank)?.let { storedChatId ->
            val keyPair = ensureSelfChatKeyPair(storedChatId)
            chatKeyStore.saveChatKeyPair(storedChatId, keyPair.publicKey, keyPair.privateKeyRef)
            val localThread = localDataSource.observeThreads().value.firstOrNull { thread -> thread.id == storedChatId }
            if (localThread != null) return storedChatId
            try {
                syncChat(storedChatId, skipKeyRegistration = true)
                localDataSource.updateInvitationStatus(storedChatId, INVITATION_STATUS_ACCEPTED)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // The stored id can still be opened once the next sync succeeds.
            }
            return storedChatId
        }

        val existingSummary = remoteDataSource.getChats().firstOrNull { chat -> chat.type.isSelfChatType() }
        if (existingSummary != null) {
            val keyPair = ensureSelfChatKeyPair(existingSummary.id)
            rememberSelfChat(existingSummary.id, keyPair)
            syncChat(existingSummary.id, seedMessages = existingSummary.seedMessages, skipKeyRegistration = true)
            localDataSource.updateInvitationStatus(existingSummary.id, INVITATION_STATUS_ACCEPTED)
            return existingSummary.id
        }

        val keyPair = ensureSelfChatKeyPair()
        val createdChatId = when (val result = remoteDataSource.createSelfChat(keyPair.publicKey)) {
            is RemoteCreateSelfChatResult.Created -> result.chat.id
            RemoteCreateSelfChatResult.AlreadyExists -> (
                remoteDataSource.getChats().firstOrNull { chat -> chat.type.isSelfChatType() }
                    ?: error(
                        chatLocalized(
                            en = "Self chat already exists but was not returned by the chat list.",
                            ru = "Self-чат уже существует, но не вернулся в списке чатов.",
                        ),
                    )
            ).id
        }
        rememberSelfChat(createdChatId, keyPair)
        syncChat(createdChatId, skipKeyRegistration = true)
        localDataSource.updateInvitationStatus(createdChatId, INVITATION_STATUS_ACCEPTED)
        return createdChatId
    }

    private suspend fun ensureSelfChatKeyPair(chatId: String? = null): GeneratedKeyPair {
        val publicKey = chatKeyStore.selfChatPublicKey()
            ?: chatId?.let { chatKeyStore.chatPublicKey(it) }
        val privateKeyRef = chatKeyStore.selfChatPrivateKeyRef()
            ?: chatId?.let { chatKeyStore.chatPrivateKeyRef(it) }
        if (!publicKey.isNullOrBlank() && privateKeyRef != null) {
            chatKeyStore.saveSelfChatKeyPair(publicKey, privateKeyRef)
            return GeneratedKeyPair(publicKey = publicKey, privateKeyRef = privateKeyRef)
        }

        val keyPair = generateChatKeyPair()
        chatKeyStore.saveSelfChatKeyPair(keyPair.publicKey, keyPair.privateKeyRef)
        return keyPair
    }

    private suspend fun rememberSelfChat(chatId: String, keyPair: GeneratedKeyPair? = null) {
        chatKeyStore.saveSelfChatId(chatId)
        val savedKeyPair = keyPair ?: run {
            val publicKey = chatKeyStore.selfChatPublicKey()
            val privateKeyRef = chatKeyStore.selfChatPrivateKeyRef()
            if (!publicKey.isNullOrBlank() && privateKeyRef != null) {
                GeneratedKeyPair(publicKey = publicKey, privateKeyRef = privateKeyRef)
            } else {
                null
            }
        }
        if (savedKeyPair != null) {
            chatKeyStore.saveChatKeyPair(chatId, savedKeyPair.publicKey, savedKeyPair.privateKeyRef)
        }
    }

    private suspend fun syncChats() {
        val currentUserId = chatKeyStore.currentUserId() ?: return
        val chatSummaries = remoteDataSource.getChats()
        chatSummaries.firstOrNull { chat -> chat.type.isSelfChatType() }?.let { chat ->
            rememberSelfChat(chat.id)
        }
        val existingChatIds = localDataSource.observeThreads().value.map(LocalChatThread::id).toSet()
        val listedChatIds = chatSummaries.map(RemoteChatSummary::id).toSet()
        val seedMessages = chatSummaries.flatMap(RemoteChatSummary::seedMessages)

        val threads = chatSummaries.map { chat ->
            buildLocalThreadFromSummary(
                summary = chat,
                currentUserId = currentUserId,
            )
        }

        localDataSource.removeChatsExcept(listedChatIds)
        (existingChatIds - listedChatIds).forEach { removedChatId ->
            chatKeyStore.clearChatState(removedChatId)
        }
        localDataSource.upsertThreads(threads)
        handleServiceMessages(seedMessages)
        if (lastPollTimestamp == null) {
            updateLastPollTimestamp(
                seedMessages.mapNotNull { message ->
                    message.createdAt.takeIf { timestamp -> timestamp.isNotBlank() }
                },
            )
        }
    }

    private suspend fun syncChatParticipants(chatId: String) {
        val currentUserId = requireCurrentUserId()
        val chatInfo = remoteDataSource.getChatInfo(chatId)
        saveChatParticipants(chatId, chatInfo.users, currentUserId)
    }

    private suspend fun syncChat(
        chatId: String,
        seedMessages: List<RemoteMessage> = emptyList(),
        skipKeyRegistration: Boolean = false,
    ) {
        val currentUserId = requireCurrentUserId()
        val chatInfo = remoteDataSource.getChatInfo(chatId)
        saveChatParticipants(chatId, chatInfo.users, currentUserId)
        if (chatInfo.type.isSelfChatType()) {
            rememberSelfChat(chatInfo.id)
        }
        localDataSource.upsertThreads(
            listOf(
                buildLocalThread(
                    chatInfo = chatInfo,
                    currentUserId = currentUserId,
                    seedMessages = seedMessages,
                ),
            ),
        )
        handleServiceMessages(seedMessages)
        if (!skipKeyRegistration) {
            if (chatInfo.type.isSelfChatType()) {
                localDataSource.updateInvitationStatus(chatId, INVITATION_STATUS_ACCEPTED)
            } else {
                val thread = localDataSource.observeThreads().value.firstOrNull { it.id == chatId }
                val currentUserHasNoKey = chatInfo.users
                    .firstOrNull { it.userId == currentUserId }
                    ?.publicKey.isNullOrBlank()
                val isPending = thread?.invitationStatus == INVITATION_STATUS_PENDING ||
                    currentUserHasNoKey
                if (isPending) {
                    localDataSource.updateInvitationStatus(chatId, INVITATION_STATUS_PENDING)
                } else {
                    val keyReady = ensureChatKeyRegistered(chatId)
                    localDataSource.updateInvitationStatus(
                        chatId = chatId,
                        status = if (keyReady) INVITATION_STATUS_ACCEPTED else INVITATION_STATUS_PENDING,
                    )
                }
            }
        }
        if (lastPollTimestamp == null) {
            updateLastPollTimestamp(seedMessages.mapNotNull { message -> message.createdAt.takeIf(String::isNotBlank) })
        }
    }

    private suspend fun syncUnknownChats(
        chatIds: Set<String>,
        messagesByChatId: Map<String, List<RemoteMessage>>,
    ): Set<String> {
        val syncedChatIds = mutableSetOf<String>()
        for (chatId in chatIds) {
            try {
                syncChat(chatId, skipKeyRegistration = true)
                val syncedThread = localDataSource.observeThreads().value.firstOrNull { thread -> thread.id == chatId }
                if (syncedThread?.typeRaw?.toChatType() == ChatType.Self) {
                    localDataSource.updateInvitationStatus(chatId, INVITATION_STATUS_ACCEPTED)
                    syncedChatIds += chatId
                    continue
                }
                val shouldRegisterKey = messagesByChatId[chatId]
                    .orEmpty()
                    .any { message -> !message.isServiceMessage() }
                if (shouldRegisterKey) {
                    val keyReady = ensureChatKeyRegistered(chatId)
                    localDataSource.updateInvitationStatus(
                        chatId = chatId,
                        status = if (keyReady) INVITATION_STATUS_ACCEPTED else INVITATION_STATUS_PENDING,
                    )
                } else {
                    localDataSource.updateInvitationStatus(chatId, INVITATION_STATUS_PENDING)
                }
                syncedChatIds += chatId
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Chat sync may fail transiently; polling will retry on the next iteration.
            }
        }
        return syncedChatIds
    }

    private suspend fun buildLocalThread(
        chatInfo: RemoteChatInfo,
        currentUserId: String,
        seedMessages: List<RemoteMessage>,
    ): LocalChatThread {
        val existingThread = localDataSource.observeThreads().value.firstOrNull { thread -> thread.id == chatInfo.id }
        val existingMessageIds = existingThread?.messages.orEmpty()
            .mapTo(mutableSetOf(), LocalChatMessage::id)
        val normalizedSeedMessages = normalizeMessagesOldestFirst(seedMessages)
        val seedMessagesStartPosition = existingThread?.lastMessagePosition?.plus(1) ?: 1L
        val savedTitle = chatPreferencesDataSource.getChatTitle(chatInfo.id)
        val resolvedTitle = resolveChatTitle(
            type = chatInfo.type,
            rawTitle = savedTitle ?: chatInfo.title,
            users = chatInfo.users,
            currentUserId = currentUserId,
            fallbackTitle = existingThread?.title.orEmpty(),
        )

        return LocalChatThread(
            id = chatInfo.id,
            title = resolvedTitle,
            subtitle = buildSubtitle(chatInfo.type, chatInfo.users),
            typeRaw = chatInfo.type,
            avatarInitials = buildAvatarInitials(resolvedTitle),
            avatarAccent = buildAvatarAccent(chatInfo.id),
            unreadCount = existingThread?.unreadCount ?: 0,
            messages = normalizedSeedMessages
                .filter { message -> message.id !in existingMessageIds }
                .mapIndexed { index, message ->
                message.toLocal(
                    currentUserId = currentUserId,
                    position = seedMessagesStartPosition + index,
                )
            },
            invitationStatus = if (chatInfo.type.isSelfChatType()) {
                INVITATION_STATUS_ACCEPTED
            } else {
                existingThread?.invitationStatus ?: INVITATION_STATUS_NONE
            },
        )
    }

    private suspend fun buildLocalThreadFromSummary(
        summary: RemoteChatSummary,
        currentUserId: String,
    ): LocalChatThread {
        val existingThread = localDataSource.observeThreads().value.firstOrNull { thread -> thread.id == summary.id }
        val existingMessageIds = existingThread?.messages.orEmpty()
            .mapTo(mutableSetOf(), LocalChatMessage::id)
        val normalizedSeedMessages = normalizeMessagesOldestFirst(summary.seedMessages)
        val seedMessagesStartPosition = existingThread?.lastMessagePosition?.plus(1) ?: 1L
        val savedTitle = chatPreferencesDataSource.getChatTitle(summary.id)
        val resolvedTitle = resolveChatTitle(
            type = summary.type,
            rawTitle = savedTitle ?: summary.title,
            users = emptyList(),
            currentUserId = currentUserId,
            fallbackTitle = existingThread?.title.orEmpty(),
        )

        return LocalChatThread(
            id = summary.id,
            title = resolvedTitle,
            subtitle = existingThread?.subtitle ?: buildSubtitle(summary.type, emptyList()),
            typeRaw = summary.type,
            avatarInitials = buildAvatarInitials(resolvedTitle),
            avatarAccent = buildAvatarAccent(summary.id),
            unreadCount = existingThread?.unreadCount ?: 0,
            messages = normalizedSeedMessages
                .filter { message -> message.id !in existingMessageIds }
                .mapIndexed { index, message ->
                    message.toLocal(
                        currentUserId = currentUserId,
                        position = seedMessagesStartPosition + index,
                    )
                },
            invitationStatus = if (summary.type.isSelfChatType()) {
                INVITATION_STATUS_ACCEPTED
            } else {
                existingThread?.invitationStatus ?: INVITATION_STATUS_NONE
            },
        )
    }

    private suspend fun saveChatParticipants(chatId: String, users: List<RemoteChatUser>, currentUserId: String) {
        chatKeyStore.saveParticipants(
            chatId = chatId,
            participants = users.map { user ->
                val savedNickname = if (user.userId != currentUserId) {
                    chatPreferencesDataSource.getUserNickname(user.userId)
                } else {
                    null
                }
                ChatParticipantKey(
                    userId = user.userId,
                    displayName = when {
                        user.userId == currentUserId -> currentUserDisplayName()
                        savedNickname != null -> savedNickname
                        else -> user.userId
                    },
                    publicKey = user.publicKey,
                    isCurrentUser = user.userId == currentUserId,
                )
            },
        )
    }

    private suspend fun resolveChatTitle(
        type: String,
        rawTitle: String,
        users: List<RemoteChatUser>,
        currentUserId: String,
        fallbackTitle: String,
    ): String {
        if (type.toChatType() == ChatType.Self) {
            return SELF_CHAT_TITLE
        }
        if (type.toChatType() != ChatType.Personal) {
            return rawTitle.ifBlank { fallbackTitle }
        }

        val titleFromUserId = rawTitle
            .takeIf(String::isNotBlank)
            ?.let { title -> chatPreferencesDataSource.getUserNickname(title) }
        if (titleFromUserId != null) return titleFromUserId

        val titleFromParticipants = buildPersonalChatTitle(
            type = type,
            users = users,
            currentUserId = currentUserId,
            chatPreferencesDataSource = chatPreferencesDataSource,
        )
        return rawTitle.ifBlank { titleFromParticipants.ifBlank { fallbackTitle } }
    }

    private suspend fun removeLocalChat(chatId: String) {
        deletePrivateKeyRef(chatKeyStore.chatPrivateKeyRef(chatId))
        val remainingChatIds = localDataSource.observeThreads().value
            .map(LocalChatThread::id)
            .filterNot { it == chatId }
            .toSet()
        localDataSource.removeChatsExcept(remainingChatIds)
        chatKeyStore.clearChatState(chatId)
    }

    private suspend fun deleteStoredPrivateKeys(chatIds: Iterable<String>) {
        val privateKeyRefs = buildList {
            chatKeyStore.currentUserPrivateKeyRef()?.let(::add)
            chatKeyStore.selfChatPrivateKeyRef()?.let(::add)
            chatIds
                .distinct()
                .forEach { chatId -> chatKeyStore.chatPrivateKeyRef(chatId)?.let(::add) }
            runCatching { chatKeyStore.exportSnapshot() }
                .getOrNull()
                ?.let { snapshot ->
                    PrivateKeyRef.deserializeOrNull(snapshot.currentUserPrivateKeyRef)?.let(::add)
                    snapshot.selfChatPrivateKeyRef?.let { privateKeyRef ->
                        PrivateKeyRef.deserializeOrNull(privateKeyRef)?.let(::add)
                    }
                    snapshot.chatKeys.forEach { keyPair ->
                        PrivateKeyRef.deserializeOrNull(keyPair.privateKeyRef)?.let(::add)
                    }
                }
        }
        privateKeyRefs
            .distinct()
            .forEach { privateKeyRef -> deletePrivateKeyRef(privateKeyRef) }
    }

    private suspend fun deletePrivateKeyRef(privateKeyRef: PrivateKeyRef?) {
        if (privateKeyRef == null) return
        try {
            encryptionService.deletePrivateKey(privateKeyRef)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Local state cleanup should continue even if the OS key was already removed.
        }
    }

    private fun PrivateKeyRef.Companion.deserializeOrNull(value: String): PrivateKeyRef? {
        return runCatching { deserialize(value) }.getOrNull()
    }

    private suspend fun applyPolledMessages(result: RemotePolledMessages) {
        if (result.messages.isEmpty()) {
            updateLastPollTimestamp(result.timestamp)
            return
        }

        val messagesByChatId = result.messages.groupBy(RemoteMessage::chatId)
        val affectedChatIds = messagesByChatId.keys
        val knownChatIds = localDataSource.observeThreads().value.map(LocalChatThread::id).toSet()
        syncUnknownChats(
            chatIds = affectedChatIds - knownChatIds,
            messagesByChatId = messagesByChatId,
        )

        val currentUserId = requireCurrentUserId()
        val openedChatId = chatPreferencesDataSource.currentOpenedChatId()
        val refreshedChatIds = localDataSource.observeThreads().value.map(LocalChatThread::id).toSet()
        val applicableMessages = result.messages.filter { message -> message.chatId in refreshedChatIds }
        handleServiceMessages(applicableMessages)

        val chatIdsNeedingParticipantRefresh = mutableSetOf<String>()
        applicableMessages
            .groupBy(RemoteMessage::chatId)
            .forEach { (chatId, messages) ->
                val existingMessageIds = localDataSource.observeThreads().value
                    .firstOrNull { thread -> thread.id == chatId }
                    ?.messages
                    ?.mapTo(mutableSetOf(), LocalChatMessage::id)
                    .orEmpty()
                if (hasMissingKeysForUsers(chatId, messages.map(RemoteMessage::fromUserId).filter { it != currentUserId }.toSet())) {
                    chatIdsNeedingParticipantRefresh += chatId
                }

                val nextPosition = localDataSource.nextMessagePosition(chatId)
                val orderedMessages = normalizeMessagesOldestFirst(messages)
                localDataSource.appendMessages(
                    chatId = chatId,
                    messages = orderedMessages.mapIndexed { index, message ->
                        message.toLocal(
                            currentUserId = currentUserId,
                            position = nextPosition + index,
                        )
                    },
                )
                val unreadIncrement = if (openedChatId == chatId) {
                    0
                } else {
                    orderedMessages.count { message ->
                        message.id !in existingMessageIds &&
                            !message.isServiceMessage() &&
                            message.fromUserId != currentUserId
                    }
                }
                if (unreadIncrement > 0) {
                    localDataSource.incrementUnreadCount(chatId, unreadIncrement)
                }
            }
        refreshParticipantsSafely(chatIdsNeedingParticipantRefresh)
        if (affectedChatIds.any { chatId -> chatId !in refreshedChatIds }) {
            return
        }
        updateLastPollTimestamp(
            listOfNotNull(result.timestamp) + result.messages.mapNotNull { message ->
                message.createdAt.takeIf(String::isNotBlank)
            },
        )
    }

    private suspend fun handleServiceMessages(messages: List<RemoteMessage>) {
        val serviceMessages = messages.filter { message ->
            message.isServiceMessage()
        }
        for (message in serviceMessages) {
            when (message.chunks.firstOrNull()?.trim().orEmpty()) {
                SERVICE_EVENT_USER_ADDED -> {
                    handleUserAdded(
                        chatId = message.chatId,
                        addedUserId = decodeServiceMessageData(message)?.userID,
                    )
                }
                SERVICE_EVENT_PUBLIC_KEY_PROVIDED -> {
                    handlePublicKeyProvided(
                        chatId = message.chatId,
                        userId = decodeServiceMessageData(message)?.userID,
                    )
                }
                SERVICE_EVENT_USER_NICKNAME_PROVIDED,
                SERVICE_EVENT_NICKNAME_PROVIDED -> {
                    handleUserNicknameProvided(message)
                }
                SERVICE_EVENT_ACCOUNT_KEY_SYNC -> {
                    handleAccountKeySync(message)
                }
                SERVICE_EVENT_ACCOUNT_KEY_SYNC_REQUEST -> {
                    handleAccountKeySyncRequest(message)
                }
            }
        }
    }

    private suspend fun publishChatKeySyncSafely(chatId: String) {
        try {
            publishChatKeySync(chatId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // The next chat list sync or explicit request can retry key synchronization.
        }
    }

    private suspend fun publishChatKeySync(chatId: String) {
        val thread = localDataSource.observeThreads().value.firstOrNull { it.id == chatId }
        if (thread?.typeRaw?.toChatType() == ChatType.Self) return

        val publicKey = chatKeyStore.chatPublicKey(chatId)?.takeIf(String::isNotBlank) ?: return
        val privateKeyRef = chatKeyStore.chatPrivateKeyRef(chatId) ?: return
        val exportedPrivateKeyRef = encryptionService.exportPrivateKey(privateKeyRef).serialize()
        val data = AccountKeySyncServiceData(
            eventId = newServiceEventId(),
            originDeviceId = requireDeviceId(),
            chatId = chatId,
            chatType = thread?.typeRaw.orEmpty(),
            publicKey = publicKey,
            privateKeyRef = exportedPrivateKeyRef,
            createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )
        sendAccountServiceMessage(SERVICE_EVENT_ACCOUNT_KEY_SYNC, json.encodeToString(data))
    }

    private suspend fun requestChatKeySyncSafely(chatId: String) {
        try {
            requestChatKeySync(chatId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // A later sync pass can request the key again.
        }
    }

    private suspend fun requestChatKeySync(chatId: String) {
        val thread = localDataSource.observeThreads().value.firstOrNull { it.id == chatId }
        if (thread?.typeRaw?.toChatType() == ChatType.Self) return
        val data = AccountKeySyncRequestServiceData(
            eventId = newServiceEventId(),
            originDeviceId = requireDeviceId(),
            chatId = chatId,
            createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )
        sendAccountServiceMessage(SERVICE_EVENT_ACCOUNT_KEY_SYNC_REQUEST, json.encodeToString(data))
    }

    private suspend fun sendAccountServiceMessage(eventType: String, payloadJson: String) {
        val selfChatId = ensureSelfChatInternal()
        val currentUserId = requireCurrentUserId()
        val selfPublicKey = chatKeyStore.selfChatPublicKey()?.takeIf(String::isNotBlank)
            ?: chatKeyStore.chatPublicKey(selfChatId)?.takeIf(String::isNotBlank)
            ?: return
        val encryptedDataChunks = encryptionService.encryptToChunks(payloadJson, selfPublicKey)
        remoteDataSource.sendServiceMessage(
            chatId = selfChatId,
            payloads = listOf(
                RemoteSendPayload(
                    recipientId = currentUserId,
                    chunks = listOf(eventType) + encryptedDataChunks,
                ),
            ),
        )
    }

    private suspend fun handleAccountKeySync(message: RemoteMessage) {
        val data = decodeAccountServiceMessageData<AccountKeySyncServiceData>(message) ?: return
        if (data.originDeviceId == requireDeviceId()) return
        if (data.chatId.isBlank() || data.publicKey.isBlank() || data.privateKeyRef.isBlank()) return
        if (chatKeyStore.chatPrivateKeyRef(data.chatId) != null) return

        val exportedPrivateKeyRef = runCatching { PrivateKeyRef.deserialize(data.privateKeyRef) }
            .getOrNull() as? PrivateKeyRef.Exported ?: return
        if (!isValidKeyPair(data.publicKey, exportedPrivateKeyRef)) return

        val currentUserId = requireCurrentUserId()
        val chatInfo = runCatching { remoteDataSource.getChatInfo(data.chatId) }.getOrNull() ?: return
        val serverPublicKey = chatInfo.users
            .firstOrNull { user -> user.userId == currentUserId }
            ?.publicKey
            ?.takeIf(String::isNotBlank)
            ?: return
        if (serverPublicKey != data.publicKey) return

        val importedPrivateKeyRef = encryptionService.importPrivateKey(
            publicKey = data.publicKey,
            privateKey = exportedPrivateKeyRef,
        )
        try {
            chatKeyStore.saveChatKeyPair(data.chatId, data.publicKey, importedPrivateKeyRef)
            syncChat(data.chatId, skipKeyRegistration = true)
            localDataSource.updateInvitationStatus(data.chatId, INVITATION_STATUS_ACCEPTED)
        } catch (error: Throwable) {
            runCatching { encryptionService.deletePrivateKey(importedPrivateKeyRef) }
            throw error
        }
    }

    private suspend fun handleAccountKeySyncRequest(message: RemoteMessage) {
        val data = decodeAccountServiceMessageData<AccountKeySyncRequestServiceData>(message) ?: return
        if (data.originDeviceId == requireDeviceId()) return
        if (data.chatId.isBlank()) return
        if (chatKeyStore.chatPrivateKeyRef(data.chatId) == null) return
        publishChatKeySyncSafely(data.chatId)
    }

    private suspend inline fun <reified T> decodeAccountServiceMessageData(message: RemoteMessage): T? {
        if (!isSelfChatMessage(message.chatId)) return null
        val payloadChunks = message.chunks.drop(1)
        if (payloadChunks.isEmpty()) return null
        val decryptedPayload = chatMessageCipher.decryptIncomingBody(
            chatId = message.chatId,
            chunks = payloadChunks,
            isEncrypted = true,
        ) ?: return null
        if (decryptedPayload.isBlank()) return null
        return runCatching { json.decodeFromString<T>(decryptedPayload) }.getOrNull()
    }

    private suspend fun isSelfChatMessage(chatId: String): Boolean {
        if (chatId == chatKeyStore.selfChatId()) return true
        val thread = localDataSource.observeThreads().value.firstOrNull { localThread -> localThread.id == chatId }
        if (thread?.typeRaw?.toChatType() != ChatType.Self) return false

        rememberSelfChat(chatId)
        return true
    }

    private suspend fun isValidKeyPair(publicKey: String, privateKeyRef: PrivateKeyRef): Boolean {
        val challenge = "mayday-key-check-${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong()}"
        return runCatching {
            encryptionService.decrypt(encryptionService.encrypt(challenge, publicKey), privateKeyRef) == challenge
        }.getOrDefault(false)
    }

    private suspend fun requireDeviceId(): String {
        chatKeyStore.deviceId()?.takeIf(String::isNotBlank)?.let { return it }
        val deviceId = "device-${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong()}"
        chatKeyStore.saveDeviceId(deviceId)
        return deviceId
    }

    private fun newServiceEventId(): String {
        return "event-${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong()}"
    }

    private fun newLocalEchoMessageId(): String {
        return "local-${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong()}"
    }

    private suspend fun decodeServiceMessageData(message: RemoteMessage): ServiceMessageData? {
        val payloadChunks = message.chunks.drop(1)
        if (payloadChunks.isEmpty()) return null

        val decryptedPayload = chatMessageCipher.decryptIncomingBody(
            chatId = message.chatId,
            chunks = payloadChunks,
            isEncrypted = true,
        ) ?: return null
        if (decryptedPayload.isBlank()) return null

        return runCatching {
            json.decodeFromString<ServiceMessageData>(decryptedPayload)
        }.getOrNull()
    }

    private suspend fun handleUserAdded(chatId: String, addedUserId: String?) {
        val currentUserId = chatKeyStore.currentUserId() ?: return
        if (addedUserId == null || addedUserId == currentUserId) {
            val existingThread = localDataSource.observeThreads().value.firstOrNull { it.id == chatId }
            if (existingThread == null) {
                syncChat(chatId, skipKeyRegistration = true)
            }
            localDataSource.updateInvitationStatus(chatId, INVITATION_STATUS_PENDING)
        } else {
            syncChat(chatId)
        }
    }

    private fun initializePaginationFromLoadedMessages(chatId: String) {
        val initialOffset = loadedMessageCount(chatId)
        updatePaginationState(chatId) { state ->
            state.copy(
                nextOffset = maxOf(state.nextOffset, initialOffset),
                hasMore = true,
                isLoading = false,
            )
        }
    }

    private suspend fun hasParticipantsWithMissingKeys(chatId: String): Boolean {
        val participants = chatKeyStore.participantsFor(chatId)
        return participants.isEmpty() || participants.any { it.publicKey.isBlank() }
    }

    private suspend fun hasMissingKeysForUsers(chatId: String, userIds: Set<String>): Boolean {
        if (userIds.isEmpty()) return false

        val participantsById = chatKeyStore.participantsFor(chatId).associateBy(ChatParticipantKey::userId)
        return userIds.any { userId ->
            participantsById[userId]?.publicKey.isNullOrBlank()
        }
    }

    private suspend fun refreshParticipantsSafely(chatIds: Set<String>) {
        for (chatId in chatIds) {
            try {
                syncChatParticipants(chatId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Keep processing messages even when participant refresh fails.
            }
        }
    }

    private suspend fun handleUserNicknameProvided(message: RemoteMessage) {
        val payloadChunks = message.chunks.drop(1)
        if (payloadChunks.isEmpty()) return

        val decryptedPayload = chatMessageCipher.decryptIncomingBody(
            chatId = message.chatId,
            chunks = payloadChunks,
            isEncrypted = true,
        ) ?: return
        if (decryptedPayload.isBlank()) return

        val data = runCatching {
            json.decodeFromString<NicknameProvidedServiceData>(decryptedPayload)
        }.getOrNull() ?: return

        if (data.nickname.isNotBlank()) {
            rememberUserNickname(data.userID, data.nickname)
        }
    }

    private suspend fun handleHistoricalNicknameMessages(messages: List<RemoteMessage>) {
        messages
            .filter(RemoteMessage::isNicknameServiceMessage)
            .forEach { message -> handleUserNicknameProvided(message) }
    }

    private suspend fun rememberUserNickname(userId: String, nickname: String) {
        val normalizedNickname = nickname.trim()
        if (userId.isBlank() || normalizedNickname.isBlank()) return

        chatPreferencesDataSource.saveUserNickname(userId, normalizedNickname)
        localDataSource.observeThreads().value.forEach { thread ->
            updateParticipantDisplayName(thread.id, userId, normalizedNickname)
            updatePersonalChatTitle(thread, userId, normalizedNickname)
        }
        nicknameVersion.value = nicknameVersion.value + 1
    }

    private suspend fun updateParticipantDisplayName(chatId: String, userId: String, nickname: String) {
        val participants = chatKeyStore.participantsFor(chatId)
        if (participants.none { participant -> participant.userId == userId && !participant.isCurrentUser }) return

        val updated = participants.map { participant ->
            if (participant.userId == userId && !participant.isCurrentUser) participant.copy(displayName = nickname)
            else participant
        }
        chatKeyStore.saveParticipants(chatId, updated)
    }

    private suspend fun updatePersonalChatTitle(thread: LocalChatThread, userId: String, nickname: String) {
        if (thread.typeRaw.toChatType() != ChatType.Personal) return

        val participants = chatKeyStore.participantsFor(thread.id)
        val isOtherParticipant = participants.any { participant ->
            participant.userId == userId && !participant.isCurrentUser
        }
        if (!isOtherParticipant && thread.title != userId) return

        localDataSource.updateChatTitle(thread.id, nickname)
        chatPreferencesDataSource.saveChatTitle(thread.id, nickname)
    }

    private suspend fun sendNicknameProvidedServiceMessage(
        chatId: String,
        nickname: String,
        recipientUserId: String? = null,
    ): Boolean {
        val currentUserId = requireCurrentUserId()
        val data = NicknameProvidedServiceData(userID = currentUserId, nickname = nickname)
        val dataJson = json.encodeToString(data)

        prepareChatParticipantsForSending(chatId)
        val participants = chatKeyStore.participantsFor(chatId).let { all ->
            val recipients = all.filter { participant ->
                !participant.isCurrentUser && participant.userId != currentUserId
            }
            if (recipientUserId != null) {
                recipients.filter { participant -> participant.userId == recipientUserId }
            } else {
                recipients
            }
        }

        val payloads = participants.map { participant ->
            if (participant.publicKey.isBlank()) {
                error(
                    chatLocalized(
                        en = "Public key is missing for nickname recipient ${participant.userId}.",
                        ru = "Не найден публичный ключ получателя никнейма ${participant.userId}.",
                    ),
                )
            }
            val eventTypeChunk = SERVICE_EVENT_USER_NICKNAME_PROVIDED
            val encryptedDataChunks = encryptionService.encryptToChunks(dataJson, participant.publicKey)
            RemoteSendPayload(
                recipientId = participant.userId,
                chunks = listOf(eventTypeChunk) + encryptedDataChunks,
            )
        }
        if (payloads.isEmpty()) return false

        remoteDataSource.sendServiceMessage(chatId, payloads)
        return true
    }

    private suspend fun handlePublicKeyProvided(chatId: String, userId: String?) {
        if (userId.isNullOrBlank()) {
            syncChatParticipants(chatId)
            return
        }
        val participants = chatKeyStore.participantsFor(chatId)
        val participant = participants.firstOrNull { it.userId == userId }
        val isNewKey = participant == null || participant.publicKey.isBlank()

        syncChatParticipants(chatId)

        if (isNewKey) {
            val nickname = chatPreferencesDataSource.getNickname()
            if (nickname.isNotBlank()) {
                try {
                    sendNicknameProvidedServiceMessage(chatId, nickname, recipientUserId = userId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    // The next nickname update or key-provided event can retry this service message.
                }
            }
        }
    }

    private fun loadedMessageCount(chatId: String): Int {
        return localDataSource.observeThreads().value
            .firstOrNull { thread -> thread.id == chatId }
            ?.messages
            ?.size
            ?: 0
    }

    private fun normalizeMessagesOldestFirst(messages: List<RemoteMessage>): List<RemoteMessage> {
        if (messages.size < 2) return messages

        return messages.withIndex()
            .sortedWith { left, right ->
                val chronology = compareMessageChronology(left.value, right.value)
                if (chronology != 0) chronology else left.index.compareTo(right.index)
            }
            .map(IndexedValue<RemoteMessage>::value)
    }

    private suspend fun requireCurrentUserId(): String {
        return checkNotNull(chatKeyStore.currentUserId()) {
            chatLocalized(
                en = "Current user id is not initialized.",
                ru = "ID текущего пользователя не инициализирован.",
            )
        }
    }

    private suspend fun requireTransportPublicKey(): String {
        return checkNotNull(chatKeyStore.currentUserPublicKey()) {
            chatLocalized(
                en = "Current user public key is not initialized.",
                ru = "Публичный ключ текущего пользователя не инициализирован.",
            )
        }
    }

    private suspend fun requireTransportPrivateKeyRef() = checkNotNull(chatKeyStore.currentUserPrivateKeyRef()) {
        chatLocalized(
            en = "Current user private key is not initialized.",
            ru = "Приватный ключ текущего пользователя не инициализирован.",
        )
    }

    private fun buildMessagePayload(
        plainText: String,
        attachments: List<PreparedChatAttachment>,
    ): String {
        return json.encodeToString(
            OutgoingMessagePayload(
                messageText = plainText,
                attachments = attachments.map { prepared ->
                    OutgoingAttachmentPayload(
                        id = prepared.encryption.uuid,
                        type = prepared.attachment.kind.toMessagePayloadType(),
                        key = prepared.encryption.key,
                        name = prepared.attachment.name,
                        mimeType = prepared.attachment.mimeType,
                        sizeBytes = prepared.attachment.sizeBytes,
                        chunkSizeBytes = prepared.encryption.chunkSizeBytes,
                        partIds = prepared.encryption.parts.map(ChatAttachmentPart::id),
                        parts = prepared.encryption.parts.map { part ->
                            AttachmentPartPayload(
                                id = part.id,
                                index = part.index,
                                sizeBytes = part.sizeBytes,
                                key = part.key,
                            )
                        },
                    )
                },
            ),
        )
    }

    private suspend fun restoreLastPollTimestamp() {
        if (lastPollTimestamp == null) {
            lastPollTimestamp = chatPreferencesDataSource.getLastPollTimestamp()
        }
    }

    private suspend fun updateLastPollTimestamp(candidates: Iterable<String>) {
        val latest = latestTimestamp(listOfNotNull(lastPollTimestamp) + candidates)
        if (latest != null && latest != lastPollTimestamp) {
            lastPollTimestamp = latest
            chatPreferencesDataSource.saveLastPollTimestamp(latest)
        }
    }

    private suspend fun updateLastPollTimestamp(candidate: String?) {
        updateLastPollTimestamp(listOfNotNull(candidate))
    }

    private fun isSameOrNewerTimestamp(candidate: String, boundary: String): Boolean {
        if (candidate.isBlank()) return false
        return candidate == boundary || latestTimestamp(listOf(candidate, boundary)) == candidate
    }

    private suspend fun generateChatKeyPair(): GeneratedKeyPair {
        return encryptionService.generateKeyPair()
    }

    private fun updatePaginationState(chatId: String, update: (MessagePaginationState) -> MessagePaginationState) {
        paginationStates.value = paginationStates.value.toMutableMap().apply {
            this[chatId] = update(this[chatId] ?: MessagePaginationState())
        }
    }

    private suspend fun throttleSuccessfulPollIteration(startedAt: TimeMark) {
        val elapsed = startedAt.elapsedNow()
        val remainingDelay = MIN_SUCCESSFUL_POLL_INTERVAL_MS.milliseconds - elapsed
        if (remainingDelay.isPositive()) {
            delay(remainingDelay)
        }
    }

    private fun String.isSelfChatType(): Boolean = toChatType() == ChatType.Self

    private companion object {
        private const val CHAT_SYNC_RETRY_DELAY_MS = 5_000L
        private const val MIN_SUCCESSFUL_POLL_INTERVAL_MS = 750L
        private const val KNOWN_CHATS_CATCH_UP_LOOP_INTERVAL_MS = 1_000L
        private const val KNOWN_CHATS_CATCH_UP_INTERVAL_MS = 5_000L
        private const val KNOWN_CHATS_CATCH_UP_LIMIT = 20
        private const val DEFAULT_MESSAGES_PAGE_SIZE = 20
        private const val MIN_POLL_TIMESTAMP = "1970-01-01T00:00:00Z"
        private const val SERVICE_EVENT_USER_ADDED = "user_added"
        private const val SERVICE_EVENT_PUBLIC_KEY_PROVIDED = "public_key_provided"
        private const val SERVICE_EVENT_USER_NICKNAME_PROVIDED = "user_nickname_provided"
        private const val SERVICE_EVENT_NICKNAME_PROVIDED = "nickname_provided"
        private const val SERVICE_EVENT_ACCOUNT_KEY_SYNC = "account_key_sync_v1"
        private const val SERVICE_EVENT_ACCOUNT_KEY_SYNC_REQUEST = "account_key_sync_request_v1"
        private const val INVITATION_STATUS_NONE = "none"
        private const val INVITATION_STATUS_PENDING = "pending"
        private const val INVITATION_STATUS_ACCEPTED = "accepted"
        private const val ATTACHMENT_UPLOAD_CHUNK_BYTES = 16 * 1024 * 1024
        private const val SELF_CHAT_TITLE = "Saved Messages"
    }
}
