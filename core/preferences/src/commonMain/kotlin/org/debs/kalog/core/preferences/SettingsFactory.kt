@file:OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)

package org.debs.kalog.core.preferences

import com.russhwolf.settings.coroutines.FlowSettings

interface SettingsFactory {
    fun create(name: String): FlowSettings
}
