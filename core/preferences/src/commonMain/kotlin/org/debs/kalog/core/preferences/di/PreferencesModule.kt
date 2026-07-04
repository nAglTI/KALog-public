@file:OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)

package org.debs.kalog.core.preferences.di

import com.russhwolf.settings.coroutines.FlowSettings
import org.debs.kalog.core.preferences.KeyValueStorage
import org.debs.kalog.core.preferences.SecureKeyValueStorage
import org.debs.kalog.core.preferences.SecureKeyValueStorageFactory
import org.debs.kalog.core.preferences.SettingsKeyValueStorage
import org.koin.dsl.module

private const val DEFAULT_SETTINGS_NAME = "kalog_preferences"
private const val DEFAULT_SECURE_SETTINGS_NAME = "kalog_secure_preferences"

val preferencesModule = module {
    single<FlowSettings> { get<org.debs.kalog.core.preferences.SettingsFactory>().create(DEFAULT_SETTINGS_NAME) }
    single<KeyValueStorage> { SettingsKeyValueStorage(get()) }
    single<SecureKeyValueStorage> {
        get<SecureKeyValueStorageFactory>().create(DEFAULT_SECURE_SETTINGS_NAME)
    }
}
