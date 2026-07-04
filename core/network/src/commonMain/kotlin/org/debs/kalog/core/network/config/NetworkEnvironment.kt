package org.debs.kalog.core.network.config

data class NetworkEnvironment(
    val name: String,
    val baseUrl: String,
    val connectTimeoutMillis: Long,
    val requestTimeoutMillis: Long,
    val socketTimeoutMillis: Long,
    val enableLogging: Boolean,
    val dnsFallbackHosts: Map<String, List<String>>,
) {
    fun resolveUrl(path: String): String {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        check(normalizedBaseUrl.isNotBlank()) {
            "Network base URL is not configured. Set kalog.network.baseUrl in local.properties."
        }
        return "$normalizedBaseUrl/${path.trimStart('/')}"
    }

    companion object {
        val Default = NetworkEnvironment(
            name = "default",
            baseUrl = "",
            connectTimeoutMillis = 15_000,
            requestTimeoutMillis = 15_000,
            socketTimeoutMillis = 15_000,
            enableLogging = false,
            dnsFallbackHosts = emptyMap(),
        )
    }
}
