@file:OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)

package org.debs.kalog.core.preferences

import com.russhwolf.settings.coroutines.SuspendSettings

interface SecureSettingsFactory {
    fun create(name: String): SuspendSettings
}
