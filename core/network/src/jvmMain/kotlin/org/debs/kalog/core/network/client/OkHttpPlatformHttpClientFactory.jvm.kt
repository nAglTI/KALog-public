package org.debs.kalog.core.network.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

class OkHttpPlatformHttpClientFactory : PlatformHttpClientFactory {
    override fun create(config: HttpClientConfig<*>.() -> Unit): HttpClient {
        return HttpClient(OkHttp) {
            config(this)
        }
    }
}
