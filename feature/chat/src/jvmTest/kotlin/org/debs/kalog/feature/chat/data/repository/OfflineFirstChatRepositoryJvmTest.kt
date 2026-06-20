package org.debs.kalog.feature.chat.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.debs.kalog.core.crypto.EncryptionService
import org.debs.kalog.core.crypto.GeneratedKeyPair
import org.debs.kalog.core.crypto.GeneratedAttachmentKey
import org.debs.kalog.feature.chat.data.cache.CachedChatAttachment
import org.debs.kalog.feature.chat.data.cache.ChatAttachmentFileCache
import org.debs.kalog.feature.chat.data.cache.EncryptedCachedAttachmentPart
import org.debs.kalog.feature.chat.data.cache.NoOpChatAttachmentFileCache
import org.debs.kalog.feature.chat.data.crypto.ChatKeyStore
import org.debs.kalog.feature.chat.data.crypto.ChatMessageCipher
import org.debs.kalog.feature.chat.data.crypto.ChatKeySnapshot
import org.debs.kalog.feature.chat.data.crypto.ChatParticipantKey
import org.debs.kalog.feature.chat.data.crypto.StoredChatKeyPair
import org.debs.kalog.feature.chat.data.crypto.StoredChatParticipants
import org.debs.kalog.core.crypto.PrivateKeyRef
import org.debs.kalog.feature.chat.data.local.ChatLocalDataSource
import org.debs.kalog.feature.chat.data.local.LocalChatMessage
import org.debs.kalog.feature.chat.data.local.LocalChatThread
import org.debs.kalog.feature.chat.data.preferences.AppThemeMode
import org.debs.kalog.feature.chat.data.preferences.ChatPreferencesDataSource
import org.debs.kalog.feature.chat.data.remote.*
import org.debs.kalog.feature.chat.domain.model.AvatarAccent
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentKind
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentLoadState
import org.debs.kalog.feature.chat.domain.model.ChatMessage
import org.debs.kalog.feature.chat.domain.model.ChatType
import org.debs.kalog.feature.chat.domain.model.DeliveryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineFirstChatRepositoryJvmTest {
    @Test
    fun reversesOlderPagesBeforePrepending() = runBlocking {
        val chatId = "chat-1"
        val localDataSource = FakeChatLocalDataSource()
        val keyStore = FakeChatKeyStore()
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "public-key",
                privateKey = "private-key",
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = emptyList(),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = mapOf(
                0 to (100 downTo 51).map { index -> remoteMessage(chatId, index, createdAt = messageTimestamp(index)) },
                50 to (50 downTo 1).map { index -> remoteMessage(chatId, index, createdAt = messageTimestamp(index)) },
            ),
        )
        val repository = OfflineFirstChatRepository(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            chatMessageCipher = ChatMessageCipher(encryptionService, keyStore),
            chatKeyStore = keyStore,
            encryptionService = encryptionService,
            chatPreferencesDataSource = FakeChatPreferencesDataSource(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )

        repository.openChat(chatId)
        repository.loadMoreMessages(chatId)
        repository.loadMoreMessages(chatId)

        val messages = localDataSource.observeThreads().value
            .first { it.id == chatId }
            .messages
            .map(LocalChatMessage::id)

        assertEquals((1..100).map(Int::toString), messages)
    }

    @Test
    fun openChat_usesSeedMessagesWithoutRequestingHistory() = runBlocking {
        val chatId = "chat-1"
        val localDataSource = FakeChatLocalDataSource()
        val keyStore = FakeChatKeyStore()
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "public-key",
                privateKey = "private-key",
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = (1..3).map { index ->
                        remoteMessage(chatId, index, createdAt = messageTimestamp(index))
                    },
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        val repository = OfflineFirstChatRepository(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            chatMessageCipher = ChatMessageCipher(encryptionService, keyStore),
            chatKeyStore = keyStore,
            encryptionService = encryptionService,
            chatPreferencesDataSource = FakeChatPreferencesDataSource(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )

        repository.openChat(chatId)

        val chat = repository.observeChat(chatId).filterNotNull().first()
        val chats = repository.observeChats().first()

        assertEquals(listOf("1", "2", "3"), chat.messages.map { message -> message.id })
        assertEquals("3", chats.first { thread -> thread.id == chatId }.lastMessage?.id)
        assertEquals(emptyList(), remoteDataSource.historyCalls)
    }

    @Test
    fun refreshChatMessages_appendsNewerHistoryMessagesWithoutWaitingForPoll() = runBlocking {
        val chatId = "chat-1"
        val localDataSource = FakeChatLocalDataSource()
        val keyStore = FakeChatKeyStore()
        keyStore.saveChatKeyPair(chatId, publicKey = "public-key", privateKey = "private-key")
        val initialMessage = remoteMessage(chatId, 1, createdAt = messageTimestamp(1), fromUserId = "user-2")
        val missedMessage = remoteMessage(chatId, 2, createdAt = messageTimestamp(2), fromUserId = "user-2")
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Personal chat",
                    type = "personal",
                    seedMessages = listOf(initialMessage),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Personal chat",
                    type = "personal",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                        RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
                    ),
                ),
            ),
            historyByOffset = mapOf(
                0 to listOf(missedMessage, initialMessage),
            ),
        )
        val repository = createRepository(
            localDataSource = localDataSource,
            keyStore = keyStore,
            remoteDataSource = remoteDataSource,
        )

        repository.openChat(chatId)
        val refreshed = repository.refreshChatMessages(chatId)

        val messageIds = localDataSource.observeThreads().value
            .first { thread -> thread.id == chatId }
            .messages
            .map(LocalChatMessage::id)

        assertTrue(refreshed)
        assertEquals(listOf("1", "2"), messageIds)
        assertEquals(listOf(0), remoteDataSource.historyCalls)
    }

    @Test
    fun syncLoop_catchesUpKnownChatsWhileLongPollIsStillBlocked() = runBlocking {
        val chatId = "chat-1"
        val localDataSource = FakeChatLocalDataSource()
        val keyStore = FakeChatKeyStore()
        keyStore.saveChatKeyPair(chatId, publicKey = "public-key", privateKey = "private-key")
        val initialMessage = remoteMessage(chatId, 1, createdAt = messageTimestamp(1), fromUserId = "user-2")
        val missedMessage = remoteMessage(chatId, 2, createdAt = messageTimestamp(2), fromUserId = "user-2")
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Personal chat",
                    type = "personal",
                    seedMessages = listOf(initialMessage),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Personal chat",
                    type = "personal",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                        RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
                    ),
                ),
            ),
            historyByOffset = mapOf(
                0 to listOf(missedMessage, initialMessage),
            ),
        )
        remoteDataSource.blockNextPoll()
        val repository = createRepository(
            localDataSource = localDataSource,
            keyStore = keyStore,
            remoteDataSource = remoteDataSource,
        )

        val syncJob = launch { repository.runSyncLoop() }
        remoteDataSource.awaitBlockedPollStarted()

        withTimeout(1_000) {
            localDataSource.observeThreads()
                .map { threads -> threads.firstOrNull { thread -> thread.id == chatId }?.messages?.map(LocalChatMessage::id) }
                .first { ids -> ids == listOf("1", "2") }
        }
        syncJob.cancel()
    }

    @Test
    fun observeChat_showsUndecryptablePlaceholderInsteadOfRawCiphertext() = runBlocking {
        val chatId = "chat-1"
        val ciphertext = "ciphertext-that-must-not-render"
        val localDataSource = FakeChatLocalDataSource(
            initialThreads = listOf(
                LocalChatThread(
                    id = chatId,
                    title = "Secure chat",
                    subtitle = "Personal chat",
                    typeRaw = "personal",
                    avatarInitials = "SC",
                    avatarAccent = AvatarAccent.Sky,
                    unreadCount = 0,
                    messages = listOf(
                        LocalChatMessage(
                            id = "message-1",
                            chatId = chatId,
                            sender = "user-2",
                            encryptedChunks = listOf(ciphertext),
                            timestamp = "2026-03-21T10:00:00Z",
                            isService = false,
                            isMine = false,
                            deliveryStatus = DeliveryStatus.Read,
                            position = 1,
                            messageType = "default",
                            fromUserId = "user-2",
                            toUserId = "user-1",
                        ),
                    ),
                ),
            ),
        )
        val repository = createRepository(
            localDataSource = localDataSource,
            keyStore = FakeChatKeyStore(),
            remoteDataSource = FakeChatRemoteDataSource(
                startSession = RemoteStartSession(
                    userId = "user-1",
                    serverPublicKey = "server-key",
                ),
                chats = emptyList(),
                chatInfoById = emptyMap(),
                historyByOffset = emptyMap(),
            ),
        )

        val message = repository.observeChat(chatId)
            .filterNotNull()
            .first()
            .messages
            .single() as ChatMessage.User

        assertEquals("Не удалось расшифровать сообщение.", message.body)
        assertEquals(emptyList(), message.attachments)
        assertFalse(message.body.contains(ciphertext))
    }

    @Test
    fun reRegistersSessionWhenClientKeysWereRegeneratedLocally() = runBlocking {
        val keyStore = FakeChatKeyStore(
            currentUserId = "user-1",
            currentUserPublicKey = "stale-public-key",
            currentUserPrivateKey = null,
            serverPublicKey = "server-key",
        )
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "new-public-key",
                privateKey = "new-private-key",
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = emptyList(),
            chatInfoById = emptyMap(),
            historyByOffset = emptyMap(),
        )
        val repository = OfflineFirstChatRepository(
            localDataSource = FakeChatLocalDataSource(),
            remoteDataSource = remoteDataSource,
            chatMessageCipher = ChatMessageCipher(encryptionService, keyStore),
            chatKeyStore = keyStore,
            encryptionService = encryptionService,
            chatPreferencesDataSource = FakeChatPreferencesDataSource(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )

        repository.startSession()

        assertEquals(1, remoteDataSource.startCalls)
        assertEquals("new-public-key", remoteDataSource.lastStartPublicKey)
        assertEquals("new-public-key", keyStore.currentUserPublicKey())
        assertEquals("new-private-key", keyStore.currentUserPrivateKey())
    }

    @Test
    fun createDirectChat_usesPerChatKeyPairAndKeepsTransportKeyPairSeparate() = runBlocking {
        val chatId = "chat-1"
        val keyStore = FakeChatKeyStore()
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "fallback-public-key",
                privateKey = "fallback-private-key",
            ),
            generatedKeyPairs = mutableListOf(
                GeneratedKeyPair(
                    publicKey = "transport-public-key",
                    privateKey = "transport-private-key",
                ),
                GeneratedKeyPair(
                    publicKey = "chat-public-key",
                    privateKey = "chat-private-key",
                ),
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = emptyList(),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Personal chat",
                    type = "personal",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "chat-public-key"),
                        RemoteChatUser(userId = "user-2", publicKey = ""),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
            createDirectChatResponse = RemoteChatCreated(
                id = chatId,
                title = "Personal chat",
                type = "personal",
            ),
        )
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            keyStore = keyStore,
            encryptionService = encryptionService,
        )

        val createdChatId = repository.createDirectChat("user-2")

        assertEquals(chatId, createdChatId)
        assertEquals("transport-public-key", remoteDataSource.lastStartPublicKey)
        assertEquals("chat-public-key", remoteDataSource.lastCreateDirectChatPublicKey)
        assertEquals("transport-public-key", keyStore.currentUserPublicKey())
        assertEquals("transport-private-key", keyStore.currentUserPrivateKey())
        assertEquals("chat-public-key", keyStore.chatPublicKey(chatId))
        assertEquals("chat-private-key", keyStore.chatPrivateKey(chatId))
    }

    @Test
    fun createGroupChat_usesPerChatKeyPairAndKeepsTransportKeyPairSeparate() = runBlocking {
        val chatId = "chat-1"
        val keyStore = FakeChatKeyStore()
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "fallback-public-key",
                privateKey = "fallback-private-key",
            ),
            generatedKeyPairs = mutableListOf(
                GeneratedKeyPair(
                    publicKey = "transport-public-key",
                    privateKey = "transport-private-key",
                ),
                GeneratedKeyPair(
                    publicKey = "group-chat-public-key",
                    privateKey = "group-chat-private-key",
                ),
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = emptyList(),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "group-chat-public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
            createGroupChatResponse = RemoteChatCreated(
                id = chatId,
                title = "Group chat",
                type = "group",
            ),
        )
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            keyStore = keyStore,
            encryptionService = encryptionService,
        )

        val createdChatId = repository.createGroupChat()

        assertEquals(chatId, createdChatId)
        assertEquals("transport-public-key", remoteDataSource.lastStartPublicKey)
        assertEquals("group-chat-public-key", remoteDataSource.lastCreateGroupChatPublicKey)
        assertEquals("transport-public-key", keyStore.currentUserPublicKey())
        assertEquals("transport-private-key", keyStore.currentUserPrivateKey())
        assertEquals("group-chat-public-key", keyStore.chatPublicKey(chatId))
        assertEquals("group-chat-private-key", keyStore.chatPrivateKey(chatId))
    }

    @Test
    fun ensureSelfChat_createsChatAndStoresSyncKeyPair() = runBlocking {
        val selfChatId = "self-chat"
        val keyStore = FakeChatKeyStore(
            currentUserId = "user-1",
            currentUserPublicKey = "transport-public-key",
            currentUserPrivateKey = "transport-private-key",
            serverPublicKey = "server-key",
        )
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "self-public-key",
                privateKey = "self-private-key",
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = emptyList(),
            chatInfoById = mapOf(
                selfChatId to RemoteChatInfo(
                    id = selfChatId,
                    title = "backend self title",
                    type = "self",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "self-public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
            createSelfChatResult = RemoteCreateSelfChatResult.Created(
                RemoteChatCreated(
                    id = selfChatId,
                    title = "backend self title",
                    type = "self",
                ),
            ),
        )
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            keyStore = keyStore,
            encryptionService = encryptionService,
        )

        val chatId = repository.ensureSelfChat()
        val selfThread = repository.observeChats().first().single { chat -> chat.id == selfChatId }

        assertEquals(selfChatId, chatId)
        assertEquals("self-public-key", remoteDataSource.lastCreateSelfChatPublicKey)
        assertEquals(selfChatId, keyStore.selfChatId())
        assertEquals("self-public-key", keyStore.selfChatPublicKey())
        assertEquals(PrivateKeyRef.Exported("self-private-key"), keyStore.selfChatPrivateKeyRef())
        assertEquals("self-public-key", keyStore.chatPublicKey(selfChatId))
        assertEquals("self-private-key", keyStore.chatPrivateKey(selfChatId))
        assertEquals(ChatType.Self, selfThread.type)
        assertEquals("Saved Messages", selfThread.title)
    }

    @Test
    fun openChat_requestsKeySyncWhenServerHasCurrentUserPublicKeyButLocalPrivateKeyIsMissing() = runBlocking {
        val chatId = "chat-1"
        val selfChatId = "self-chat"
        val keyStore = FakeChatKeyStore(
            currentUserId = "user-1",
            currentUserPublicKey = "transport-public-key",
            currentUserPrivateKey = "transport-private-key",
            serverPublicKey = "server-key",
            autoStoreCurrentUserChatKeyFromParticipants = false,
        )
        keyStore.saveSelfChatId(selfChatId)
        keyStore.saveSelfChatKeyPair("self-public-key", PrivateKeyRef.Exported("self-private-key"))
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = emptyList(),
                ),
                RemoteChatSummary(
                    id = selfChatId,
                    title = "Saved Messages",
                    type = "self",
                    seedMessages = emptyList(),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "server-chat-public-key"),
                        RemoteChatUser(userId = "user-2", publicKey = "user-2-public-key"),
                    ),
                ),
                selfChatId to RemoteChatInfo(
                    id = selfChatId,
                    title = "Saved Messages",
                    type = "self",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "self-public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            keyStore = keyStore,
            encryptionService = FakeEncryptionService(
                generatedKeyPair = GeneratedKeyPair(publicKey = "unused-public-key", privateKey = "unused-private-key"),
            ),
        )

        repository.openChat(chatId)

        assertEquals(null, keyStore.chatPrivateKey(chatId))
        assertTrue(remoteDataSource.setGroupChatPublicKeyCalls.isEmpty())
        val (_, payloads) = remoteDataSource.sentServiceMessages.single()
        assertEquals("user-1", payloads.single().recipientId)
        assertEquals("account_key_sync_request_v1", payloads.single().chunks.first())
    }

    @Test
    fun syncLoop_registersPerChatKeyForInvitedUserWhenUnknownChatAppears() = runBlocking {
        val chatId = "chat-1"
        val keyStore = FakeChatKeyStore(
            currentUserId = "user-1",
            currentUserPublicKey = "transport-public-key",
            currentUserPrivateKey = "transport-private-key",
            serverPublicKey = "server-key",
        )
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "invited-chat-public-key",
                privateKey = "invited-chat-private-key",
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = emptyList(),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Invited chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = ""),
                        RemoteChatUser(userId = "user-2", publicKey = "user-2-public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
            pollResults = listOf(
                RemotePolledMessages(
                    timestamp = "2026-03-21T10:05:00Z",
                    messages = listOf(
                        remoteMessage(
                            chatId = chatId,
                            index = 1,
                            createdAt = "2026-03-21T10:05:00Z",
                            fromUserId = "user-2",
                            toUserId = "user-1",
                        ),
                    ),
                ),
            ),
        )
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            keyStore = keyStore,
            encryptionService = encryptionService,
        )

        runCatching {
            withTimeout(200) {
                repository.runSyncLoop()
            }
        }

        assertEquals(
            listOf(chatId to "invited-chat-public-key"),
            remoteDataSource.setGroupChatPublicKeyCalls,
        )
        assertEquals("invited-chat-public-key", keyStore.chatPublicKey(chatId))
        assertEquals("invited-chat-private-key", keyStore.chatPrivateKey(chatId))
        assertEquals(
            "invited-chat-public-key",
            keyStore.participantsFor(chatId).first { participant -> participant.userId == "user-1" }.publicKey,
        )
    }

    @Test
    fun syncLoop_doesNotRetryBootstrapChatListWhenInitialSyncFails() = runBlocking {
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = emptyList(),
            chatInfoById = emptyMap(),
            historyByOffset = emptyMap(),
            pollResults = listOf(
                RemotePolledMessages(
                    timestamp = "2026-03-21T10:05:00Z",
                    messages = emptyList(),
                ),
            ),
            getChatsFailuresRemaining = 1,
        )
        val repository = createRepository(remoteDataSource = remoteDataSource)

        runCatching {
            withTimeout(100) {
                repository.runSyncLoop()
            }
        }

        assertEquals(1, remoteDataSource.getChatsCalls)
        assertEquals(MIN_POLL_TIMESTAMP_FOR_TESTS, remoteDataSource.pollSinceCalls.first())
    }

    @Test
    fun clearAllData_cancelsInFlightPollBeforeWipingState() = runBlocking {
        val chatId = "chat-1"
        val localDataSource = FakeChatLocalDataSource()
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(remoteMessage(chatId, 1, createdAt = "2026-03-21T10:00:00Z")),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            localDataSource = localDataSource,
        )

        repository.openChat(chatId)
        remoteDataSource.blockNextPoll()
        val syncJob = launch { repository.runSyncLoop() }
        remoteDataSource.awaitBlockedPollStarted()

        withTimeout(500) {
            repository.clearAllData()
        }
        remoteDataSource.releaseBlockedPoll(
            RemotePolledMessages(
                timestamp = "2026-03-21T10:05:00Z",
                messages = listOf(remoteMessage(chatId, 2, createdAt = "2026-03-21T10:05:00Z")),
            ),
        )

        assertTrue(localDataSource.observeThreads().value.isEmpty())
        syncJob.join()
    }

    @Test
    fun openChatAndSendMessage_workWhenInitialChatListSyncFails() = runBlocking {
        val chatId = "chat-1"
        val keyStore = FakeChatKeyStore()
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "public-key",
                privateKey = "private-key",
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = emptyList(),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
            getChatsFailuresRemaining = 1,
        )
        val repository = OfflineFirstChatRepository(
            localDataSource = FakeChatLocalDataSource(),
            remoteDataSource = remoteDataSource,
            chatMessageCipher = ChatMessageCipher(encryptionService, keyStore),
            chatKeyStore = keyStore,
            encryptionService = encryptionService,
            chatPreferencesDataSource = FakeChatPreferencesDataSource(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )

        repository.openChat(chatId)
        repository.sendMessage(chatId, "hello")

        assertEquals(1, remoteDataSource.getChatsCalls)
        assertEquals(1, remoteDataSource.sendCalls)
    }

    @Test
    fun syncLoop_appendsPolledMessagesAndUpdatesChatFlows() = runBlocking {
        val chatId = "chat-1"
        val localDataSource = FakeChatLocalDataSource()
        val keyStore = FakeChatKeyStore()
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "public-key",
                privateKey = "private-key",
            ),
        )
        val initialTimestamp = "2026-03-21T10:00:00Z"
        val incomingTimestamp = "2026-03-21T10:05:00Z"
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(remoteMessage(chatId, 1, createdAt = initialTimestamp)),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = mapOf(
                0 to listOf(remoteMessage(chatId, 1, createdAt = initialTimestamp)),
            ),
            pollResults = listOf(
                RemotePolledMessages(
                    timestamp = incomingTimestamp,
                    messages = listOf(
                        remoteMessage(chatId, 3, createdAt = incomingTimestamp),
                        remoteMessage(chatId, 2, createdAt = "2026-03-21T10:04:00Z"),
                    ),
                ),
            ),
        )
        val repository = OfflineFirstChatRepository(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            chatMessageCipher = ChatMessageCipher(encryptionService, keyStore),
            chatKeyStore = keyStore,
            encryptionService = encryptionService,
            chatPreferencesDataSource = FakeChatPreferencesDataSource(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )

        repository.openChat(chatId)
        runCatching {
            withTimeout(100) {
                repository.runSyncLoop()
            }
        }

        val chats = repository.observeChats().first()
        val chat = repository.observeChat(chatId).filterNotNull().first()

        assertEquals(listOf(initialTimestamp), remoteDataSource.pollSinceCalls)
        assertEquals(incomingTimestamp, repository.lastPollTimestamp)
        assertEquals("3", chats.first { thread -> thread.id == chatId }.lastMessage?.id)
        assertEquals("3", chat.messages.last().id)
        assertEquals(listOf("1", "2", "3"), localDataSource.observeThreads().value.first().messages.map(LocalChatMessage::id))
    }

    @Test
    fun syncLoop_doesNotOverrideLastMessageWithStaleChatPreview() = runBlocking {
        val chatId = "chat-1"
        val oldTimestamp = "2026-03-21T10:00:00Z"
        val latestTimestamp = "2026-03-21T10:05:00Z"
        val localDataSource = FakeChatLocalDataSource()
        val keyStore = FakeChatKeyStore()
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "public-key",
                privateKey = "private-key",
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(remoteMessage(chatId, 1, createdAt = oldTimestamp)),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
            pollResults = listOf(
                RemotePolledMessages(
                    timestamp = latestTimestamp,
                    messages = emptyList(),
                ),
            ),
        )
        val repository = OfflineFirstChatRepository(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            chatMessageCipher = ChatMessageCipher(encryptionService, keyStore),
            chatKeyStore = keyStore,
            encryptionService = encryptionService,
            chatPreferencesDataSource = FakeChatPreferencesDataSource(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )

        repository.openChat(chatId)
        localDataSource.appendMessages(
            chatId = chatId,
            messages = listOf(
                LocalChatMessage(
                    id = "2",
                    chatId = chatId,
                    sender = "You",
                    encryptedChunks = listOf("chunk-2"),
                    timestamp = latestTimestamp,
                    isService = false,
                    isMine = true,
                    deliveryStatus = org.debs.kalog.feature.chat.domain.model.DeliveryStatus.Sent,
                    position = 2L,
                    messageType = "text",
                    fromUserId = "user-1",
                    toUserId = "user-1",
                ),
            ),
        )
        repository.lastPollTimestamp = null
        runCatching {
            withTimeout(100) {
                repository.runSyncLoop()
            }
        }

        val chat = repository.observeChats().first().first { it.id == chatId }

        assertEquals(listOf("1", "2"), localDataSource.observeThreads().value.first().messages.map(LocalChatMessage::id))
        assertEquals("2", chat.lastMessage?.id)
    }

    @Test
    fun syncLoop_doesNotRefreshChatListForKnownChatsOnEveryPoll() = runBlocking {
        val chatId = "chat-1"
        val initialTimestamp = "2026-03-21T10:00:00Z"
        val incomingTimestamp = "2026-03-21T10:05:00Z"
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(remoteMessage(chatId, 1, createdAt = initialTimestamp)),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
            pollResults = listOf(
                RemotePolledMessages(
                    timestamp = incomingTimestamp,
                    messages = listOf(
                        remoteMessage(chatId, 2, createdAt = incomingTimestamp),
                    ),
                ),
            ),
        )
        val repository = createRepository(remoteDataSource = remoteDataSource)

        repository.openChat(chatId)
        runCatching {
            withTimeout(100) {
                repository.runSyncLoop()
            }
        }

        assertEquals(1, remoteDataSource.getChatsCalls)
    }

    @Test
    fun syncLoop_throttlesSuccessfulEmptyPolls() = runBlocking {
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = emptyList(),
            chatInfoById = emptyMap(),
            historyByOffset = emptyMap(),
            pollResults = listOf(
                RemotePolledMessages(
                    timestamp = "2026-03-21T10:05:00Z",
                    messages = emptyList(),
                ),
                RemotePolledMessages(
                    timestamp = "2026-03-21T10:05:01Z",
                    messages = emptyList(),
                ),
            ),
        )
        val repository = createRepository(remoteDataSource = remoteDataSource)

        runCatching {
            withTimeout(250) {
                repository.runSyncLoop()
            }
        }

        assertEquals(1, remoteDataSource.pollSinceCalls.size)
    }

    @Test
    fun syncLoop_syncsUnknownChatWithoutRefreshingFullChatList() = runBlocking {
        val existingChatId = "chat-1"
        val newChatId = "chat-2"
        val incomingTimestamp = "2026-03-21T10:05:00Z"
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = existingChatId,
                    title = "Existing chat",
                    type = "group",
                    seedMessages = emptyList(),
                ),
            ),
            chatInfoById = mapOf(
                existingChatId to RemoteChatInfo(
                    id = existingChatId,
                    title = "Existing chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
                newChatId to RemoteChatInfo(
                    id = newChatId,
                    title = "New chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
            pollResults = listOf(
                RemotePolledMessages(
                    timestamp = incomingTimestamp,
                    messages = listOf(
                        remoteMessage(newChatId, 1, createdAt = incomingTimestamp),
                    ),
                ),
            ),
        )
        val repository = createRepository(remoteDataSource = remoteDataSource)

        repository.startSession()
        remoteDataSource.updateChats(
            listOf(
                RemoteChatSummary(
                    id = existingChatId,
                    title = "Existing chat",
                    type = "group",
                    seedMessages = emptyList(),
                ),
                RemoteChatSummary(
                    id = newChatId,
                    title = "New chat",
                    type = "group",
                    seedMessages = listOf(remoteMessage(newChatId, 1, createdAt = incomingTimestamp)),
                ),
            ),
        )
        runCatching {
            withTimeout(100) {
                repository.runSyncLoop()
            }
        }

        assertEquals(1, remoteDataSource.getChatsCalls)
        assertEquals(
            setOf(existingChatId, newChatId),
            repository.observeChats().first().map { it.id }.toSet(),
        )
    }

    @Test
    fun syncLoop_keepsUnreadCountForUnopenedChats() = runBlocking {
        val openedChatId = "chat-1"
        val unopenedChatId = "chat-2"
        val repository = createRepository(
            remoteDataSource = FakeChatRemoteDataSource(
                startSession = RemoteStartSession(
                    userId = "user-1",
                    serverPublicKey = "server-key",
                ),
                chats = listOf(
                    RemoteChatSummary(
                        id = openedChatId,
                        title = "Opened chat",
                        type = "group",
                        seedMessages = emptyList(),
                    ),
                    RemoteChatSummary(
                        id = unopenedChatId,
                        title = "Unread chat",
                        type = "group",
                        seedMessages = emptyList(),
                    ),
                ),
                chatInfoById = mapOf(
                    openedChatId to RemoteChatInfo(
                        id = openedChatId,
                        title = "Opened chat",
                        type = "group",
                        users = listOf(
                            RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                            RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
                        ),
                    ),
                    unopenedChatId to RemoteChatInfo(
                        id = unopenedChatId,
                        title = "Unread chat",
                        type = "group",
                        users = listOf(
                            RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                            RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
                        ),
                    ),
                ),
                historyByOffset = emptyMap(),
                pollResults = listOf(
                    RemotePolledMessages(
                        timestamp = "2026-03-21T10:05:00Z",
                        messages = listOf(
                            remoteMessage(
                                chatId = unopenedChatId,
                                index = 1,
                                createdAt = "2026-03-21T10:05:00Z",
                                fromUserId = "user-2",
                                toUserId = "user-1",
                            ),
                        ),
                    ),
                ),
            ),
        )

        repository.openChat(openedChatId)
        runCatching {
            withTimeout(200) {
                repository.runSyncLoop()
            }
        }

        assertEquals(
            1,
            repository.observeChats().first().first { it.id == unopenedChatId }.unreadCount,
        )

        repository.openChat(unopenedChatId)

        assertEquals(
            0,
            repository.observeChats().first().first { it.id == unopenedChatId }.unreadCount,
        )
    }

    @Test
    fun syncLoop_doesNotAdvanceCursorWhenApplyingUnknownChatFails() = runBlocking {
        val existingChatId = "chat-1"
        val missingChatId = "chat-2"
        val initialTimestamp = "2026-03-21T10:00:00Z"
        val failedBatchTimestamp = "2026-03-21T10:05:00Z"
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = existingChatId,
                    title = "Existing chat",
                    type = "group",
                    seedMessages = listOf(remoteMessage(existingChatId, 1, createdAt = initialTimestamp)),
                ),
            ),
            chatInfoById = mapOf(
                existingChatId to RemoteChatInfo(
                    id = existingChatId,
                    title = "Existing chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
            pollResults = List(2) {
                RemotePolledMessages(
                    timestamp = failedBatchTimestamp,
                    messages = listOf(
                        remoteMessage(
                            chatId = missingChatId,
                            index = 2,
                            createdAt = failedBatchTimestamp,
                            fromUserId = "user-2",
                            toUserId = "user-1",
                        ),
                    ),
                )
            },
        )
        val repository = createRepository(remoteDataSource = remoteDataSource)

        repository.openChat(existingChatId)
        repeat(2) {
            runCatching {
                withTimeout(150) {
                    repository.runSyncLoop()
                }
            }
        }

        assertEquals(listOf(initialTimestamp, initialTimestamp), remoteDataSource.pollSinceCalls)
    }

    @Test
    fun startSession_mergesUnseenPreviewMessagesIntoExistingThread() = runBlocking {
        val chatId = "chat-1"
        val existingTimestamp = "2026-03-21T10:00:00Z"
        val previewTimestamp = "2026-03-21T10:05:00Z"
        val localDataSource = FakeChatLocalDataSource(
            initialThreads = listOf(
                LocalChatThread(
                    id = chatId,
                    title = "Group chat",
                    subtitle = "2 members",
                    typeRaw = "group",
                    avatarInitials = "GC",
                    avatarAccent = org.debs.kalog.feature.chat.domain.model.AvatarAccent.Rose,
                    unreadCount = 3,
                    messages = listOf(
                        LocalChatMessage(
                            id = "1",
                            chatId = chatId,
                            sender = "You",
                            encryptedChunks = listOf("chunk-1"),
                            timestamp = existingTimestamp,
                            isService = false,
                            isMine = true,
                            deliveryStatus = org.debs.kalog.feature.chat.domain.model.DeliveryStatus.Sent,
                            position = 1,
                            messageType = "text",
                            fromUserId = "user-1",
                            toUserId = "user-1",
                        ),
                    ),
                ),
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(remoteMessage(chatId, 2, createdAt = previewTimestamp)),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            localDataSource = localDataSource,
        )

        repository.startSession()

        val thread = localDataSource.observeThreads().value.first { it.id == chatId }
        assertEquals(listOf("1", "2"), thread.messages.map(LocalChatMessage::id))
        assertEquals(3, thread.unreadCount)
        assertEquals(previewTimestamp, repository.lastPollTimestamp)
    }

    @Test
    fun sendMessage_appendsLocalEchoWithoutRefreshingHistory() = runBlocking {
        val chatId = "chat-1"
        val localDataSource = FakeChatLocalDataSource()
        val keyStore = FakeChatKeyStore()
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "public-key",
                privateKey = "private-key",
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(remoteMessage(chatId, 1, createdAt = messageTimestamp(1))),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        val repository = OfflineFirstChatRepository(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            chatMessageCipher = ChatMessageCipher(encryptionService, keyStore),
            chatKeyStore = keyStore,
            encryptionService = encryptionService,
            chatPreferencesDataSource = FakeChatPreferencesDataSource(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )

        repository.openChat(chatId)
        repository.sendMessage(chatId, "hello")

        assertEquals(1, remoteDataSource.sendCalls)
        assertEquals(emptyList(), remoteDataSource.historyCalls)
        val messageIds = localDataSource.observeThreads().value.first().messages.map(LocalChatMessage::id)
        assertEquals("1", messageIds.first())
        assertTrue(messageIds.last().startsWith("local-"))
    }

    @Test
    fun refreshChatMessages_replacesMatchingLocalEchoWithServerMessage() = runBlocking {
        val chatId = "chat-1"
        val localDataSource = FakeChatLocalDataSource()
        val keyStore = FakeChatKeyStore()
        val payloadJson = kotlinx.serialization.json.Json.encodeToString(
            OutgoingMessagePayload(messageText = "hello", attachments = emptyList()),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(remoteMessage(chatId, 1, createdAt = messageTimestamp(1))),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = mapOf(
                0 to listOf(
                    remoteMessage(
                        chatId = chatId,
                        index = 2,
                        createdAt = messageTimestamp(2),
                        fromUserId = "user-1",
                        toUserId = "user-1",
                    ).copy(chunks = listOf(payloadJson)),
                ),
            ),
        )
        val repository = createRepository(
            localDataSource = localDataSource,
            keyStore = keyStore,
            remoteDataSource = remoteDataSource,
        )

        repository.openChat(chatId)
        repository.sendMessage(chatId, "hello")
        repository.refreshChatMessages(chatId)

        assertEquals(
            listOf("1", "2"),
            localDataSource.observeThreads().value.first().messages.map(LocalChatMessage::id),
        )
    }

    @Test
    fun sendMessage_refreshesParticipantKeysBeforeEncryptingForInvitedUser() = runBlocking {
        val chatId = "chat-1"
        val initialChatInfo = RemoteChatInfo(
            id = chatId,
            title = "Group chat",
            type = "group",
            users = listOf(
                RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                RemoteChatUser(userId = "user-2", publicKey = ""),
            ),
        )
        val updatedChatInfo = initialChatInfo.copy(
            users = listOf(
                RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
            ),
        )
        val keyStore = FakeChatKeyStore()
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "public-key",
                privateKey = "private-key",
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = emptyList(),
                ),
            ),
            chatInfoById = mapOf(chatId to initialChatInfo),
            historyByOffset = emptyMap(),
        )
        val repository = OfflineFirstChatRepository(
            localDataSource = FakeChatLocalDataSource(),
            remoteDataSource = remoteDataSource,
            chatMessageCipher = ChatMessageCipher(encryptionService, keyStore),
            chatKeyStore = keyStore,
            encryptionService = encryptionService,
            chatPreferencesDataSource = FakeChatPreferencesDataSource(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )

        repository.openChat(chatId)
        remoteDataSource.updateChatInfo(chatId, updatedChatInfo)

        repository.sendMessage(chatId, "hello")

        assertEquals(
            setOf("user-1", "user-2"),
            remoteDataSource.lastSentPayloads.map(RemoteSendPayload::recipientId).toSet(),
        )
        assertEquals(
            "public-key-2",
            keyStore.participantsFor(chatId).first { participant -> participant.userId == "user-2" }.publicKey,
        )
    }

    @Test
    fun sendMessage_keepsImportedChatKeyWhenRemoteCurrentUserKeyDiffers() = runBlocking {
        val chatId = "chat-1"
        val keyStore = FakeChatKeyStore(
            currentUserId = "user-1",
            currentUserPublicKey = "transport-public-key",
            currentUserPrivateKey = "transport-private-key",
            serverPublicKey = "server-key",
        )
        keyStore.saveChatKeyPair(
            chatId = chatId,
            publicKey = "imported-chat-public-key",
            privateKey = "imported-chat-private-key",
        )
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "generated-chat-public-key",
                privateKey = "generated-chat-private-key",
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = emptyList(),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "stale-server-chat-public-key"),
                        RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            keyStore = keyStore,
            encryptionService = encryptionService,
        )

        repository.openChat(chatId)
        repository.sendMessage(chatId, "hello")

        assertEquals("imported-chat-public-key", keyStore.chatPublicKey(chatId))
        assertEquals("imported-chat-private-key", keyStore.chatPrivateKey(chatId))
        assertEquals(listOf(chatId to "imported-chat-public-key"), remoteDataSource.setGroupChatPublicKeyCalls)
        assertEquals(
            setOf("user-1", "user-2"),
            remoteDataSource.lastSentPayloads.map(RemoteSendPayload::recipientId).toSet(),
        )
        assertEquals(
            "imported-chat-public-key",
            keyStore.participantsFor(chatId).first { participant -> participant.userId == "user-1" }.publicKey,
        )
    }

    @Test
    fun sendMessage_failsClosedWhenRecipientEncryptionFails() = runBlocking {
        val chatId = "chat-1"
        val keyStore = FakeChatKeyStore()
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "public-key",
                privateKey = "private-key",
            ),
            failingPublicKeys = setOf("public-key-2"),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = emptyList(),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                        RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        val repository = OfflineFirstChatRepository(
            localDataSource = FakeChatLocalDataSource(),
            remoteDataSource = remoteDataSource,
            chatMessageCipher = ChatMessageCipher(encryptionService, keyStore),
            chatKeyStore = keyStore,
            encryptionService = encryptionService,
            chatPreferencesDataSource = FakeChatPreferencesDataSource(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )

        repository.openChat(chatId)
        val result = runCatching { repository.sendMessage(chatId, "hello") }

        assertTrue(result.isFailure)
        assertEquals(0, remoteDataSource.sendCalls)
        assertEquals(emptyList(), remoteDataSource.lastSentPayloads)
    }

    @Test
    fun prepareAttachment_generatesChaCha20Poly1305KeyForMessagePayload() = runBlocking {
        val chatId = "chat-1"
        val keyStore = FakeChatKeyStore()
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = emptyList(),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            keyStore = keyStore,
        )

        val preparedImage = repository.prepareAttachment(
            chatId = chatId,
            attachment = ChatAttachment(
                id = "attachment-1",
                kind = ChatAttachmentKind.Image,
                name = "photo.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 128_000,
                localUri = "content://gallery/photo.jpg",
                contentBytes = "photo-bytes".encodeToByteArray(),
            ),
        )
        val preparedVoice = repository.prepareAttachment(
            chatId = chatId,
            attachment = ChatAttachment(
                id = "attachment-2",
                kind = ChatAttachmentKind.Voice,
                name = "voice.m4a",
                mimeType = "audio/mp4",
                durationMillis = 7_000,
                localUri = "content://recorder/voice.m4a",
                contentBytes = "voice-bytes".encodeToByteArray(),
            ),
        )

        assertEquals("attachment-1", preparedImage.attachment.encryptionKeyId)
        assertEquals("attachment-1", preparedImage.encryption.uuid)
        assertEquals("chacha20-poly1305-key-1", preparedImage.encryption.key)
        assertEquals("ChaCha20-Poly1305", preparedImage.encryption.algorithm)
        assertEquals(256, preparedImage.encryption.sizeBits)
        assertEquals("attachment-2", preparedVoice.attachment.encryptionKeyId)
        assertEquals("attachment-2", preparedVoice.encryption.uuid)
        assertEquals("chacha20-poly1305-key-2", preparedVoice.encryption.key)
        assertEquals("ChaCha20-Poly1305", preparedVoice.encryption.algorithm)
        assertEquals(256, preparedVoice.encryption.sizeBits)
    }

    @Test
    fun prepareAttachment_uploadsEncryptedBytesWhenContentIsAvailable() = runBlocking {
        val chatId = "chat-1"
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = emptyList(),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        val repository = createRepository(remoteDataSource = remoteDataSource)

        val prepared = repository.prepareAttachment(
            chatId = chatId,
            attachment = ChatAttachment(
                id = "local-file-id",
                kind = ChatAttachmentKind.File,
                name = "report.pdf",
                mimeType = "application/pdf",
                sizeBytes = 11,
                contentBytes = "hello world".encodeToByteArray(),
            ),
        )

        assertEquals("attachment-1", prepared.attachment.id)
        assertEquals(null, prepared.attachment.contentBytes)
        assertEquals(1, remoteDataSource.uploadedAttachments.size)
        val upload = remoteDataSource.uploadedAttachments.single()
        assertEquals("attachment-1", upload.attachmentId)
        assertEquals("upload-token-attachment-1", upload.uploadToken)
        assertEquals("application/pdf", upload.contentType)
        assertEquals(
            "encrypted-with-chacha20-poly1305-key-1:hello world",
            upload.bytes.decodeToString(),
        )
    }

    @Test
    fun prepareAttachment_uploadsLargeLocalFileAsEncryptedChunks() = runBlocking {
        val chatId = "chat-1"
        val chunkSizeBytes = 16 * 1024 * 1024
        val sourceBytes = ByteArray(chunkSizeBytes + 5) { index -> (index % 251).toByte() }
        val attachmentFileCache = FakeChatAttachmentFileCache()
        val sourceFile = attachmentFileCache.put(
            attachmentId = "local-source",
            fileName = "big.bin",
            mimeType = "application/octet-stream",
            bytes = sourceBytes,
        )
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "public-key",
                privateKey = "private-key",
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = emptyList(),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            encryptionService = encryptionService,
            attachmentFileCache = attachmentFileCache,
        )

        val prepared = repository.prepareAttachment(
            chatId = chatId,
            attachment = ChatAttachment(
                id = "local-file-id",
                kind = ChatAttachmentKind.File,
                name = "big.bin",
                mimeType = "application/octet-stream",
                sizeBytes = sourceBytes.size.toLong(),
                localUri = sourceFile.localUri,
                contentBytes = null,
            ),
        )

        assertEquals("attachment-1", prepared.attachment.id)
        assertEquals(listOf("attachment-1", "attachment-2"), prepared.attachment.parts.map { it.id })
        assertEquals(listOf(null, null), prepared.attachment.parts.map { it.key })
        assertEquals(2, remoteDataSource.uploadedAttachments.size)
        assertEquals(
            chunkSizeBytes,
            encryptionService.decryptAttachment(
                remoteDataSource.uploadedAttachments[0].bytes,
                "chacha20-poly1305-key-1",
            ).size,
        )
        assertEquals(
            5,
            encryptionService.decryptAttachment(
                remoteDataSource.uploadedAttachments[1].bytes,
                "chacha20-poly1305-key-1",
            ).size,
        )
    }

    @Test
    fun sendMessage_usesCompactPartIdsForChunkedAttachmentMetadata() = runBlocking {
        val chatId = "chat-1"
        val chunkSizeBytes = 16 * 1024 * 1024
        val sourceBytes = ByteArray(chunkSizeBytes + 1) { index -> (index % 127).toByte() }
        val attachmentFileCache = FakeChatAttachmentFileCache()
        val sourceFile = attachmentFileCache.put(
            attachmentId = "local-source",
            fileName = "big.bin",
            mimeType = "application/octet-stream",
            bytes = sourceBytes,
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = emptyList(),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                        RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            attachmentFileCache = attachmentFileCache,
        )
        val prepared = repository.prepareAttachment(
            chatId = chatId,
            attachment = ChatAttachment(
                id = "local-file-id",
                kind = ChatAttachmentKind.File,
                name = "big.bin",
                mimeType = "application/octet-stream",
                sizeBytes = sourceBytes.size.toLong(),
                localUri = sourceFile.localUri,
            ),
        )

        repository.sendMessage(chatId, "big", listOf(prepared))

        remoteDataSource.lastSentPayloads.forEach { payload ->
            val messagePayload = payload.chunks.single()
            assertTrue(messagePayload.contains(""""partIds":["attachment-1","attachment-2"]"""))
            assertTrue(messagePayload.contains(""""parts":[{"id":"attachment-1","index":0,"size":16777216"""))
            assertTrue(messagePayload.contains(""""id":"attachment-2","index":1,"size":1"""))
        }
    }

    @Test
    fun sendMessage_includesPreparedAttachmentIdTypeAndKeyInsideEncryptedPayload() = runBlocking {
        val chatId = "chat-1"
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = emptyList(),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                        RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        val repository = createRepository(remoteDataSource = remoteDataSource)
        val firstAttachment = repository.prepareAttachment(
            chatId = chatId,
            attachment = ChatAttachment(
                id = "image-uuid",
                kind = ChatAttachmentKind.Image,
                name = "image.jpg",
                contentBytes = "image-bytes".encodeToByteArray(),
            ),
        )
        val secondAttachment = repository.prepareAttachment(
            chatId = chatId,
            attachment = ChatAttachment(
                id = "file-uuid",
                kind = ChatAttachmentKind.File,
                name = "report.pdf",
                contentBytes = "report-bytes".encodeToByteArray(),
            ),
        )

        repository.sendMessage(chatId, "hello", listOf(firstAttachment, secondAttachment))

        val payloads = remoteDataSource.lastSentPayloads
        assertEquals(setOf("user-1", "user-2"), payloads.map(RemoteSendPayload::recipientId).toSet())
        payloads.forEach { payload ->
            assertEquals(
                """{"messageText":"hello","attachments":[{"id":"attachment-1","type":"photo","key":"chacha20-poly1305-key-1","name":"image.jpg"},{"id":"attachment-2","type":"file","key":"chacha20-poly1305-key-2","name":"report.pdf"}]}""",
                payload.chunks.single(),
            )
        }
    }

    @Test
    fun sendMessage_keepsAudioFilesAndVoiceMessagesAsDifferentAttachmentTypes() = runBlocking {
        val chatId = "chat-1"
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = emptyList(),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                        RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        val repository = createRepository(remoteDataSource = remoteDataSource)
        val audioAttachment = repository.prepareAttachment(
            chatId = chatId,
            attachment = ChatAttachment(
                id = "audio-uuid",
                kind = ChatAttachmentKind.Audio,
                name = "song.mp3",
                mimeType = "audio/mpeg",
                contentBytes = "audio-bytes".encodeToByteArray(),
            ),
        )
        val voiceAttachment = repository.prepareAttachment(
            chatId = chatId,
            attachment = ChatAttachment(
                id = "voice-uuid",
                kind = ChatAttachmentKind.Voice,
                name = "voice.m4a",
                mimeType = "audio/mp4",
                contentBytes = "voice-bytes".encodeToByteArray(),
            ),
        )

        repository.sendMessage(chatId, "", listOf(audioAttachment, voiceAttachment))

        remoteDataSource.lastSentPayloads.forEach { payload ->
            val messagePayload = payload.chunks.single()
            assertTrue(messagePayload.contains("\"type\":\"audio\""))
            assertTrue(messagePayload.contains("\"type\":\"voice\""))
        }
    }

    @Test
    fun openChat_downloadsDecryptsAndCachesIncomingImageAttachments() = runBlocking {
        val chatId = "chat-1"
        val attachmentKey = "chacha20-poly1305-key-1"
        val attachmentPayload =
            """{"messageText":"photo","attachments":[{"id":"attachment-1","type":"photo","key":"$attachmentKey"}]}"""
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(
                        remoteMessage(
                            chatId = chatId,
                            index = 1,
                            fromUserId = "user-2",
                            toUserId = "user-1",
                            createdAt = messageTimestamp(1),
                        ).copy(chunks = listOf(attachmentPayload)),
                    ),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                        RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        remoteDataSource.uploadAttachment(
            attachmentId = "attachment-1",
            uploadToken = "seed",
            bytes = "encrypted-with-$attachmentKey:image-bytes".encodeToByteArray(),
            contentType = "image/png",
        )
        val attachmentFileCache = FakeChatAttachmentFileCache()
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            attachmentFileCache = attachmentFileCache,
        )

        repository.openChat(chatId)

        val message = repository.observeChat(chatId)
            .filterNotNull()
            .first()
            .messages
            .filterIsInstance<ChatMessage.User>()
            .single()
        val attachment = message.attachments.single()
        assertEquals("photo", message.body)
        assertEquals(ChatAttachmentKind.Image, attachment.kind)
        assertEquals("attachment-1", attachment.id)
        assertEquals(null, attachment.contentBytes)
        assertEquals(null, attachment.localUri)

        val cachedAttachment = withTimeout(1_000) {
            repository.observeChat(chatId)
                .filterNotNull()
                .map { thread ->
                    thread.messages
                        .filterIsInstance<ChatMessage.User>()
                        .single()
                        .attachments
                        .single()
                }
                .first { loadedAttachment ->
                    loadedAttachment.localUri?.let { localUri ->
                        localUri.isNotBlank() &&
                            attachmentFileCache.readBytes(localUri)?.decodeToString() == "image-bytes"
                    } == true
                }
        }
        assertEquals(null, cachedAttachment.contentBytes)
        assertTrue(cachedAttachment.localUri?.isNotBlank() == true)
    }

    @Test
    fun requestAttachmentDownload_downloadsDecryptsAndAssemblesChunkedIncomingAttachments() = runBlocking {
        val chatId = "chat-1"
        val attachmentPayload =
            """{"messageText":"file","attachments":[{"id":"attachment-1","type":"file","key":"chacha20-poly1305-key-1","name":"big.bin","mimeType":"application/octet-stream","size":11,"chunkSize":6,"parts":[{"id":"attachment-1","index":0,"size":6,"key":"chacha20-poly1305-key-1"},{"id":"attachment-2","index":1,"size":5,"key":"chacha20-poly1305-key-2"}]}]}"""
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(
                        remoteMessage(
                            chatId = chatId,
                            index = 1,
                            fromUserId = "user-2",
                            toUserId = "user-1",
                            createdAt = messageTimestamp(1),
                        ).copy(chunks = listOf(attachmentPayload)),
                    ),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                        RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        remoteDataSource.uploadAttachment(
            attachmentId = "attachment-1",
            uploadToken = "seed-1",
            bytes = "encrypted-with-chacha20-poly1305-key-1:hello ".encodeToByteArray(),
            contentType = "application/octet-stream",
        )
        remoteDataSource.uploadAttachment(
            attachmentId = "attachment-2",
            uploadToken = "seed-2",
            bytes = "encrypted-with-chacha20-poly1305-key-2:world".encodeToByteArray(),
            contentType = "application/octet-stream",
        )
        val attachmentFileCache = FakeChatAttachmentFileCache()
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            attachmentFileCache = attachmentFileCache,
        )

        repository.openChat(chatId)

        val waitingAttachment = withTimeout(1_000) {
            repository.observeChat(chatId)
                .filterNotNull()
                .map { thread ->
                    thread.messages
                        .filterIsInstance<ChatMessage.User>()
                        .single()
                        .attachments
                        .single()
                }
                .first { loadedAttachment ->
                    loadedAttachment.localUri == null &&
                        loadedAttachment.loadState == ChatAttachmentLoadState.WaitingForTap
                }
        }
        assertEquals("big.bin", waitingAttachment.name)

        repository.requestAttachmentDownload(chatId, "attachment-1")

        val cachedAttachment = withTimeout(1_000) {
            repository.observeChat(chatId)
                .filterNotNull()
                .map { thread ->
                    thread.messages
                        .filterIsInstance<ChatMessage.User>()
                        .single()
                        .attachments
                        .single()
                }
                .first { loadedAttachment ->
                    loadedAttachment.localUri?.let { localUri ->
                        localUri.isNotBlank() &&
                            attachmentFileCache.readBytes(localUri)?.decodeToString() == "hello world"
                    } == true
                }
        }
        assertEquals("big.bin", cachedAttachment.name)
        assertEquals(2, cachedAttachment.parts.size)
        assertTrue(cachedAttachment.localUri?.isNotBlank() == true)
    }

    @Test
    fun syncLoop_refreshesParticipantKeysAfterIncomingMessageFromParticipantWithBlankLocalKey() = runBlocking {
        val chatId = "chat-1"
        val initialTimestamp = "2026-03-21T10:00:00Z"
        val incomingTimestamp = "2026-03-21T10:05:00Z"
        val initialChatInfo = RemoteChatInfo(
            id = chatId,
            title = "Group chat",
            type = "group",
            users = listOf(
                RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                RemoteChatUser(userId = "user-2", publicKey = ""),
            ),
        )
        val updatedChatInfo = initialChatInfo.copy(
            users = listOf(
                RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
            ),
        )
        val keyStore = FakeChatKeyStore()
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "public-key",
                privateKey = "private-key",
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(remoteMessage(chatId, 1, createdAt = initialTimestamp)),
                ),
            ),
            chatInfoById = mapOf(chatId to initialChatInfo),
            historyByOffset = emptyMap(),
            pollResults = listOf(
                RemotePolledMessages(
                    timestamp = incomingTimestamp,
                    messages = listOf(
                        remoteMessage(
                            chatId = chatId,
                            index = 2,
                            createdAt = incomingTimestamp,
                            fromUserId = "user-2",
                            toUserId = "user-1",
                        ),
                    ),
                ),
            ),
        )
        val repository = OfflineFirstChatRepository(
            localDataSource = FakeChatLocalDataSource(),
            remoteDataSource = remoteDataSource,
            chatMessageCipher = ChatMessageCipher(encryptionService, keyStore),
            chatKeyStore = keyStore,
            encryptionService = encryptionService,
            chatPreferencesDataSource = FakeChatPreferencesDataSource(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )

        repository.openChat(chatId)
        remoteDataSource.updateChatInfo(chatId, updatedChatInfo)
        runCatching {
            withTimeout(100) {
                repository.runSyncLoop()
            }
        }

        assertEquals(
            "public-key-2",
            keyStore.participantsFor(chatId).first { participant -> participant.userId == "user-2" }.publicKey,
        )
    }

    @Test
    fun syncLoop_handlesPublicKeyProvidedServiceMessageFromSwaggerContract() = runBlocking {
        val chatId = "chat-1"
        val initialTimestamp = "2026-03-21T10:00:00Z"
        val incomingTimestamp = "2026-03-21T10:05:00Z"
        val initialChatInfo = RemoteChatInfo(
            id = chatId,
            title = "Group chat",
            type = "group",
            users = listOf(
                RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                RemoteChatUser(userId = "user-2", publicKey = ""),
            ),
        )
        val updatedChatInfo = initialChatInfo.copy(
            users = listOf(
                RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
            ),
        )
        val keyStore = FakeChatKeyStore()
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "public-key",
                privateKey = "private-key",
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(remoteMessage(chatId, 1, createdAt = initialTimestamp)),
                ),
            ),
            chatInfoById = mapOf(chatId to initialChatInfo),
            historyByOffset = emptyMap(),
            pollResults = listOf(
                RemotePolledMessages(
                    timestamp = incomingTimestamp,
                    messages = listOf(
                        serviceMessage(
                            chatId = chatId,
                            id = "2",
                            createdAt = incomingTimestamp,
                            eventType = "public_key_provided",
                            payload = """
                                {"userID":"user-2"}
                            """.trimIndent(),
                        ),
                    ),
                ),
            ),
        )
        val repository = OfflineFirstChatRepository(
            localDataSource = FakeChatLocalDataSource(),
            remoteDataSource = remoteDataSource,
            chatMessageCipher = ChatMessageCipher(encryptionService, keyStore),
            chatKeyStore = keyStore,
            encryptionService = encryptionService,
            chatPreferencesDataSource = FakeChatPreferencesDataSource(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )

        repository.openChat(chatId)
        remoteDataSource.updateChatInfo(chatId, updatedChatInfo)
        runCatching {
            withTimeout(100) {
                repository.runSyncLoop()
            }
        }

        assertEquals(
            "public-key-2",
            keyStore.participantsFor(chatId).first { participant -> participant.userId == "user-2" }.publicKey,
        )
    }

    @Test
    fun syncLoop_sendsOwnNicknameWhenUserProvidesPublicKey() = runBlocking {
        val chatId = "chat-1"
        val initialTimestamp = "2026-03-21T10:00:00Z"
        val incomingTimestamp = "2026-03-21T10:05:00Z"
        val initialChatInfo = RemoteChatInfo(
            id = chatId,
            title = "Group chat",
            type = "group",
            users = listOf(
                RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                RemoteChatUser(userId = "user-2", publicKey = ""),
            ),
        )
        val updatedChatInfo = initialChatInfo.copy(
            users = listOf(
                RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(remoteMessage(chatId, 1, createdAt = initialTimestamp)),
                ),
            ),
            chatInfoById = mapOf(chatId to initialChatInfo),
            historyByOffset = emptyMap(),
            pollResults = listOf(
                RemotePolledMessages(
                    timestamp = incomingTimestamp,
                    messages = listOf(
                        serviceMessage(
                            chatId = chatId,
                            id = "2",
                            createdAt = incomingTimestamp,
                            eventType = "public_key_provided",
                            payload = """
                                {"userID":"user-2"}
                            """.trimIndent(),
                        ),
                    ),
                ),
            ),
        )
        val preferences = FakeChatPreferencesDataSource(initialNickname = "Daniil")
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            preferencesDataSource = preferences,
        )

        repository.openChat(chatId)
        remoteDataSource.updateChatInfo(chatId, updatedChatInfo)
        runCatching {
            withTimeout(100) {
                repository.runSyncLoop()
            }
        }

        val sentMessage = remoteDataSource.sentServiceMessages.single()
        assertEquals(chatId, sentMessage.first)
        val sentPayload = sentMessage.second.single()
        assertEquals("user-2", sentPayload.recipientId)
        assertEquals("user_nickname_provided", sentPayload.chunks.first())
        assertEquals(
            """{"userID":"user-1","nickname":"Daniil"}""",
            sentPayload.chunks.drop(1).single(),
        )
    }

    @Test
    fun broadcastNickname_sendsToPersonalAndGroupChatsFromEndAndStoresOwnNicknamePair() = runBlocking {
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = "chat-1",
                    title = "Personal chat",
                    type = "personal",
                    seedMessages = listOf(remoteMessage("chat-1", 1, createdAt = "2026-03-21T10:00:00Z")),
                ),
                RemoteChatSummary(
                    id = "chat-2",
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(remoteMessage("chat-2", 2, createdAt = "2026-03-21T10:01:00Z")),
                ),
            ),
            chatInfoById = mapOf(
                "chat-1" to RemoteChatInfo(
                    id = "chat-1",
                    title = "Personal chat",
                    type = "personal",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                        RemoteChatUser(userId = "user-2", publicKey = "public-key-user-2"),
                    ),
                ),
                "chat-2" to RemoteChatInfo(
                    id = "chat-2",
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                        RemoteChatUser(userId = "user-3", publicKey = "public-key-user-3"),
                        RemoteChatUser(userId = "user-4", publicKey = "public-key-user-4"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
        )
        val preferences = FakeChatPreferencesDataSource(initialNickname = "New Nick")
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            preferencesDataSource = preferences,
        )

        repository.broadcastNicknameToAllChats()

        assertEquals(
            listOf("chat-2", "chat-1"),
            remoteDataSource.sentServiceMessages.map { it.first },
        )
        assertEquals(
            setOf("user-3", "user-4"),
            remoteDataSource.sentServiceMessages
                .first { it.first == "chat-2" }
                .second
                .map(RemoteSendPayload::recipientId)
                .toSet(),
        )
        assertEquals(
            setOf("user-2"),
            remoteDataSource.sentServiceMessages
                .first { it.first == "chat-1" }
                .second
                .map(RemoteSendPayload::recipientId)
                .toSet(),
        )
        remoteDataSource.sentServiceMessages
            .flatMap { it.second }
            .forEach { payload ->
                assertEquals("user_nickname_provided", payload.chunks.first())
                assertEquals(
                    """{"userID":"user-1","nickname":"New Nick"}""",
                    payload.chunks.drop(1).single(),
                )
            }
        assertEquals("New Nick", preferences.getUserNickname("user-1"))
    }

    @Test
    fun syncLoop_savesNicknameAndUsesItInsteadOfUuidWherePossible() = runBlocking {
        val chatId = "chat-1"
        val initialTimestamp = "2026-03-21T10:00:00Z"
        val incomingTimestamp = "2026-03-21T10:05:00Z"
        val chatInfo = RemoteChatInfo(
            id = chatId,
            title = "user-2",
            type = "personal",
            users = listOf(
                RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "user-2",
                    type = "personal",
                    seedMessages = listOf(
                        remoteMessage(
                            chatId = chatId,
                            index = 1,
                            createdAt = initialTimestamp,
                            fromUserId = "user-2",
                            toUserId = "user-1",
                        ),
                    ),
                ),
            ),
            chatInfoById = mapOf(chatId to chatInfo),
            historyByOffset = emptyMap(),
            pollResults = listOf(
                RemotePolledMessages(
                    timestamp = incomingTimestamp,
                    messages = listOf(
                        serviceMessage(
                            chatId = chatId,
                            id = "2",
                            createdAt = incomingTimestamp,
                            eventType = "user_nickname_provided",
                            payload = """
                                {"userID":"user-2","nickname":"Alice"}
                            """.trimIndent(),
                        ),
                    ),
                ),
            ),
        )
        val preferences = FakeChatPreferencesDataSource()
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            preferencesDataSource = preferences,
        )

        repository.openChat(chatId)
        runCatching {
            withTimeout(100) {
                repository.runSyncLoop()
            }
        }

        val chat = requireNotNull(repository.observeChat(chatId).first())
        val firstUserMessage = chat.messages.first { it.id == "1" } as ChatMessage.User
        val nicknameServiceMessage = chat.messages.first { it.id == "2" } as ChatMessage.Service

        assertEquals("Alice", preferences.getUserNickname("user-2"))
        assertEquals("Alice", chat.title)
        assertEquals("A", chat.avatar.initials)
        assertEquals("Alice", firstUserMessage.sender)
        assertEquals("Alice изменил никнейм", nicknameServiceMessage.body)
        assertEquals("Alice", repository.getChatParticipants(chatId).first { it.userId == "user-2" }.displayName)
    }

    @Test
    fun loadMoreMessages_savesNicknameFromHistoricalServiceMessage() = runBlocking {
        val chatId = "chat-1"
        val nicknameTimestamp = "2026-03-21T10:01:00Z"
        val messageTimestamp = "2026-03-21T10:02:00Z"
        val previewTimestamp = "2026-03-21T10:10:00Z"
        val chatInfo = RemoteChatInfo(
            id = chatId,
            title = "Group chat",
            type = "group",
            users = listOf(
                RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(
                        remoteMessage(
                            chatId = chatId,
                            index = 3,
                            createdAt = previewTimestamp,
                            fromUserId = "user-2",
                            toUserId = "user-1",
                        ),
                    ),
                ),
            ),
            chatInfoById = mapOf(chatId to chatInfo),
            historyByOffset = mapOf(
                1 to listOf(
                    serviceMessage(
                        chatId = chatId,
                        id = "1",
                        createdAt = nicknameTimestamp,
                        eventType = "user_nickname_provided",
                        payload = """
                            {"userID":"user-2","nickname":"Alice"}
                        """.trimIndent(),
                    ),
                    remoteMessage(
                        chatId = chatId,
                        index = 2,
                        createdAt = messageTimestamp,
                        fromUserId = "user-2",
                        toUserId = "user-1",
                    ),
                ),
            ),
        )
        val preferences = FakeChatPreferencesDataSource()
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            preferencesDataSource = preferences,
        )

        repository.openChat(chatId)
        val loaded = repository.loadMoreMessages(chatId)

        val chat = requireNotNull(repository.observeChat(chatId).first())
        val historicalMessage = chat.messages.first { it.id == "2" } as ChatMessage.User
        val nicknameServiceMessage = chat.messages.first { it.id == "1" } as ChatMessage.Service

        assertTrue(loaded)
        assertEquals("Alice", preferences.getUserNickname("user-2"))
        assertEquals("Alice", historicalMessage.sender)
        assertEquals("Alice изменил никнейм", nicknameServiceMessage.body)
        assertEquals("Alice", repository.getChatParticipants(chatId).first { it.userId == "user-2" }.displayName)
    }

    @Test
    fun runSyncLoop_usesPersistedLastPollTimestampAfterOpeningChatWithNewerSeed() = runBlocking {
        val chatId = "chat-1"
        val persistedTimestamp = "2026-03-21T10:00:00Z"
        val previewTimestamp = "2026-03-21T10:10:00Z"
        val pollTimestamp = "2026-03-21T10:11:00Z"
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(
                        remoteMessage(chatId, 1, createdAt = previewTimestamp),
                    ),
                ),
            ),
            chatInfoById = mapOf(
                chatId to RemoteChatInfo(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
            pollResults = listOf(
                RemotePolledMessages(
                    timestamp = pollTimestamp,
                    messages = emptyList(),
                ),
            ),
        )
        val preferences = FakeChatPreferencesDataSource(initialLastPollTimestamp = persistedTimestamp)
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            preferencesDataSource = preferences,
        )

        repository.openChat(chatId)
        runCatching {
            withTimeout(100) {
                repository.runSyncLoop()
            }
        }

        assertEquals(listOf(persistedTimestamp), remoteDataSource.pollSinceCalls)
        assertEquals(pollTimestamp, repository.lastPollTimestamp)
        assertEquals(pollTimestamp, preferences.getLastPollTimestamp())
    }

    @Test
    fun syncLoop_reemitsOtherChatsWhenKnownNicknameChanges() = runBlocking {
        val nicknameChatId = "chat-1"
        val otherChatId = "chat-2"
        val initialTimestamp = "2026-03-21T10:00:00Z"
        val incomingTimestamp = "2026-03-21T10:05:00Z"
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = nicknameChatId,
                    title = "Nickname source",
                    type = "group",
                    seedMessages = emptyList(),
                ),
                RemoteChatSummary(
                    id = otherChatId,
                    title = "Other group",
                    type = "group",
                    seedMessages = listOf(
                        remoteMessage(
                            chatId = otherChatId,
                            index = 1,
                            createdAt = initialTimestamp,
                            fromUserId = "user-2",
                            toUserId = "user-1",
                        ),
                    ),
                ),
            ),
            chatInfoById = mapOf(
                nicknameChatId to RemoteChatInfo(
                    id = nicknameChatId,
                    title = "Nickname source",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                        RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
                    ),
                ),
                otherChatId to RemoteChatInfo(
                    id = otherChatId,
                    title = "Other group",
                    type = "group",
                    users = listOf(
                        RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                        RemoteChatUser(userId = "user-2", publicKey = "public-key-2"),
                    ),
                ),
            ),
            historyByOffset = emptyMap(),
            pollResults = listOf(
                RemotePolledMessages(
                    timestamp = incomingTimestamp,
                    messages = listOf(
                        serviceMessage(
                            chatId = nicknameChatId,
                            id = "2",
                            createdAt = incomingTimestamp,
                            eventType = "user_nickname_provided",
                            payload = """
                                {"userID":"user-2","nickname":"Alice"}
                            """.trimIndent(),
                        ),
                    ),
                ),
            ),
        )
        val keyStore = FakeChatKeyStore()
        keyStore.saveChatKeyPair(nicknameChatId, publicKey = "public-key", privateKey = "private-key")
        keyStore.saveChatKeyPair(otherChatId, publicKey = "public-key", privateKey = "private-key")
        val repository = createRepository(
            remoteDataSource = remoteDataSource,
            keyStore = keyStore,
        )

        repository.startSession()

        val observedNickname = CompletableDeferred<String>()
        val observerReady = CompletableDeferred<Unit>()
        val observer = launch {
            repository.observeChat(otherChatId)
                .filterNotNull()
                .collect { chat ->
                    if (!observerReady.isCompleted) observerReady.complete(Unit)
                    val message = chat.messages.firstOrNull { it.id == "1" } as? ChatMessage.User
                    if (message?.sender == "Alice") {
                        observedNickname.complete(message.sender)
                    }
                }
        }
        withTimeout(100) { observerReady.await() }

        runCatching {
            withTimeout(100) {
                repository.runSyncLoop()
            }
        }

        assertEquals("Alice", withTimeout(500) { observedNickname.await() })
        observer.cancel()
    }

    @Test
    fun syncLoop_updatesParticipantsWhenOtherUserAddedViaServiceMessage() = runBlocking {
        val chatId = "chat-1"
        val initialTimestamp = "2026-03-21T10:00:00Z"
        val incomingTimestamp = "2026-03-21T10:05:00Z"
        val initialChatInfo = RemoteChatInfo(
            id = chatId,
            title = "Group chat",
            type = "group",
            users = listOf(
                RemoteChatUser(userId = "user-1", publicKey = "public-key"),
            ),
        )
        val updatedChatInfo = initialChatInfo.copy(
            users = listOf(
                RemoteChatUser(userId = "user-1", publicKey = "public-key"),
                RemoteChatUser(userId = "user-2", publicKey = ""),
            ),
        )
        val keyStore = FakeChatKeyStore()
        val encryptionService = FakeEncryptionService(
            generatedKeyPair = GeneratedKeyPair(
                publicKey = "public-key",
                privateKey = "private-key",
            ),
        )
        val remoteDataSource = FakeChatRemoteDataSource(
            startSession = RemoteStartSession(
                userId = "user-1",
                serverPublicKey = "server-key",
            ),
            chats = listOf(
                RemoteChatSummary(
                    id = chatId,
                    title = "Group chat",
                    type = "group",
                    seedMessages = listOf(remoteMessage(chatId, 1, createdAt = initialTimestamp)),
                ),
            ),
            chatInfoById = mapOf(chatId to initialChatInfo),
            historyByOffset = emptyMap(),
            pollResults = listOf(
                RemotePolledMessages(
                    timestamp = incomingTimestamp,
                    messages = listOf(
                        serviceMessage(
                            chatId = chatId,
                            id = "2",
                            createdAt = incomingTimestamp,
                            eventType = "user_added",
                            payload = """
                                {"userID":"user-2"}
                            """.trimIndent(),
                        ),
                    ),
                ),
            ),
        )
        val repository = OfflineFirstChatRepository(
            localDataSource = FakeChatLocalDataSource(),
            remoteDataSource = remoteDataSource,
            chatMessageCipher = ChatMessageCipher(encryptionService, keyStore),
            chatKeyStore = keyStore,
            encryptionService = encryptionService,
            chatPreferencesDataSource = FakeChatPreferencesDataSource(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )

        repository.openChat(chatId)
        remoteDataSource.updateChatInfo(chatId, updatedChatInfo)
        runCatching {
            withTimeout(100) {
                repository.runSyncLoop()
            }
        }

        assertEquals(
            setOf("user-1", "user-2"),
            keyStore.participantsFor(chatId).map(ChatParticipantKey::userId).toSet(),
        )
    }
}

