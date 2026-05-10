package org.debs.kalog.core.network.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import org.debs.kalog.core.network.config.NetworkConfig

class OkHttpPlatformHttpClientFactory(
    private val networkConfig: NetworkConfig = NetworkConfig(),
) : PlatformHttpClientFactory {
    override fun create(config: HttpClientConfig<*>.() -> Unit): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(networkConfig.connectTimeoutMillis, TimeUnit.MILLISECONDS)
                    readTimeout(LONG_TRANSFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    writeTimeout(LONG_TRANSFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    callTimeout(0L, TimeUnit.MILLISECONDS)
                }
            }
            config(this)
        }
    }

    private companion object {
        private const val LONG_TRANSFER_TIMEOUT_MS = 60 * 60 * 1000L
    }
}
