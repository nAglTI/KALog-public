package org.debs.kalog.feature.chat.data.local

import kotlinx.coroutines.flow.StateFlow

internal interface ChatLocalDataSource {
    fun observeThreads(): StateFlow<List<LocalChatThread>>

    suspend fun upsertThreads(threads: List<LocalChatThread>)

    suspend fun removeChatsExcept(chatIds: Set<String>)

    suspend fun replaceMessages(chatId: String, messages: List<LocalChatMessage>)

    suspend fun prependMessages(chatId: String, messages: List<LocalChatMessage>)

    suspend fun appendMessages(chatId: String, messages: List<LocalChatMessage>)

    suspend fun hasMessages(chatId: String): Boolean

    suspend fun nextMessagePosition(chatId: String): Long

    suspend fun previousMessagePosition(chatId: String): Long

    suspend fun incrementUnreadCount(chatId: String, incrementBy: Int)

    suspend fun markChatOpened(chatId: String)

    suspend fun updateChatTitle(chatId: String, title: String)

    suspend fun updateInvitationStatus(chatId: String, status: String)

    suspend fun clearAll()
}