private fun remoteMessage(
    chatId: String,
    index: Int,
    createdAt: String = "",
    fromUserId: String = "user-1",
    toUserId: String = "user-1",
): RemoteMessage {
    return RemoteMessage(
        id = index.toString(),
        chatId = chatId,
        fromUserId = fromUserId,
        toUserId = toUserId,
        type = "text",
        chunks = listOf("chunk-$index"),
        createdAt = createdAt,
    )
}

private fun serviceMessage(
    chatId: String,
    id: String,
    createdAt: String,
    eventType: String,
    payload: String? = null,
): RemoteMessage {
    return RemoteMessage(
        id = id,
        chatId = chatId,
        fromUserId = "user-2",
        toUserId = "user-1",
        type = "service",
        chunks = listOfNotNull(eventType, payload),
        createdAt = createdAt,
    )
}

private fun groupChatInfo(chatId: String, otherUserId: String): RemoteChatInfo {
    return RemoteChatInfo(
        id = chatId,
        title = chatId,
        type = "group",
        users = listOf(
            RemoteChatUser(userId = "user-1", publicKey = "public-key"),
            RemoteChatUser(userId = otherUserId, publicKey = "public-key-$otherUserId"),
        ),
    )
}

private fun messageTimestamp(index: Int): String {
    val hour = 8 + index / 60
    val minute = index % 60
    return "2026-03-21T${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}:00Z"
}

