package org.debs.kalog.feature.chat.data.preferences

import kotlinx.coroutines.flow.Flow

interface ChatPreferencesDataSource {
    fun observeLastOpenedChatId(): Flow<String?>

    suspend fun currentOpenedChatId(): String?

    suspend fun saveLastOpenedChatId(chatId: String)

    suspend fun clearLastOpenedChatId()

    fun observeNickname(): Flow<String>

    suspend fun getNickname(): String

    suspend fun saveNickname(nickname: String)

    suspend fun getUserNickname(userId: String): String?

    suspend fun saveUserNickname(userId: String, nickname: String)

    suspend fun getChatTitle(chatId: String): String?

    suspend fun saveChatTitle(chatId: String, title: String)

    suspend fun clearAll()

    suspend fun isDebugModeEnabled(): Boolean

    suspend fun setDebugModeEnabled(enabled: Boolean)
}
