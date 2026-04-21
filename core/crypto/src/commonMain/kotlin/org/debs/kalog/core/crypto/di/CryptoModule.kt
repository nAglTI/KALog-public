package org.debs.kalog.core.crypto.di

import org.debs.kalog.core.crypto.EncryptionService
import org.debs.kalog.core.crypto.RsaOaepEncryptionService
import org.koin.dsl.module

val cryptoModule = module {
    single<EncryptionService> { RsaOaepEncryptionService() }
}
