package org.debs.kalog.core.network.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.debs.kalog.core.network.config.NetworkConfig

class SecureHttpClientFactory(
    private val platformHttpClientFactory: PlatformHttpClientFactory,
    private val networkConfig: NetworkConfig,
    private val json: Json,
) {
    fun create(): HttpClient {
        return platformHttpClientFactory.create {
            expectSuccess = false

            install(ContentNegotiation) {
                json(json)
            }
            install(WebSockets)
            install(HttpTimeout) {
                connectTimeoutMillis = networkConfig.connectTimeoutMillis
                requestTimeoutMillis = networkConfig.requestTimeoutMillis
                socketTimeoutMillis = networkConfig.socketTimeoutMillis
            }
        }
    }
}
