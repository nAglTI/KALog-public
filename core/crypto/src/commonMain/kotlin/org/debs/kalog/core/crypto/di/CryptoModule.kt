package org.debs.kalog.core.crypto.di

import org.debs.kalog.core.crypto.EncryptionService
import org.debs.kalog.core.crypto.createEncryptionService
import org.koin.dsl.module

val cryptoModule = module {
    single<EncryptionService> { createEncryptionService() }
}
