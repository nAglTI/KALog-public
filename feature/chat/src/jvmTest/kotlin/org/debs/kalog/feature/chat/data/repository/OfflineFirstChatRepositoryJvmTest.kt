package org.debs.kalog.feature.chat.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.debs.kalog.core.crypto.EncryptionService
import org.debs.kalog.core.crypto.GeneratedKeyPair
import org.debs.kalog.feature.chat.data.crypto.ChatKeyStore
import org.debs.kalog.feature.chat.data.crypto.ChatMessageCipher
import org.debs.kalog.feature.chat.data.crypto.ChatParticipantKey
import org.debs.kalog.feature.chat.data.local.ChatLocalDataSource
import org.debs.kalog.feature.chat.data.local.LocalChatMessage
import org.debs.kalog.feature.chat.data.local.LocalChatThread
import org.debs.kalog.feature.chat.data.preferences.ChatPreferencesDataSource
import org.debs.kalog.feature.chat.data.remote.*
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun sendMessage_reliesOnServerPushInsteadOfRefreshingHistory() = runBlocking {
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
        assertEquals(listOf("1"), localDataSource.observeThreads().value.first().messages.map(LocalChatMessage::id))
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
            val merged = (messages + thread.messages)
                .distinctBy(LocalChatMessage::id)
                .sortedBy(LocalChatMessage::position)
            thread.copy(messages = merged)
        }
    }

    override suspend fun appendMessages(chatId: String, messages: List<LocalChatMessage>) {
        updateThread(chatId) { thread ->
            val merged = (thread.messages + messages)
                .distinctBy(LocalChatMessage::id)
                .sortedBy(LocalChatMessage::position)
            thread.copy(messages = merged)
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

private class FakeChatRemoteDataSource(
    private val startSession: RemoteStartSession,
    private val chats: List<RemoteChatSummary>,
    private val chatInfoById: Map<String, RemoteChatInfo>,
    private val historyByOffset: Map<Int, List<RemoteMessage>>,
    private val pollResults: List<RemotePolledMessages> = emptyList(),
    private var getChatsFailuresRemaining: Int = 0,
    private val createDirectChatResponse: RemoteChatCreated? = null,
    private val createGroupChatResponse: RemoteChatCreated? = null,
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
    var lastCreateDirectChatPublicKey: String? = null
        private set
    var lastCreateGroupChatPublicKey: String? = null
        private set
    val setGroupChatPublicKeyCalls = mutableListOf<Pair<String, String>>()

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

    override suspend fun inviteUserToChat(chatId: String, userId: String) = Unit

    override suspend fun leaveGroupChat(chatId: String) = Unit

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
}

private class FakeChatKeyStore(
    private var currentUserId: String? = null,
    private var currentUserPublicKey: String? = null,
    private var currentUserPrivateKey: String? = null,
    private var serverPublicKey: String? = null,
) : ChatKeyStore {
    private val participantsByChatId = mutableMapOf<String, List<ChatParticipantKey>>()
    private val chatPublicKeys = mutableMapOf<String, String>()
    private val chatPrivateKeys = mutableMapOf<String, String>()

    override suspend fun currentUserId(): String? = currentUserId

    override suspend fun currentUserPublicKey(): String? = currentUserPublicKey

    override suspend fun currentUserPrivateKey(): String? = currentUserPrivateKey

    override suspend fun serverPublicKey(): String? = serverPublicKey

    override suspend fun saveCurrentUserKeys(userId: String?, publicKey: String, privateKey: String) {
        currentUserId = userId ?: currentUserId
        currentUserPublicKey = publicKey
        currentUserPrivateKey = privateKey
    }

    override suspend fun saveCurrentUserId(userId: String) {
        currentUserId = userId
    }

    override suspend fun saveServerPublicKey(publicKey: String) {
        serverPublicKey = publicKey
    }

    override suspend fun chatPublicKey(chatId: String): String? = chatPublicKeys[chatId]

    override suspend fun chatPrivateKey(chatId: String): String? = chatPrivateKeys[chatId]

    override suspend fun saveChatKeyPair(chatId: String, publicKey: String, privateKey: String) {
        chatPublicKeys[chatId] = publicKey
        chatPrivateKeys[chatId] = privateKey
    }

    override suspend fun participantsFor(chatId: String): List<ChatParticipantKey> {
        return participantsByChatId[chatId].orEmpty()
    }

    override suspend fun saveParticipants(chatId: String, participants: List<ChatParticipantKey>) {
        participantsByChatId[chatId] = participants
    }

    override suspend fun clearChatState(chatId: String) {
        participantsByChatId.remove(chatId)
        chatPublicKeys.remove(chatId)
        chatPrivateKeys.remove(chatId)
    }

    override suspend fun clearAll() {
        currentUserId = null
        currentUserPublicKey = null
        currentUserPrivateKey = null
        serverPublicKey = null
        participantsByChatId.clear()
        chatPublicKeys.clear()
        chatPrivateKeys.clear()
    }
}

private class FakeEncryptionService(
    private val generatedKeyPair: GeneratedKeyPair,
    private val generatedKeyPairs: MutableList<GeneratedKeyPair> = mutableListOf(),
) : EncryptionService {
    override val algorithmLabel: String = "fake"
    override val maxPayloadBytesPerChunk: Int = 190

    override suspend fun generateKeyPair(): GeneratedKeyPair {
        return if (generatedKeyPairs.isNotEmpty()) {
            generatedKeyPairs.removeAt(0)
        } else {
            generatedKeyPair
        }
    }

    override suspend fun encrypt(message: String, publicKey: String): String = message

    override suspend fun decrypt(message: String, privateKey: String): String = message

    override suspend fun encryptToChunks(message: String, publicKey: String, chunkSizeBytes: Int): List<String> {
        require(publicKey.isNotBlank()) { "Public key must not be blank." }
        return listOf(message)
    }

    override suspend fun decryptFromChunks(chunks: List<String>, privateKey: String): String {
        return chunks.joinToString(separator = "")
    }
}

private class FakeChatPreferencesDataSource(
    initialOpenedChatId: String? = null,
) : ChatPreferencesDataSource {
    private val openedChatId = MutableStateFlow(initialOpenedChatId)

    override fun observeLastOpenedChatId(): Flow<String?> = openedChatId.asStateFlow()

    override suspend fun currentOpenedChatId(): String? = openedChatId.value

    override suspend fun saveLastOpenedChatId(chatId: String) {
        openedChatId.value = chatId
    }

    override suspend fun clearLastOpenedChatId() {
        openedChatId.value = null
    }

    override suspend fun clearAll() {
        clearLastOpenedChatId()
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
): OfflineFirstChatRepository {
    return OfflineFirstChatRepository(
        localDataSource = localDataSource,
        remoteDataSource = remoteDataSource,
        chatMessageCipher = ChatMessageCipher(encryptionService, keyStore),
        chatKeyStore = keyStore,
        encryptionService = encryptionService,
        chatPreferencesDataSource = FakeChatPreferencesDataSource(),
        json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
    )
}

private const val MIN_POLL_TIMESTAMP_FOR_TESTS = "1970-01-01T00:00:00Z"
