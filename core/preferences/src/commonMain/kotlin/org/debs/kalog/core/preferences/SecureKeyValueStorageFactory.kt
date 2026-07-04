@file:OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)

package org.debs.kalog.core.preferences

interface SecureKeyValueStorageFactory {
    fun create(name: String): SecureKeyValueStorage
}

class SettingsSecureKeyValueStorageFactory(
    private val settingsFactory: SecureSettingsFactory,
) : SecureKeyValueStorageFactory {
    override fun create(name: String): SecureKeyValueStorage {
        return SettingsSecureKeyValueStorage(settingsFactory.create(name))
    }
}
