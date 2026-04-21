package org.debs.kalog.feature.chat.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InMemoryChatLocalDataSource : ChatLocalDataSource {
    private val mutex = Mutex()
    private val threadsState = MutableStateFlow(emptyList<LocalChatThread>())

    override fun observeThreads(): StateFlow<List<LocalChatThread>> = threadsState.asStateFlow()

    override suspend fun upsertThreads(threads: List<LocalChatThread>) = mutex.withLock {
        val currentThreads = threadsState.value.associateBy(LocalChatThread::id).toMutableMap()
        threads.forEach { incomingThread ->
            val existingThread = currentThreads[incomingThread.id]
            currentThreads[incomingThread.id] = incomingThread.copy(
                messages = mergeMessages(
                    existingMessages = existingThread?.messages.orEmpty(),
                    incomingMessages = incomingThread.messages,
                ),
                invitationStatus = if (incomingThread.invitationStatus == "none") {
                    existingThread?.invitationStatus ?: "none"
                } else {
                    incomingThread.invitationStatus
                },
            )
        }
        threadsState.value = currentThreads.values.sortForDisplay()
    }

    override suspend fun removeChatsExcept(chatIds: Set<String>) = mutex.withLock {
        threadsState.value = threadsState.value
            .filter { thread -> thread.id in chatIds }
            .sortForDisplay()
    }

    override suspend fun replaceMessages(chatId: String, messages: List<LocalChatMessage>) = mutex.withLock {
        updateThread(chatId) { thread ->
            thread.copy(messages = messages.sortedBy(LocalChatMessage::position))
        }
    }

    override suspend fun prependMessages(chatId: String, messages: List<LocalChatMessage>) = mutex.withLock {
        updateThread(chatId) { thread ->
            thread.copy(
                messages = mergeMessages(
                    existingMessages = thread.messages,
                    incomingMessages = messages,
                ),
            )
        }
    }

    override suspend fun appendMessages(chatId: String, messages: List<LocalChatMessage>) = mutex.withLock {
        updateThread(chatId) { thread ->
            thread.copy(
                messages = mergeMessages(
                    existingMessages = thread.messages,
                    incomingMessages = messages,
                ),
            )
        }
    }

    override suspend fun hasMessages(chatId: String): Boolean = mutex.withLock {
        threadsState.value.firstOrNull { thread -> thread.id == chatId }?.messages?.isNotEmpty() == true
    }

    override suspend fun nextMessagePosition(chatId: String): Long = mutex.withLock {
        threadsState.value
            .firstOrNull { thread -> thread.id == chatId }
            ?.messages
            ?.maxOfOrNull(LocalChatMessage::position)
            ?.plus(1)
            ?: 1L
    }

    override suspend fun previousMessagePosition(chatId: String): Long = mutex.withLock {
        threadsState.value
            .firstOrNull { thread -> thread.id == chatId }
            ?.messages
            ?.minOfOrNull(LocalChatMessage::position)
            ?.minus(1)
            ?: 0L
    }

    override suspend fun incrementUnreadCount(chatId: String, incrementBy: Int) = mutex.withLock {
        if (incrementBy <= 0) return@withLock
        updateThread(chatId) { thread ->
            thread.copy(unreadCount = thread.unreadCount + incrementBy)
        }
    }

    override suspend fun markChatOpened(chatId: String) = mutex.withLock {
        updateThread(chatId) { thread -> thread.copy(unreadCount = 0) }
    }

    override suspend fun updateChatTitle(chatId: String, title: String) = mutex.withLock {
        updateThread(chatId) { thread -> thread.copy(title = title) }
    }

    override suspend fun updateInvitationStatus(chatId: String, status: String) = mutex.withLock {
        updateThread(chatId) { thread -> thread.copy(invitationStatus = status) }
    }

    override suspend fun clearAll() = mutex.withLock {
        threadsState.value = emptyList()
    }

    private fun updateThread(chatId: String, transform: (LocalChatThread) -> LocalChatThread) {
        threadsState.value = threadsState.value
            .map { thread -> if (thread.id == chatId) transform(thread) else thread }
            .sortForDisplay()
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

private fun Collection<LocalChatThread>.sortForDisplay(): List<LocalChatThread> {
    return sortedByDescending { thread ->
        thread.messages.maxOfOrNull(LocalChatMessage::timestamp).orEmpty()
    }
}