private class FakeChatLocalDataSource(
    initialThreads: List<LocalChatThread> = emptyList(),
) : ChatLocalDataSource {
    private val threadsState = MutableStateFlow(initialThreads)

    override fun observeThreads(): StateFlow<List<LocalChatThread>> = threadsState.asStateFlow()

    override suspend fun upsertThreads(threads: List<LocalChatThread>) {
        val currentThreads = threadsState.value.associateBy(LocalChatThread::id).toMutableMap()
        threads.forEach { thread ->
            val existing = currentThreads[thread.id]
            currentThreads[thread.id] = thread.copy(
                messages = mergeMessages(
                    existingMessages = existing?.messages.orEmpty(),
                    incomingMessages = thread.messages,
                ),
                invitationStatus = if (thread.invitationStatus == "none") {
                    existing?.invitationStatus ?: "none"
                } else {
                    thread.invitationStatus
                },
            )
        }
        threadsState.value = currentThreads.values.sortedByDescending(LocalChatThread::lastMessagePosition)
    }

    override suspend fun removeChatsExcept(chatIds: Set<String>) {
        threadsState.value = threadsState.value.filter { it.id in chatIds }
    }

    override suspend fun replaceMessages(chatId: String, messages: List<LocalChatMessage>) {
        updateThread(chatId) { thread ->
            thread.copy(messages = messages.sortedBy(LocalChatMessage::position))
        }
    }

    override suspend fun prependMessages(chatId: String, messages: List<LocalChatMessage>) {
        updateThread(chatId) { thread ->
            thread.copy(
                messages = mergeMessages(
                    existingMessages = thread.messages,
                    incomingMessages = messages,
                ),
            )
        }
    }

    override suspend fun appendMessages(chatId: String, messages: List<LocalChatMessage>) {
        updateThread(chatId) { thread ->
            thread.copy(
                messages = mergeMessages(
                    existingMessages = thread.messages,
                    incomingMessages = messages,
                ),
            )
        }
    }

    override suspend fun hasMessages(chatId: String): Boolean {
        return threadsState.value.firstOrNull { it.id == chatId }?.messages?.isNotEmpty() == true
    }

    override suspend fun nextMessagePosition(chatId: String): Long {
        return threadsState.value
            .firstOrNull { it.id == chatId }
            ?.messages
            ?.maxOfOrNull(LocalChatMessage::position)
            ?.plus(1)
            ?: 1L
    }

    override suspend fun previousMessagePosition(chatId: String): Long {
        return threadsState.value
            .firstOrNull { it.id == chatId }
            ?.messages
            ?.minOfOrNull(LocalChatMessage::position)
            ?.minus(1)
            ?: 0L
    }

    override suspend fun incrementUnreadCount(chatId: String, incrementBy: Int) {
        if (incrementBy <= 0) return
        updateThread(chatId) { thread ->
            thread.copy(unreadCount = thread.unreadCount + incrementBy)
        }
    }

    override suspend fun markChatOpened(chatId: String) {
        updateThread(chatId) { thread -> thread.copy(unreadCount = 0) }
    }

    override suspend fun updateChatTitle(chatId: String, title: String) {
        updateThread(chatId) { thread -> thread.copy(title = title) }
    }

    override suspend fun updateInvitationStatus(chatId: String, status: String) {
        updateThread(chatId) { thread -> thread.copy(invitationStatus = status) }
    }

    override suspend fun clearAll() {
        threadsState.value = emptyList()
    }

    private fun updateThread(chatId: String, transform: (LocalChatThread) -> LocalChatThread) {
        threadsState.value = threadsState.value.map { thread ->
            if (thread.id == chatId) transform(thread) else thread
        }.sortedByDescending(LocalChatThread::lastMessagePosition)
    }

    private fun mergeMessages(
        existingMessages: List<LocalChatMessage>,
        incomingMessages: List<LocalChatMessage>,
    ): List<LocalChatMessage> {
        val merged = existingMessages.associateBy(LocalChatMessage::id).toMutableMap()
        incomingMessages.forEach { incoming ->
            val matchingLocalEchoId = if (!incoming.id.isLocalEchoMessageIdForTest()) {
                merged.values.firstOrNull { existing ->
                    existing.id.isLocalEchoMessageIdForTest() && existing.hasSameLocalEchoFingerprintForTest(incoming)
                }?.id
            } else {
                null
            }
            if (matchingLocalEchoId != null) {
                merged.remove(matchingLocalEchoId)
            } else if (incoming.id.isLocalEchoMessageIdForTest() && merged.values.any { existing ->
                    !existing.id.isLocalEchoMessageIdForTest() && existing.hasSameLocalEchoFingerprintForTest(incoming)
                }
            ) {
                return@forEach
            }

            val existing = merged[incoming.id]
            merged[incoming.id] = if (existing == null) {
                incoming
            } else {
                incoming.copy(
                    sender = incoming.sender ?: existing.sender,
                    encryptedChunks = incoming.encryptedChunks.ifEmpty { existing.encryptedChunks },
                    timestamp = incoming.timestamp.ifBlank { existing.timestamp },
                    isService = incoming.isService,
                    isMine = incoming.isMine ?: existing.isMine,
                    deliveryStatus = incoming.deliveryStatus ?: existing.deliveryStatus,
                    position = existing.position,
                    messageType = incoming.messageType.ifBlank { existing.messageType },
                    fromUserId = incoming.fromUserId ?: existing.fromUserId,
                    toUserId = incoming.toUserId ?: existing.toUserId,
                )
            }
        }
        return merged.values.sortedBy(LocalChatMessage::position)
    }
}

