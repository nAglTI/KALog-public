@file:OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)

package org.debs.kalog.core.preferences

import com.russhwolf.settings.coroutines.FlowSettings
import kotlinx.coroutines.flow.first

class SettingsKeyValueStorage(
    private val settings: FlowSettings,
) : KeyValueStorage {
    override fun observeString(key: String, defaultValue: String) = settings.getStringFlow(key, defaultValue)

    override fun observeBoolean(key: String, defaultValue: Boolean) = settings.getBooleanFlow(key, defaultValue)

    override suspend fun getStringOrNull(key: String): String? = settings.getStringOrNullFlow(key).first()

    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean = settings.getBooleanFlow(key, defaultValue).first()

    override suspend fun putString(key: String, value: String) {
        settings.putString(key, value)
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        settings.putBoolean(key, value)
    }

    override suspend fun remove(key: String) {
        settings.remove(key)
    }

    override suspend fun clear() {
        settings.clear()
    }
}
