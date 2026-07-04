package org.debs.kalog.core.network.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dispatcher
import java.util.concurrent.TimeUnit
import org.debs.kalog.core.network.config.NetworkEnvironment

class OkHttpPlatformHttpClientFactory(
    private val networkEnvironment: NetworkEnvironment = NetworkEnvironment.Default,
) : PlatformHttpClientFactory {
    override fun create(config: HttpClientConfig<*>.() -> Unit): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                config {
                    dispatcher(
                        Dispatcher().apply {
                            maxRequests = MAX_PARALLEL_REQUESTS
                            maxRequestsPerHost = MAX_PARALLEL_REQUESTS_PER_HOST
                        },
                    )
                    connectTimeout(networkEnvironment.connectTimeoutMillis, TimeUnit.MILLISECONDS)
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
        private const val MAX_PARALLEL_REQUESTS = 64
        private const val MAX_PARALLEL_REQUESTS_PER_HOST = 16
    }
}
