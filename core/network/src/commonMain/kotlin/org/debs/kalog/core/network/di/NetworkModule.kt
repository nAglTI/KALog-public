package org.debs.kalog.core.network.di

import kotlinx.serialization.json.Json
import org.debs.kalog.core.network.client.KtorSecureApiClient
import org.debs.kalog.core.network.client.SecureApiClient
import org.debs.kalog.core.network.client.SecureHttpClientFactory
import org.debs.kalog.core.network.config.configuredNetworkEnvironment
import org.debs.kalog.core.network.security.CipherNetworkSecurityProvider
import org.debs.kalog.core.network.security.NetworkSecurityProvider
import org.debs.kalog.core.network.security.NoOpTransportKeyProvider
import org.koin.dsl.module

val networkModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
    single { configuredNetworkEnvironment() }
    single { NoOpTransportKeyProvider() }
    single<NetworkSecurityProvider> { CipherNetworkSecurityProvider(get(), getOrNull() ?: get<NoOpTransportKeyProvider>()) }
    single { SecureHttpClientFactory(get(), get(), get()) }
    single { get<SecureHttpClientFactory>().create() }
    single<SecureApiClient> { KtorSecureApiClient(get(), get(), getOrNull() ?: get<NoOpTransportKeyProvider>()) }
}
