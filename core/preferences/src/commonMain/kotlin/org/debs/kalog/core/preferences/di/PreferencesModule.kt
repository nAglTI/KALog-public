@file:OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)

package org.debs.kalog.core.preferences.di

import com.russhwolf.settings.coroutines.FlowSettings
import com.russhwolf.settings.coroutines.SuspendSettings
import org.debs.kalog.core.preferences.KeyValueStorage
import org.debs.kalog.core.preferences.SecureKeyValueStorage
import org.debs.kalog.core.preferences.SettingsKeyValueStorage
import org.debs.kalog.core.preferences.SettingsSecureKeyValueStorage
import org.koin.dsl.module
import org.koin.core.qualifier.named

private const val DEFAULT_SETTINGS_NAME = "kalog_preferences"
private const val DEFAULT_SECURE_SETTINGS_NAME = "kalog_secure_preferences"
private val secureSettingsQualifier = named("secure_settings")

val preferencesModule = module {
    single<FlowSettings> { get<org.debs.kalog.core.preferences.SettingsFactory>().create(DEFAULT_SETTINGS_NAME) }
    single<SuspendSettings>(secureSettingsQualifier) {
        get<org.debs.kalog.core.preferences.SecureSettingsFactory>().create(DEFAULT_SECURE_SETTINGS_NAME)
    }
    single<KeyValueStorage> { SettingsKeyValueStorage(get()) }
    single<SecureKeyValueStorage> { SettingsSecureKeyValueStorage(get(secureSettingsQualifier)) }
}
