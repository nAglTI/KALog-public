package org.debs.kalog.core.network.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

interface PlatformHttpClientFactory {
    fun create(config: HttpClientConfig<*>.() -> Unit = {}): HttpClient
}
