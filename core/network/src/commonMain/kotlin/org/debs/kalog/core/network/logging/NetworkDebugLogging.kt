package org.debs.kalog.core.network.logging

import kotlinx.coroutines.flow.MutableStateFlow

object NetworkLoggingConfig {
    private val debugBuildOverride = MutableStateFlow<Boolean?>(null)

    fun setDebugBuild(isDebugBuild: Boolean) {
        debugBuildOverride.value = isDebugBuild
    }

    internal fun resolveDebugBuild(): Boolean? = debugBuildOverride.value
}

internal expect fun defaultPlatformDebugBuild(): Boolean

internal val isPlatformDebugBuild: Boolean
    get() = NetworkLoggingConfig.resolveDebugBuild() ?: defaultPlatformDebugBuild()

internal expect fun platformHttpLog(message: String)

fun debugHttpLog(message: String) {
    if (isPlatformDebugBuild) {
        platformHttpLog(message)
    }
}
