package org.debs.kalog.core.network.config

internal fun configuredNetworkEnvironment(): NetworkEnvironment {
    return NetworkEnvironment.Default.copy(
        baseUrl = NetworkBuildKonfig.NETWORK_BASE_URL,
        dnsFallbackHosts = NetworkBuildKonfig.NETWORK_DNS_FALLBACK_HOSTS.toDnsFallbackHosts(),
    )
}

private fun String.toDnsFallbackHosts(): Map<String, List<String>> {
    if (isBlank()) return emptyMap()

    return split(';')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .associate { entry ->
            val separatorIndex = entry.indexOf('=')
            require(separatorIndex > 0) {
                "Invalid kalog.network.dnsFallbackHosts entry '$entry'. Use host=ip1,ip2;other-host=ip3."
            }

            val host = entry.take(separatorIndex).trim()
            val addresses = entry
                .drop(separatorIndex + 1)
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)

            require(addresses.isNotEmpty()) {
                "Invalid kalog.network.dnsFallbackHosts entry '$entry'. At least one address is required."
            }

            host to addresses
        }
}