private fun String.isLocalEchoMessageIdForTest(): Boolean = startsWith("local-")

private fun LocalChatMessage.hasSameLocalEchoFingerprintForTest(other: LocalChatMessage): Boolean {
    return !isService &&
        !other.isService &&
        isMine == true &&
        other.isMine == true &&
        fromUserId == other.fromUserId &&
        toUserId == other.toUserId &&
        encryptedChunks.isNotEmpty() &&
        encryptedChunks == other.encryptedChunks
}

private class FakeChatRemoteDataSource(
    private val startSession: RemoteStartSession,
    private val chats: List<RemoteChatSummary>,
    private val chatInfoById: Map<String, RemoteChatInfo>,
    private val historyByOffset: Map<Int, List<RemoteMessage>>,
    private val pollResults: List<RemotePolledMessages> = emptyList(),
    private var getChatsFailuresRemaining: Int = 0,
    private val createDirectChatResponse: RemoteChatCreated? = null,
    private val createGroupChatResponse: RemoteChatCreated? = null,
    private val createSelfChatResult: RemoteCreateSelfChatResult? = null,
    private val attachmentUploadIds: MutableList<String> = mutableListOf(),
) : ChatRemoteDataSource {
    var startCalls: Int = 0
        private set

    var lastStartPublicKey: String? = null
        private set

    val historyCalls = mutableListOf<Int>()
    val pollSinceCalls = mutableListOf<String>()
    var sendCalls: Int = 0
        private set
    var getChatsCalls: Int = 0
        private set
    var lastSentPayloads: List<RemoteSendPayload> = emptyList()
        private set
    val sentServiceMessages = mutableListOf<Pair<String, List<RemoteSendPayload>>>()
    var lastCreateDirectChatPublicKey: String? = null
        private set
    var lastCreateGroupChatPublicKey: String? = null
        private set
    var lastCreateSelfChatPublicKey: String? = null
        private set
    val setGroupChatPublicKeyCalls = mutableListOf<Pair<String, String>>()
    val initAttachmentUploadCalls = mutableListOf<String>()
    val uploadedAttachments = mutableListOf<UploadedAttachment>()

    private var pollIndex: Int = 0
    private val mutableChats = chats.toMutableList()
    private val mutableChatInfoById = chatInfoById.toMutableMap()
    private var blockNextPoll = false
    private var blockedPoll = CompletableDeferred<RemotePolledMessages>()
    private var blockedPollStarted = CompletableDeferred<Unit>()

    override suspend fun start(publicKey: String): RemoteStartSession {
        startCalls += 1
        lastStartPublicKey = publicKey
        return startSession
    }

    override suspend fun getChats(): List<RemoteChatSummary> {
        getChatsCalls += 1
        if (getChatsFailuresRemaining > 0) {
            getChatsFailuresRemaining -= 1
            error("Simulated chat list failure")
        }
        return mutableChats.toList()
    }

    override suspend fun getChatInfo(chatId: String): RemoteChatInfo {
        return requireNotNull(mutableChatInfoById[chatId])
    }

    override suspend fun getMessageHistory(chatId: String, offset: Int, limit: Int): List<RemoteMessage> {
        historyCalls += offset
        return historyByOffset[offset].orEmpty()
    }

    override suspend fun pollMessages(since: String): RemotePolledMessages {
        pollSinceCalls += since
        if (blockNextPoll) {
            blockedPollStarted.complete(Unit)
            return blockedPoll.await()
        }
        val result = pollResults.getOrNull(pollIndex)
        pollIndex += 1
        return result ?: awaitCancellation()
    }

    override suspend fun sendMessage(chatId: String, payloads: List<RemoteSendPayload>) {
        sendCalls += 1
        lastSentPayloads = payloads
    }

    override suspend fun sendServiceMessage(chatId: String, payloads: List<RemoteSendPayload>) {
        sentServiceMessages += chatId to payloads
    }

    fun updateChatInfo(chatId: String, chatInfo: RemoteChatInfo) {
        mutableChatInfoById[chatId] = chatInfo
    }

    fun updateChats(chats: List<RemoteChatSummary>) {
        mutableChats.clear()
        mutableChats += chats
    }

    suspend fun awaitBlockedPollStarted() {
        blockedPollStarted.await()
    }

    fun blockNextPoll() {
        blockNextPoll = true
        blockedPoll = CompletableDeferred()
        blockedPollStarted = CompletableDeferred()
    }

    fun releaseBlockedPoll(result: RemotePolledMessages) {
        blockNextPoll = false
        blockedPoll.complete(result)
    }

    override suspend fun createDirectChat(targetUserId: String, publicKey: String): RemoteChatCreated {
        lastCreateDirectChatPublicKey = publicKey
        return requireNotNull(createDirectChatResponse) {
            "createDirectChatResponse was not configured for this test."
        }
    }

    override suspend fun createGroupChat(publicKey: String): RemoteChatCreated {
        lastCreateGroupChatPublicKey = publicKey
        return requireNotNull(createGroupChatResponse) {
            "createGroupChatResponse was not configured for this test."
        }
    }

    override suspend fun createSelfChat(publicKey: String): RemoteCreateSelfChatResult {
        lastCreateSelfChatPublicKey = publicKey
        return requireNotNull(createSelfChatResult) {
            "createSelfChatResult was not configured for this test."
        }
    }

    override suspend fun inviteUserToChat(chatId: String, userId: String) = Unit

    override suspend fun leaveGroupChat(chatId: String) = Unit

    override suspend fun leaveChat(chatId: String) = Unit

    override suspend fun setGroupChatPublicKey(chatId: String, publicKey: String) {
        setGroupChatPublicKeyCalls += chatId to publicKey
        val currentChatInfo = mutableChatInfoById[chatId] ?: return
        mutableChatInfoById[chatId] = currentChatInfo.copy(
            users = currentChatInfo.users.map { user ->
                if (user.userId == startSession.userId) {
                    user.copy(publicKey = publicKey)
                } else {
                    user
                }
            },
        )
    }

    override suspend fun initAttachmentUpload(): RemoteAttachmentUploadReservation {
        val attachmentId = if (attachmentUploadIds.isNotEmpty()) {
            attachmentUploadIds.removeAt(0)
        } else {
            "attachment-${initAttachmentUploadCalls.size + 1}"
        }
        initAttachmentUploadCalls += attachmentId
        return RemoteAttachmentUploadReservation(
            attachmentId = attachmentId,
            uploadToken = "upload-token-$attachmentId",
        )
    }

    override suspend fun uploadAttachment(
        attachmentId: String,
        uploadToken: String,
        bytes: ByteArray,
        contentType: String?,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit,
    ) {
        onProgress(bytes.size.toLong(), bytes.size.toLong())
        uploadedAttachments += UploadedAttachment(
            attachmentId = attachmentId,
            uploadToken = uploadToken,
            bytes = bytes,
            contentType = contentType,
        )
    }

    override suspend fun downloadAttachment(attachmentId: String, rangeHeader: String?): ByteArray {
        return uploadedAttachments.first { upload -> upload.attachmentId == attachmentId }.bytes
    }
}

