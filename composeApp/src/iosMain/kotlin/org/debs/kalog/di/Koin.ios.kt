package org.debs.kalog.di

import org.debs.kalog.app.AppExitManager
import org.debs.kalog.app.IosAppExitManager
import org.debs.kalog.core.database.DatabaseDriverFactory
import org.debs.kalog.core.database.IosDatabaseDriverFactory
import org.debs.kalog.core.network.client.DarwinPlatformHttpClientFactory
import org.debs.kalog.core.network.client.PlatformHttpClientFactory
import org.debs.kalog.core.preferences.IosSecureSettingsFactory
import org.debs.kalog.core.preferences.IosSettingsFactory
import org.debs.kalog.core.preferences.SecureSettingsFactory
import org.debs.kalog.core.preferences.SettingsFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModules(): List<Module> {
    return listOf(
        module {
            single<AppExitManager> { IosAppExitManager() }
            single<DatabaseDriverFactory> { IosDatabaseDriverFactory() }
            single<PlatformHttpClientFactory> { DarwinPlatformHttpClientFactory() }
            single<SettingsFactory> { IosSettingsFactory() }
            single<SecureSettingsFactory> { IosSecureSettingsFactory() }
        },
    )
}
