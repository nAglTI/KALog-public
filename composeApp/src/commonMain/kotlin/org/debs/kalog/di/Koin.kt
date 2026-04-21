package org.debs.kalog.di

import org.debs.kalog.core.crypto.di.cryptoModule
import org.debs.kalog.core.database.di.databaseModule
import org.debs.kalog.core.network.di.networkModule
import org.debs.kalog.core.preferences.di.preferencesModule
import org.debs.kalog.feature.chat.di.chatFeatureModule
//import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.mp.KoinPlatformTools

private val sharedModules = listOf(
    cryptoModule,
    databaseModule,
    networkModule,
    preferencesModule,
    chatFeatureModule,
)

fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return

    startKoin {
        appDeclaration()
        modules(sharedModules + platformModules())
    }
}

expect fun platformModules(): List<Module>
