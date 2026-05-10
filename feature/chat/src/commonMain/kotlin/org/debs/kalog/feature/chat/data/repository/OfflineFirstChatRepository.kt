package org.debs.kalog.feature.chat.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.debs.kalog.core.crypto.EncryptionService
import org.debs.kalog.core.crypto.GeneratedKeyPair
import org.debs.kalog.feature.chat.data.CURRENT_USER_DISPLAY_NAME
import org.debs.kalog.feature.chat.data.crypto.AttachmentEncryptionKey
import org.debs.kalog.feature.chat.data.crypto.ChatKeyStore
import org.debs.kalog.feature.chat.data.crypto.ChatMessageCipher
import org.debs.kalog.feature.chat.data.crypto.ChatParticipantKey
import org.debs.kalog.feature.chat.data.local.ChatLocalDataSource
import org.debs.kalog.feature.chat.data.local.LocalChatMessage
import org.debs.kalog.feature.chat.data.local.LocalChatThread
import org.debs.kalog.feature.chat.data.preferences.ChatPreferencesDataSource
import org.debs.kalog.feature.chat.data.remote.ChatRemoteDataSource
import org.debs.kalog.feature.chat.data.remote.NicknameProvidedServiceData
import org.debs.kalog.feature.chat.data.remote.RemoteChatInfo
import org.debs.kalog.feature.chat.data.remote.RemoteChatSummary
import org.debs.kalog.feature.chat.data.remote.RemoteChatUser
import org.debs.kalog.feature.chat.data.remote.RemoteMessage
import org.debs.kalog.feature.chat.data.remote.RemotePolledMessages
import org.debs.kalog.feature.chat.data.remote.RemoteSendPayload
import org.debs.kalog.feature.chat.data.remote.ServiceMessageData
import org.debs.kalog.feature.chat.domain.model.AvatarAccent
import org.debs.kalog.feature.chat.domain.model.AvatarSpec
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentEncryptionSpec
import org.debs.kalog.feature.chat.domain.model.ChatMessage
import org.debs.kalog.feature.chat.domain.model.ChatParticipant
import org.debs.kalog.feature.chat.domain.model.ChatThread
import org.debs.kalog.feature.chat.domain.model.ChatType
import org.debs.kalog.feature.chat.domain.model.DeliveryStatus
import org.debs.kalog.feature.chat.domain.model.InvitationStatus
import org.debs.kalog.feature.chat.domain.model.PreparedChatAttachment
import org.debs.kalog.feature.chat.domain.repository.ChatRepository
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class OfflineFirstChatRepository(
    private val localDataSource: ChatLocalDataSource,
    private val remoteDataSource: ChatRemoteDataSource,
    private val chatMessageCipher: ChatMessageCipher,
    private val chatKeyStore: ChatKeyStore,
    private val encryptionService: EncryptionService,
    private val chatPreferencesDataSource: ChatPreferencesDataSource,
    private val json: Json,
) : ChatRepository {
    private val sessionMutex = Mutex()
    private val paginationStates = MutableStateFlow<Map<String, MessagePaginationState>>(emptyMap())
    private var sessionStarted = false
    internal var lastPollTimestamp: String? = null
    private val syncLoopJob = MutableStateFlow<Job?>(null)
    private val syncEnabled = MutableStateFlow(true)
    private val nicknameVersion = MutableStateFlow(0)

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
                    throttleSuccessfulPollIteration(pollStartedAt)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    delay(CHAT_SYNC_RETRY_DELAY_MS)
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
        paginationStates.value = emptyMap()
        localDataSource.clearAll()
        chatPreferencesDataSource.clearAll()
        chatKeyStore.clearAll()
        nicknameVersion.value = nicknameVersion.value + 1
    }

    override suspend fun closeChat() {
        chatPreferencesDataSource.clearLastOpenedChatId()
    }

    override fun observeChats() = combine(localDataSource.observeThreads(), nicknameVersion) { threads, _ ->
        threads.map { thread ->
            thread.toDomain(
                chatMessageCipher = chatMessageCipher,
                chatPreferencesDataSource = chatPreferencesDataSource,
            )
        }
    }

    override fun observeChat(chatId: String) = combine(
        localDataSource.observeThreads(),
        paginationStates,
        nicknameVersion,
    ) { threads, states, _ ->
        val thread = threads.firstOrNull { it.id == chatId } ?: return@combine null
        val paginationState = states[chatId] ?: MessagePaginationState()
        thread.toDomain(
            hasMoreMessages = paginationState.hasMore,
            isLoadingMoreMessages = paginationState.isLoading,
            chatMessageCipher = chatMessageCipher,
            chatPreferencesDataSource = chatPreferencesDataSource,
        )
    }

    override suspend fun openChat(chatId: String) {
        startSession()
        ensureUserSession()
        localDataSource.markChatOpened(chatId)
        chatPreferencesDataSource.saveLastOpenedChatId(chatId)
        syncChat(chatId)
        initializePaginationFromLoadedMessages(chatId)
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

        remoteDataSource.sendMessage(
            chatId = chatId,
            payloads = payloads.map { payload ->
                RemoteSendPayload(
                    recipientId = payload.recipientId,
                    chunks = payload.chunks,
                )
            },
        )

        localDataSource.markChatOpened(chatId)
    }

    override suspend fun prepareAttachment(chatId: String, attachment: ChatAttachment): PreparedChatAttachment {
        startSession()
        ensureUserSession()

        val generatedKey = encryptionService.generateAttachmentKey()
        val storedKey = AttachmentEncryptionKey(
            id = attachment.id,
            chatId = chatId,
            algorithm = generatedKey.algorithmLabel,
            sizeBits = generatedKey.sizeBits,
            key = generatedKey.key,
        )
        chatKeyStore.saveAttachmentEncryptionKey(chatId, storedKey)

        val preparedAttachment = attachment.copy(encryptionKeyId = storedKey.id)
        return PreparedChatAttachment(
            attachment = preparedAttachment,
            encryption = ChatAttachmentEncryptionSpec(
                uuid = storedKey.id,
                key = storedKey.key,
                algorithm = storedKey.algorithm,
                sizeBits = storedKey.sizeBits,
            ),
        )
    }

    override suspend fun createDirectChat(targetUserId: String): String {
        startSession()
        ensureUserSession()
        val chatKeyPair = generateChatKeyPair()

        val chat = remoteDataSource.createDirectChat(
            targetUserId = targetUserId,
            publicKey = chatKeyPair.publicKey,
        )
        chatKeyStore.saveChatKeyPair(chat.id, chatKeyPair.publicKey, chatKeyPair.privateKey)
        syncChat(chat.id)
        localDataSource.updateInvitationStatus(chat.id, INVITATION_STATUS_ACCEPTED)
        return chat.id
    }

    override suspend fun createGroupChat(publicKey: String?): String {
        startSession()
        ensureUserSession()
        val generatedChatKeyPair = if (publicKey == null) generateChatKeyPair() else null

        val chat = remoteDataSource.createGroupChat(generatedChatKeyPair?.publicKey ?: publicKey.orEmpty())
        generatedChatKeyPair?.let { keyPair ->
            chatKeyStore.saveChatKeyPair(chat.id, keyPair.publicKey, keyPair.privateKey)
        }
        syncChat(chat.id)
        localDataSource.updateInvitationStatus(chat.id, INVITATION_STATUS_ACCEPTED)
        return chat.id
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

        ensureChatKeyRegistered(chatId)
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

    private suspend fun ensureChatKeyRegistered(chatId: String) {
        val currentUserId = chatKeyStore.currentUserId() ?: return
        val participants = chatKeyStore.participantsFor(chatId)
        val currentUserParticipant = participants.firstOrNull { it.userId == currentUserId }
        val storedPublicKey = chatKeyStore.chatPublicKey(chatId)
        val storedPrivateKey = chatKeyStore.chatPrivateKey(chatId)
        if (
            currentUserParticipant != null &&
            currentUserParticipant.publicKey.isNotBlank() &&
            storedPublicKey != null &&
            storedPrivateKey != null &&
            currentUserParticipant.publicKey == storedPublicKey
        ) {
            return
        }

        val keyPair = if (
            storedPublicKey != null &&
            storedPrivateKey != null &&
            (currentUserParticipant?.publicKey.isNullOrBlank() || currentUserParticipant.publicKey == storedPublicKey)
        ) {
            GeneratedKeyPair(
                publicKey = storedPublicKey,
                privateKey = storedPrivateKey,
            )
        } else {
            generateChatKeyPair().also { generatedKeyPair ->
                chatKeyStore.saveChatKeyPair(chatId, generatedKeyPair.publicKey, generatedKeyPair.privateKey)
            }
        }

        remoteDataSource.setGroupChatPublicKey(chatId, keyPair.publicKey)
        syncChatParticipants(chatId)
    }

    private suspend fun prepareChatParticipantsForSending(chatId: String) {
        ensureChatKeyRegistered(chatId)
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
            privateKey = requireTransportPrivateKey(),
        )
        chatKeyStore.saveServerPublicKey(session.serverPublicKey)
    }

    private suspend fun ensureTransportKeys(): Boolean {
        val publicKey = chatKeyStore.currentUserPublicKey()
        val privateKey = chatKeyStore.currentUserPrivateKey()
        if (publicKey != null && privateKey != null) return false

        val keyPair = encryptionService.generateKeyPair()
        chatKeyStore.saveCurrentUserKeys(
            userId = chatKeyStore.currentUserId(),
            publicKey = keyPair.publicKey,
            privateKey = keyPair.privateKey,
        )
        return true
    }

    private suspend fun syncChats() {
        val currentUserId = chatKeyStore.currentUserId() ?: return
        val chatSummaries = remoteDataSource.getChats()
        val existingChatIds = localDataSource.observeThreads().value.map(LocalChatThread::id).toSet()
        val listedChatIds = chatSummaries.map(RemoteChatSummary::id).toSet()

        handleHistoricalNicknameMessages(chatSummaries.flatMap(RemoteChatSummary::seedMessages))
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
        if (lastPollTimestamp == null) {
            updateLastPollTimestamp(
                chatSummaries.flatMap { chat ->
                    chat.seedMessages.mapNotNull { message ->
                        message.createdAt.takeIf { timestamp -> timestamp.isNotBlank() }
                    }
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
        handleHistoricalNicknameMessages(seedMessages)
        localDataSource.upsertThreads(
            listOf(
                buildLocalThread(
                    chatInfo = chatInfo,
                    currentUserId = currentUserId,
                    seedMessages = seedMessages,
                ),
            ),
        )
        if (!skipKeyRegistration) {
            val thread = localDataSource.observeThreads().value.firstOrNull { it.id == chatId }
            val currentUserHasNoKey = chatInfo.users
                .firstOrNull { it.userId == currentUserId }
                ?.publicKey.isNullOrBlank()
            val isPending = thread?.invitationStatus == INVITATION_STATUS_PENDING ||
                currentUserHasNoKey
            if (isPending) {
                localDataSource.updateInvitationStatus(chatId, INVITATION_STATUS_PENDING)
            } else {
                ensureChatKeyRegistered(chatId)
                localDataSource.updateInvitationStatus(chatId, INVITATION_STATUS_ACCEPTED)
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
                val shouldRegisterKey = messagesByChatId[chatId]
                    .orEmpty()
                    .any { message -> !message.isServiceMessage() }
                if (shouldRegisterKey) {
                    ensureChatKeyRegistered(chatId)
                    localDataSource.updateInvitationStatus(chatId, INVITATION_STATUS_ACCEPTED)
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
                        user.userId == currentUserId -> CURRENT_USER_DISPLAY_NAME
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
        val remainingChatIds = localDataSource.observeThreads().value
            .map(LocalChatThread::id)
            .filterNot { it == chatId }
            .toSet()
        localDataSource.removeChatsExcept(remainingChatIds)
        chatKeyStore.clearChatState(chatId)
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
            }
        }
    }

    private suspend fun decodeServiceMessageData(message: RemoteMessage): ServiceMessageData? {
        val payloadChunks = message.chunks.drop(1)
        if (payloadChunks.isEmpty()) return null

        val decryptedPayload = chatMessageCipher.decryptIncoming(
            chatId = message.chatId,
            chunks = payloadChunks,
            isEncrypted = true,
        )
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

        val decryptedPayload = chatMessageCipher.decryptIncoming(
            chatId = message.chatId,
            chunks = payloadChunks,
            isEncrypted = true,
        )
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
                error("Public key is missing for nickname recipient ${participant.userId}.")
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
            "Current user id is not initialized."
        }
    }

    private suspend fun requireTransportPublicKey(): String {
        return checkNotNull(chatKeyStore.currentUserPublicKey()) {
            "Current user public key is not initialized."
        }
    }

    private suspend fun requireTransportPrivateKey(): String {
        return checkNotNull(chatKeyStore.currentUserPrivateKey()) {
            "Current user private key is not initialized."
        }
    }

    private fun buildMessagePayload(
        plainText: String,
        attachments: List<PreparedChatAttachment>,
    ): String {
        if (attachments.isEmpty()) return plainText

        return json.encodeToString(
            OutgoingMessagePayload(
                message = plainText,
                attachments = attachments.map { prepared ->
                    OutgoingAttachmentPayload(
                        uuid = prepared.encryption.uuid,
                        key = prepared.encryption.key,
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

    private companion object {
        private const val CHAT_SYNC_RETRY_DELAY_MS = 5_000L
        private const val MIN_SUCCESSFUL_POLL_INTERVAL_MS = 750L
        private const val DEFAULT_MESSAGES_PAGE_SIZE = 20
        private const val MIN_POLL_TIMESTAMP = "1970-01-01T00:00:00Z"
        private const val SERVICE_EVENT_USER_ADDED = "user_added"
        private const val SERVICE_EVENT_PUBLIC_KEY_PROVIDED = "public_key_provided"
        private const val SERVICE_EVENT_USER_NICKNAME_PROVIDED = "user_nickname_provided"
        private const val SERVICE_EVENT_NICKNAME_PROVIDED = "nickname_provided"
        private const val INVITATION_STATUS_PENDING = "pending"
        private const val INVITATION_STATUS_ACCEPTED = "accepted"
    }
}

private data class MessagePaginationState(
    val nextOffset: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
)

@Serializable
private data class OutgoingMessagePayload(
    @SerialName("message") val message: String,
    @SerialName("attachments") val attachments: List<OutgoingAttachmentPayload>,
)

@Serializable
private data class OutgoingAttachmentPayload(
    @SerialName("uuid") val uuid: String,
    @SerialName("key") val key: String,
)

private suspend fun LocalChatThread.toDomain(
    hasMoreMessages: Boolean = false,
    isLoadingMoreMessages: Boolean = false,
    chatMessageCipher: ChatMessageCipher,
    chatPreferencesDataSource: ChatPreferencesDataSource? = null,
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
            message.toDomain(chatMessageCipher, chatPreferencesDataSource)
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
        chatMessageCipher.decryptIncoming(
            chatId = chatId,
            chunks = encryptedChunks,
            isEncrypted = true,
        )
    }
    val resolvedSender = if (isMine == true || sender == CURRENT_USER_DISPLAY_NAME) {
        sender.orEmpty()
    } else {
        val userId = fromUserId ?: sender.orEmpty()
        chatPreferencesDataSource?.getUserNickname(userId) ?: sender.orEmpty()
    }
    return ChatMessage.User(
        id = id,
        sender = resolvedSender,
        body = decryptedBody,
        timestamp = timestamp.toDisplayTimestamp(),
        isMine = isMine == true,
        deliveryStatus = deliveryStatus ?: DeliveryStatus.Sent,
    )
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
        chatMessageCipher.decryptIncoming(
            chatId = chatId,
            chunks = payloadChunks,
            isEncrypted = true,
        ).ifBlank { null }
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
            if (displayName != null) "$displayName joined the chat"
            else "A user joined the chat"
        }

        "user_nickname_provided",
        "nickname_provided" -> {
            val data = payload?.let {
                runCatching { Json.decodeFromString<NicknameProvidedServiceData>(it) }.getOrNull()
            }
            if (data != null) "${data.nickname} changed their nickname"
            else "A user changed their nickname"
        }

        else -> if (debugMode) "$eventType${payload?.let { " $it" }.orEmpty()}" else null
    }
}

private fun RemoteMessage.toLocal(
    currentUserId: String,
    position: Long,
): LocalChatMessage {
    val isMine = fromUserId == currentUserId
    val isService = isServiceMessage()
    return LocalChatMessage(
        id = id,
        chatId = chatId,
        sender = if (isMine) CURRENT_USER_DISPLAY_NAME else fromUserId,
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

private fun RemoteMessage.isServiceMessage(): Boolean {
    return type.equals("service", ignoreCase = true) || type.equals("system", ignoreCase = true)
}

private fun RemoteMessage.isNicknameServiceMessage(): Boolean {
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
        Month.JANUARY -> "янв"
        Month.FEBRUARY -> "фев"
        Month.MARCH -> "мар"
        Month.APRIL -> "апр"
        Month.MAY -> "мая"
        Month.JUNE -> "июн"
        Month.JULY -> "июл"
        Month.AUGUST -> "авг"
        Month.SEPTEMBER -> "сен"
        Month.OCTOBER -> "окт"
        Month.NOVEMBER -> "ноя"
        Month.DECEMBER -> "дек"
    }
}

private fun LocalDateTime.timeString(): String {
    return "${hour.twoDigits()}:${minute.twoDigits()}"
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

private fun latestTimestamp(candidates: Iterable<String>): String? {
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

private fun compareMessageChronology(left: RemoteMessage, right: RemoteMessage): Int {
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

private fun buildAvatarInitials(title: String): String {
    return title
        .split(" ")
        .filter(String::isNotBlank)
        .take(2)
        .joinToString(separator = "") { it.take(1).uppercase() }
        .ifBlank { "--" }
}

private fun buildAvatarAccent(seed: String): AvatarAccent {
    val accents = AvatarAccent.entries
    val normalizedHash = seed.hashCode().toLong().let { if (it < 0) -it else it }
    return accents[(normalizedHash % accents.size).toInt()]
}

private suspend fun buildPersonalChatTitle(
    type: String,
    users: List<RemoteChatUser>,
    currentUserId: String,
    chatPreferencesDataSource: ChatPreferencesDataSource,
): String {
    if (type.toChatType() != ChatType.Personal) return ""
    val otherUserId = users.firstOrNull { it.userId != currentUserId }?.userId ?: return ""
    return chatPreferencesDataSource.getUserNickname(otherUserId) ?: otherUserId
}

private fun buildSubtitle(type: String, users: List<RemoteChatUser>): String {
    return when (type.toChatType()) {
        ChatType.Group -> "${users.size.coerceAtLeast(1)} members"
        ChatType.Personal -> "Personal chat"
        ChatType.Unknown -> when {
            users.size > 2 -> "${users.size} members"
            type.isNotBlank() -> type
            else -> ""
        }
    }
}

private fun String.toChatType(): ChatType {
    return when {
        contains("group", ignoreCase = true) -> ChatType.Group
        isBlank() -> ChatType.Unknown
        else -> ChatType.Personal
    }
}

private val SHORT_TIME_REGEX = Regex("""^\d{2}:\d{2}$""")
private val ISO_TIMESTAMP_REGEX = Regex("""(\d{4})-(\d{2})-(\d{2})[T\s].*?(\d{2}):(\d{2})""")
