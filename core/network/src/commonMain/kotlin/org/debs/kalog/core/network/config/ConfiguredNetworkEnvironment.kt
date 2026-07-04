package org.debs.kalog.core.network.config

internal fun configuredNetworkEnvironment(): NetworkEnvironment {
    return NetworkEnvironment.Default.copy(
        baseUrl = NetworkBuildKonfig.NETWORK_BASE_URL,
    )
}
