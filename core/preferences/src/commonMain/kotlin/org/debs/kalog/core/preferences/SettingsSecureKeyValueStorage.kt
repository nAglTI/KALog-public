@file:OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)

package org.debs.kalog.core.preferences

import com.russhwolf.settings.coroutines.SuspendSettings

class SettingsSecureKeyValueStorage(
    private val settings: SuspendSettings,
) : SecureKeyValueStorage {
    override suspend fun getStringOrNull(key: String): String? = settings.getStringOrNull(key)

    override suspend fun putString(key: String, value: String) {
        settings.putString(key, value)
    }

    override suspend fun remove(key: String) {
        settings.remove(key)
    }

    override suspend fun clear() {
        settings.clear()
    }
}
