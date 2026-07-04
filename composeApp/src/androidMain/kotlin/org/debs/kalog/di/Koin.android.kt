package org.debs.kalog.di

import org.debs.kalog.app.AndroidAppExitManager
import org.debs.kalog.app.AppExitManager
import org.debs.kalog.core.database.AndroidDatabaseDriverFactory
import org.debs.kalog.core.database.DatabaseDriverFactory
import org.debs.kalog.core.network.client.OkHttpPlatformHttpClientFactory
import org.debs.kalog.core.network.client.PlatformHttpClientFactory
import org.debs.kalog.core.preferences.AndroidSecureSettingsFactory
import org.debs.kalog.core.preferences.AndroidSettingsFactory
import org.debs.kalog.core.preferences.SecureKeyValueStorageFactory
import org.debs.kalog.core.preferences.SecureSettingsFactory
import org.debs.kalog.core.preferences.SettingsFactory
import org.debs.kalog.core.preferences.SettingsSecureKeyValueStorageFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModules(): List<Module> {
    return listOf(
        module {
            single<AppExitManager> { AndroidAppExitManager() }
            single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidContext()) }
            single<PlatformHttpClientFactory> { OkHttpPlatformHttpClientFactory(get()) }
            single<SettingsFactory> { AndroidSettingsFactory(androidContext()) }
            single<SecureSettingsFactory> { AndroidSecureSettingsFactory(androidContext()) }
            single<SecureKeyValueStorageFactory> { SettingsSecureKeyValueStorageFactory(get()) }
        },
    )
}
