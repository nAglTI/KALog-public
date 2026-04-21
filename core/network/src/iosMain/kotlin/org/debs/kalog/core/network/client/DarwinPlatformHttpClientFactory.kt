package org.debs.kalog.core.network.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

class DarwinPlatformHttpClientFactory : PlatformHttpClientFactory {
    override fun create(config: HttpClientConfig<*>.() -> Unit): HttpClient {
        return HttpClient(Darwin) {
            config(this)
        }
    }
}