private data class UploadedAttachment(
    val attachmentId: String,
    val uploadToken: String,
    val bytes: ByteArray,
    val contentType: String?,
)

private class FakeChatKeyStore(
    private var currentUserId: String? = null,
    private var currentUserPublicKey: String? = null,
    private var currentUserPrivateKey: String? = null,
    private var serverPublicKey: String? = null,
    private val autoStoreCurrentUserChatKeyFromParticipants: Boolean = true,
) : ChatKeyStore {
    private val participantsByChatId = mutableMapOf<String, List<ChatParticipantKey>>()
    private val chatPublicKeys = mutableMapOf<String, String>()
    private val chatPrivateKeys = mutableMapOf<String, String>()
    private val keyRevision = MutableStateFlow("0")
    private var selfChatId: String? = null
    private var selfChatPublicKey: String? = null
    private var selfChatPrivateKey: String? = null
    private var deviceId: String? = null
    private var revisionCounter = 0

    override fun observeKeyRevision(): Flow<String> = keyRevision.asStateFlow()

    override suspend fun currentKeyRevision(): String = keyRevision.value

    override suspend fun currentUserId(): String? = currentUserId

    override suspend fun currentUserPublicKey(): String? = currentUserPublicKey

    override suspend fun currentUserPrivateKey(): String? = currentUserPrivateKey

    override suspend fun serverPublicKey(): String? = serverPublicKey

    override suspend fun saveCurrentUserKeys(userId: String?, publicKey: String, privateKey: String) {
        currentUserId = userId ?: currentUserId
        currentUserPublicKey = publicKey
        currentUserPrivateKey = privateKey
        touchKeyRevision()
    }

    override suspend fun saveCurrentUserId(userId: String) {
        currentUserId = userId
        touchKeyRevision()
    }

    override suspend fun saveServerPublicKey(publicKey: String) {
        serverPublicKey = publicKey
        touchKeyRevision()
    }

    override suspend fun chatPublicKey(chatId: String): String? = chatPublicKeys[chatId]

    override suspend fun chatPrivateKey(chatId: String): String? = chatPrivateKeys[chatId]

    override suspend fun saveChatKeyPair(chatId: String, publicKey: String, privateKey: String) {
        chatPublicKeys[chatId] = publicKey
        chatPrivateKeys[chatId] = privateKey
        touchKeyRevision()
    }

    override suspend fun selfChatId(): String? = selfChatId

    override suspend fun saveSelfChatId(chatId: String) {
        selfChatId = chatId
        touchKeyRevision()
    }

    override suspend fun selfChatPublicKey(): String? = selfChatPublicKey

    override suspend fun selfChatPrivateKeyRef(): PrivateKeyRef? {
        return selfChatPrivateKey?.let(PrivateKeyRef::Exported)
    }

    override suspend fun saveSelfChatKeyPair(publicKey: String, privateKeyRef: PrivateKeyRef) {
        selfChatPublicKey = publicKey
        selfChatPrivateKey = privateKeyRef.serialize().deserializeFakePrivateKey()
        selfChatId?.let { chatId -> saveChatKeyPair(chatId, publicKey, selfChatPrivateKey.orEmpty()) }
        touchKeyRevision()
    }

    override suspend fun deviceId(): String? = deviceId

    override suspend fun saveDeviceId(deviceId: String) {
        this.deviceId = deviceId
    }

    override suspend fun participantsFor(chatId: String): List<ChatParticipantKey> {
        return participantsByChatId[chatId].orEmpty()
    }

    override suspend fun saveParticipants(chatId: String, participants: List<ChatParticipantKey>) {
        participantsByChatId[chatId] = participants
        val currentUserParticipant = participants.firstOrNull { participant ->
            participant.isCurrentUser && participant.userId == currentUserId && participant.publicKey.isNotBlank()
        }
        if (autoStoreCurrentUserChatKeyFromParticipants && currentUserParticipant != null && chatPrivateKeys[chatId] == null) {
            chatPublicKeys[chatId] = currentUserParticipant.publicKey
            chatPrivateKeys[chatId] = "private-key-for-${currentUserParticipant.publicKey}"
        }
        touchKeyRevision()
    }

    override suspend fun clearChatState(chatId: String) {
        participantsByChatId.remove(chatId)
        chatPublicKeys.remove(chatId)
        chatPrivateKeys.remove(chatId)
        touchKeyRevision()
    }

    override suspend fun exportSnapshot(): ChatKeySnapshot {
        return ChatKeySnapshot(
            currentUserId = checkNotNull(currentUserId),
            currentUserPublicKey = checkNotNull(currentUserPublicKey),
            currentUserPrivateKeyRef = PrivateKeyRef.Exported(checkNotNull(currentUserPrivateKey)).serialize(),
            serverPublicKey = serverPublicKey,
            selfChatId = selfChatId,
            selfChatPublicKey = selfChatPublicKey,
            selfChatPrivateKeyRef = selfChatPrivateKey?.let { PrivateKeyRef.Exported(it).serialize() },
            chatKeys = chatPublicKeys.mapNotNull { (chatId, publicKey) ->
                StoredChatKeyPair(
                    chatId = chatId,
                    publicKey = publicKey,
                    privateKeyRef = PrivateKeyRef.Exported(chatPrivateKeys[chatId] ?: return@mapNotNull null).serialize(),
                )
            },
            participantsByChat = participantsByChatId.map { (chatId, participants) ->
                StoredChatParticipants(chatId = chatId, participants = participants)
            },
        )
    }

    override suspend fun importSnapshot(snapshot: ChatKeySnapshot) {
        currentUserId = snapshot.currentUserId
        currentUserPublicKey = snapshot.currentUserPublicKey
        currentUserPrivateKey = snapshot.currentUserPrivateKeyRef.deserializeFakePrivateKey()
        serverPublicKey = snapshot.serverPublicKey
        selfChatId = snapshot.selfChatId
        selfChatPublicKey = snapshot.selfChatPublicKey
        selfChatPrivateKey = snapshot.selfChatPrivateKeyRef?.deserializeFakePrivateKey()
        chatPublicKeys.clear()
        chatPrivateKeys.clear()
        participantsByChatId.clear()
        snapshot.chatKeys.forEach { keyPair ->
            chatPublicKeys[keyPair.chatId] = keyPair.publicKey
            chatPrivateKeys[keyPair.chatId] = keyPair.privateKeyRef.deserializeFakePrivateKey()
        }
        snapshot.participantsByChat.forEach { participants ->
            participantsByChatId[participants.chatId] = participants.participants
        }
        touchKeyRevision()
    }

    override suspend fun clearAll() {
        currentUserId = null
        currentUserPublicKey = null
        currentUserPrivateKey = null
        serverPublicKey = null
        selfChatId = null
        selfChatPublicKey = null
        selfChatPrivateKey = null
        deviceId = null
        participantsByChatId.clear()
        chatPublicKeys.clear()
        chatPrivateKeys.clear()
        touchKeyRevision()
    }

    private fun touchKeyRevision() {
        revisionCounter += 1
        keyRevision.value = revisionCounter.toString()
    }

    private fun String.deserializeFakePrivateKey(): String {
        return (PrivateKeyRef.deserialize(this) as? PrivateKeyRef.Exported)?.value ?: this
    }
}

