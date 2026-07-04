package org.debs.kalog.feature.chat.data.preferences

import kotlinx.coroutines.flow.map
import org.debs.kalog.core.preferences.KeyValueStorage
import org.debs.kalog.core.preferences.SecureKeyValueStorage

class SettingsChatPreferencesDataSource(
    private val keyValueStorage: KeyValueStorage,
    private val secureKeyValueStorage: SecureKeyValueStorage,
) : ChatPreferencesDataSource {
    override fun observeLastOpenedChatId() = keyValueStorage
        .observeString(LAST_OPENED_CHAT_ID, "")
        .map { it.ifBlank { null } }

    override suspend fun currentOpenedChatId(): String? {
        return keyValueStorage.getStringOrNull(LAST_OPENED_CHAT_ID)?.ifBlank { null }
    }

    override suspend fun saveLastOpenedChatId(chatId: String) {
        keyValueStorage.putString(LAST_OPENED_CHAT_ID, chatId)
    }

    override suspend fun clearLastOpenedChatId() {
        keyValueStorage.remove(LAST_OPENED_CHAT_ID)
    }

    override suspend fun getLastPollTimestamp(): String? {
        return keyValueStorage.getStringOrNull(LAST_POLL_TIMESTAMP)?.ifBlank { null }
    }

    override suspend fun saveLastPollTimestamp(timestamp: String) {
        keyValueStorage.putString(LAST_POLL_TIMESTAMP, timestamp)
    }

    override suspend fun clearLastPollTimestamp() {
        keyValueStorage.remove(LAST_POLL_TIMESTAMP)
    }

    override fun observeNickname() = keyValueStorage
        .observeString(NICKNAME, "")

    override suspend fun getNickname(): String {
        return keyValueStorage.getStringOrNull(NICKNAME).orEmpty()
    }

    override suspend fun saveNickname(nickname: String) {
        keyValueStorage.putString(NICKNAME, nickname)
    }

    override suspend fun getUserNickname(userId: String): String? {
        return secureKeyValueStorage.getStringOrNull(userNicknameKey(userId))?.ifBlank { null }
    }

    override suspend fun saveUserNickname(userId: String, nickname: String) {
        secureKeyValueStorage.putString(userNicknameKey(userId), nickname)
    }

    override suspend fun getChatTitle(chatId: String): String? {
        return keyValueStorage.getStringOrNull(chatTitleKey(chatId))?.ifBlank { null }
    }

    override suspend fun saveChatTitle(chatId: String, title: String) {
        keyValueStorage.putString(chatTitleKey(chatId), title)
    }

    override suspend fun clearAll() {
        clearLastOpenedChatId()
        clearLastPollTimestamp()
    }

    override suspend fun isDebugModeEnabled(): Boolean {
        return keyValueStorage.getStringOrNull(DEBUG_MODE) == "true"
    }

    override suspend fun setDebugModeEnabled(enabled: Boolean) {
        keyValueStorage.putString(DEBUG_MODE, if (enabled) "true" else "false")
    }

    override suspend fun getMediaCacheRetentionDays(): Int {
        return keyValueStorage.getStringOrNull(MEDIA_CACHE_RETENTION_DAYS)
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: DEFAULT_MEDIA_CACHE_RETENTION_DAYS
    }

    override suspend fun saveMediaCacheRetentionDays(days: Int) {
        keyValueStorage.putString(MEDIA_CACHE_RETENTION_DAYS, days.coerceAtLeast(0).toString())
    }

    override fun observeThemeMode() = keyValueStorage
        .observeString(APP_THEME_MODE, AppThemeMode.System.storageValue)
        .map(AppThemeMode::fromStorage)

    override suspend fun getThemeMode(): AppThemeMode {
        return AppThemeMode.fromStorage(keyValueStorage.getStringOrNull(APP_THEME_MODE))
    }

    override suspend fun saveThemeMode(themeMode: AppThemeMode) {
        keyValueStorage.putString(APP_THEME_MODE, themeMode.storageValue)
    }

    override fun observeDesktopAutostartEnabled() = keyValueStorage
        .observeString(DESKTOP_AUTOSTART_ENABLED, "true")
        .map { it != "false" }

    override suspend fun isDesktopAutostartEnabled(): Boolean {
        return keyValueStorage.getStringOrNull(DESKTOP_AUTOSTART_ENABLED) != "false"
    }

    override suspend fun setDesktopAutostartEnabled(enabled: Boolean) {
        keyValueStorage.putString(DESKTOP_AUTOSTART_ENABLED, if (enabled) "true" else "false")
    }

    override suspend fun isAccountOnboardingCompleted(): Boolean {
        return keyValueStorage.getStringOrNull(ACCOUNT_ONBOARDING_COMPLETED) == "true"
    }

    override suspend fun setAccountOnboardingCompleted(completed: Boolean) {
        keyValueStorage.putString(ACCOUNT_ONBOARDING_COMPLETED, if (completed) "true" else "false")
    }

    private fun userNicknameKey(userId: String) = "$USER_NICKNAME_PREFIX$userId"

    private fun chatTitleKey(chatId: String) = "$CHAT_TITLE_PREFIX$chatId"

    private companion object {
        private const val LAST_OPENED_CHAT_ID = "chat.last_opened_id"
        private const val LAST_POLL_TIMESTAMP = "chat.last_poll_timestamp"
        private const val NICKNAME = "user.nickname"
        private const val USER_NICKNAME_PREFIX = "user.nickname."
        private const val CHAT_TITLE_PREFIX = "chat.title."
        private const val DEBUG_MODE = "chat.debug_mode"
        private const val MEDIA_CACHE_RETENTION_DAYS = "chat.media_cache.retention_days"
        private const val APP_THEME_MODE = "app.theme.mode"
        private const val DESKTOP_AUTOSTART_ENABLED = "desktop.autostart.enabled"
        private const val ACCOUNT_ONBOARDING_COMPLETED = "account.onboarding.completed"
        private const val DEFAULT_MEDIA_CACHE_RETENTION_DAYS = 7
    }
}
