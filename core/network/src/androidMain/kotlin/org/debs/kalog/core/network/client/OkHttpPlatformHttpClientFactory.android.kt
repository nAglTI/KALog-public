package org.debs.kalog.core.network.client

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import org.debs.kalog.core.network.config.NetworkConfig
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

class OkHttpPlatformHttpClientFactory(
    private val networkConfig: NetworkConfig,
) : PlatformHttpClientFactory {
    override fun create(config: HttpClientConfig<*>.() -> Unit): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                config {
                    dns(AndroidFallbackDns(networkConfig.dnsFallbackHosts))
                }
            }
            config(this)
        }
    }
}

private class AndroidFallbackDns(
    private val fallbackHosts: Map<String, List<String>>,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            Dns.SYSTEM.lookup(hostname)
        } catch (error: UnknownHostException) {
            val fallbackAddresses = fallbackHosts[hostname].orEmpty()
            if (fallbackAddresses.isEmpty()) throw error

            Log.w("KALogDns", "System DNS failed for $hostname, using fallback addresses: $fallbackAddresses", error)
            fallbackAddresses.map(InetAddress::getByName)
        }
    }
}