private class FakeEncryptionService(
    private val generatedKeyPair: GeneratedKeyPair,
    private val generatedKeyPairs: MutableList<GeneratedKeyPair> = mutableListOf(),
    private val failingPublicKeys: Set<String> = emptySet(),
) : EncryptionService {
    override val algorithmLabel: String = "fake"
    override val maxPayloadBytesPerChunk: Int = 190
    private var generatedAttachmentKeyCount = 0

    override suspend fun generateKeyPair(): GeneratedKeyPair {
        return if (generatedKeyPairs.isNotEmpty()) {
            generatedKeyPairs.removeAt(0)
        } else {
            generatedKeyPair
        }
    }

    override suspend fun generateAttachmentKey(): GeneratedAttachmentKey {
        generatedAttachmentKeyCount += 1
        return GeneratedAttachmentKey(
            key = "chacha20-poly1305-key-$generatedAttachmentKeyCount",
            sizeBits = 256,
            algorithmLabel = "ChaCha20-Poly1305",
        )
    }

    override suspend fun encrypt(message: String, publicKey: String): String = message

    override suspend fun decrypt(message: String, privateKey: String): String = message

    override suspend fun encryptAttachment(bytes: ByteArray, key: String): ByteArray {
        return ("encrypted-with-$key:").encodeToByteArray() + bytes
    }

    override suspend fun decryptAttachment(bytes: ByteArray, key: String): ByteArray {
        val prefix = "encrypted-with-$key:".encodeToByteArray()
        return if (bytes.size >= prefix.size && bytes.copyOfRange(0, prefix.size).contentEquals(prefix)) {
            bytes.copyOfRange(prefix.size, bytes.size)
        } else {
            bytes
        }
    }

    override suspend fun encryptToChunks(message: String, publicKey: String, chunkSizeBytes: Int): List<String> {
        require(publicKey.isNotBlank()) { "Public key must not be blank." }
        check(publicKey !in failingPublicKeys) { "Encryption failed." }
        return listOf(message)
    }

    override suspend fun decryptFromChunks(chunks: List<String>, privateKey: String): String {
        return chunks.joinToString(separator = "")
    }
}

