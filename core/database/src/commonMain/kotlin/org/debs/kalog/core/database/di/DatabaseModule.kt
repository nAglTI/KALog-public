package org.debs.kalog.core.database.di

import org.debs.kalog.core.database.DatabaseDriverFactory
import org.debs.kalog.core.database.KalogDatabase
import org.koin.dsl.module

val databaseModule = module {
    single { KalogDatabase(get<DatabaseDriverFactory>().create()) }
}
