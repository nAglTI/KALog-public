package org.debs.kalog.feature.chat.presentation.platform

interface DesktopAutostartManager {
    val isSupported: Boolean

    suspend fun isEnabled(): Boolean

    suspend fun setEnabled(enabled: Boolean): Boolean
}

internal class NoOpDesktopAutostartManager : DesktopAutostartManager {
    override val isSupported: Boolean = false

    override suspend fun isEnabled(): Boolean = false

    override suspend fun setEnabled(enabled: Boolean): Boolean = false
}

internal expect fun createDesktopAutostartManager(): DesktopAutostartManager