private class FakeChatPreferencesDataSource(
    initialOpenedChatId: String? = null,
    initialNickname: String = "",
    initialLastPollTimestamp: String? = null,
    initialUserNicknames: Map<String, String> = emptyMap(),
) : ChatPreferencesDataSource {
    private val openedChatId = MutableStateFlow(initialOpenedChatId)
    private val nickname = MutableStateFlow(initialNickname)
    private var lastPollTimestamp = initialLastPollTimestamp
    private val userNicknames = initialUserNicknames.toMutableMap()
    private val chatTitles = mutableMapOf<String, String>()
    private var debugMode = false
    private var mediaCacheRetentionDays = 7
    private val themeMode = MutableStateFlow(AppThemeMode.System)
    private val desktopAutostartEnabled = MutableStateFlow(true)
    private var accountOnboardingCompleted = false

    override fun observeLastOpenedChatId(): Flow<String?> = openedChatId.asStateFlow()

    override suspend fun currentOpenedChatId(): String? = openedChatId.value

    override suspend fun saveLastOpenedChatId(chatId: String) {
        openedChatId.value = chatId
    }

    override suspend fun clearLastOpenedChatId() {
        openedChatId.value = null
    }

    override suspend fun getLastPollTimestamp(): String? {
        return lastPollTimestamp
    }

    override suspend fun saveLastPollTimestamp(timestamp: String) {
        lastPollTimestamp = timestamp
    }

    override suspend fun clearLastPollTimestamp() {
        lastPollTimestamp = null
    }

    override fun observeNickname(): Flow<String> = nickname.asStateFlow()

    override suspend fun getNickname(): String = nickname.value

    override suspend fun saveNickname(nickname: String) {
        this.nickname.value = nickname
    }

    override suspend fun getUserNickname(userId: String): String? {
        return userNicknames[userId]
    }

    override suspend fun saveUserNickname(userId: String, nickname: String) {
        userNicknames[userId] = nickname
    }

    override suspend fun getChatTitle(chatId: String): String? {
        return chatTitles[chatId]
    }

    override suspend fun saveChatTitle(chatId: String, title: String) {
        chatTitles[chatId] = title
    }

    override suspend fun clearAll() {
        clearLastOpenedChatId()
        clearLastPollTimestamp()
        nickname.value = ""
        userNicknames.clear()
        chatTitles.clear()
        debugMode = false
    }

    override suspend fun isDebugModeEnabled(): Boolean {
        return debugMode
    }

    override suspend fun setDebugModeEnabled(enabled: Boolean) {
        debugMode = enabled
    }

    override suspend fun getMediaCacheRetentionDays(): Int {
        return mediaCacheRetentionDays
    }

    override suspend fun saveMediaCacheRetentionDays(days: Int) {
        mediaCacheRetentionDays = days
    }

    override fun observeThemeMode(): Flow<AppThemeMode> = themeMode.asStateFlow()

    override suspend fun getThemeMode(): AppThemeMode = themeMode.value

    override suspend fun saveThemeMode(themeMode: AppThemeMode) {
        this.themeMode.value = themeMode
    }

    override fun observeDesktopAutostartEnabled(): Flow<Boolean> = desktopAutostartEnabled.asStateFlow()

    override suspend fun isDesktopAutostartEnabled(): Boolean = desktopAutostartEnabled.value

    override suspend fun setDesktopAutostartEnabled(enabled: Boolean) {
        desktopAutostartEnabled.value = enabled
    }

    override suspend fun isAccountOnboardingCompleted(): Boolean = accountOnboardingCompleted

    override suspend fun setAccountOnboardingCompleted(completed: Boolean) {
        accountOnboardingCompleted = completed
    }
}

