package org.debs.kalog.core.network.config

data class NetworkConfig(
    // FIXME: Switch to HTTPS/TLS-backed endpoints for production environments.
    val baseUrl: String = "https://api.kalog.local/",
    val connectTimeoutMillis: Long = 15_000,
    val requestTimeoutMillis: Long = 15_000,
    val socketTimeoutMillis: Long = 15_000,
    val enableLogging: Boolean = true,
    val dnsFallbackHosts: Map<String, List<String>> = mapOf(
        "dnsserver.kalog.local" to listOf("14.88.13.37"),
    ),
)
