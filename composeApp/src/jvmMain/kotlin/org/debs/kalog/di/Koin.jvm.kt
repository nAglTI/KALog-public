package org.debs.kalog.di

import org.debs.kalog.app.AppExitManager
import org.debs.kalog.app.JvmAppExitManager
import org.debs.kalog.core.database.DatabaseDriverFactory
import org.debs.kalog.core.database.JvmDatabaseDriverFactory
import org.debs.kalog.core.network.client.OkHttpPlatformHttpClientFactory
import org.debs.kalog.core.network.client.PlatformHttpClientFactory
import org.debs.kalog.core.preferences.JvmSecureSettingsFactory
import org.debs.kalog.core.preferences.JvmSettingsFactory
import org.debs.kalog.core.preferences.SecureSettingsFactory
import org.debs.kalog.core.preferences.SettingsFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModules(): List<Module> {
    return listOf(
        module {
            single<AppExitManager> { JvmAppExitManager() }
            single<DatabaseDriverFactory> { JvmDatabaseDriverFactory() }
            single<PlatformHttpClientFactory> { OkHttpPlatformHttpClientFactory() }
            single<SettingsFactory> { JvmSettingsFactory("org.debs.kalog") }
            single<SecureSettingsFactory> { JvmSecureSettingsFactory("org.debs.kalog") }
        },
    )
}