private class FakeChatAttachmentFileCache : ChatAttachmentFileCache {
    private val cachedAttachments = mutableMapOf<String, CachedChatAttachment>()
    private val cachedBytes = mutableMapOf<String, ByteArray>()

    override suspend fun get(attachmentId: String): CachedChatAttachment? {
        return cachedAttachments[attachmentId]
    }

    override suspend fun put(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        bytes: ByteArray,
    ): CachedChatAttachment {
        val localUri = "memory://$attachmentId"
        val cachedAttachment = CachedChatAttachment(
            localUri = localUri,
            sizeBytes = bytes.size.toLong(),
        )
        cachedAttachments[attachmentId] = cachedAttachment
        cachedBytes[localUri] = bytes
        return cachedAttachment
    }

    override suspend fun putEncrypted(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        encryptedBytes: ByteArray,
        plainSizeBytes: Long?,
        decryptionKey: String,
    ): CachedChatAttachment {
        val bytes = encryptedBytes.decryptFakeAttachment(decryptionKey)
        return put(
            attachmentId = attachmentId,
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
        ).copy(sizeBytes = plainSizeBytes ?: bytes.size.toLong()).also { cached ->
            cachedAttachments[attachmentId] = cached
        }
    }

    override suspend fun readBytes(localUri: String): ByteArray? {
        return cachedBytes[localUri]
    }

    override suspend fun readBytes(localUri: String, offset: Long, length: Int): ByteArray? {
        val bytes = cachedBytes[localUri] ?: return null
        if (offset >= bytes.size) return ByteArray(0)
        val startIndex = offset.toInt()
        val endIndex = minOf(startIndex + length, bytes.size)
        return bytes.copyOfRange(startIndex, endIndex)
    }

    override suspend fun putFromChunks(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        chunks: suspend (suspend (ByteArray) -> Unit) -> Unit,
    ): CachedChatAttachment {
        val assembledBytes = mutableListOf<Byte>()
        chunks { bytes -> assembledBytes += bytes.asIterable() }
        return put(
            attachmentId = attachmentId,
            fileName = fileName,
            mimeType = mimeType,
            bytes = assembledBytes.toByteArray(),
        )
    }

    override suspend fun putEncryptedFromChunks(
        attachmentId: String,
        fileName: String?,
        mimeType: String?,
        plainSizeBytes: Long?,
        chunks: suspend (suspend (EncryptedCachedAttachmentPart) -> Unit) -> Unit,
    ): CachedChatAttachment {
        val assembledBytes = mutableListOf<Byte>()
        chunks { part -> assembledBytes += part.encryptedBytes.decryptFakeAttachment(part.decryptionKey).asIterable() }
        return put(
            attachmentId = attachmentId,
            fileName = fileName,
            mimeType = mimeType,
            bytes = assembledBytes.toByteArray(),
        ).copy(sizeBytes = plainSizeBytes ?: assembledBytes.size.toLong()).also { cached ->
            cachedAttachments[attachmentId] = cached
        }
    }

    override suspend fun clearAll(): Int {
        val count = cachedAttachments.size
        cachedAttachments.clear()
        cachedBytes.clear()
        return count
    }

    override suspend fun clearOlderThan(ageMillis: Long): Int {
        return 0
    }
}

private fun ByteArray.decryptFakeAttachment(key: String): ByteArray {
    val prefix = "encrypted-with-$key:".encodeToByteArray()
    return if (size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)) {
        copyOfRange(prefix.size, size)
    } else {
        this
    }
}

private fun createRepository(
    remoteDataSource: FakeChatRemoteDataSource,
    localDataSource: FakeChatLocalDataSource = FakeChatLocalDataSource(),
    keyStore: FakeChatKeyStore = FakeChatKeyStore(),
    encryptionService: FakeEncryptionService = FakeEncryptionService(
        generatedKeyPair = GeneratedKeyPair(
            publicKey = "public-key",
            privateKey = "private-key",
        ),
    ),
    preferencesDataSource: FakeChatPreferencesDataSource = FakeChatPreferencesDataSource(),
    attachmentFileCache: ChatAttachmentFileCache = NoOpChatAttachmentFileCache,
): OfflineFirstChatRepository {
    return OfflineFirstChatRepository(
        localDataSource = localDataSource,
        remoteDataSource = remoteDataSource,
        chatMessageCipher = ChatMessageCipher(encryptionService, keyStore),
        chatKeyStore = keyStore,
        encryptionService = encryptionService,
        chatPreferencesDataSource = preferencesDataSource,
        json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        attachmentFileCache = attachmentFileCache,
    )
}

private const val MIN_POLL_TIMESTAMP_FOR_TESTS = "1970-01-01T00:00:00Z"
