@file:OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)

package org.debs.kalog.core.preferences

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.coroutines.FlowSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import platform.Foundation.NSUserDefaults

class IosSettingsFactory(
    private val appId: String = "org.debs.kalog",
) : SettingsFactory {
    override fun create(name: String): FlowSettings {
        val defaults = NSUserDefaults(
            suiteName = "$appId.$name",
        ) ?: NSUserDefaults.standardUserDefaults

        return NSUserDefaultsSettings(defaults).toFlowSettings()
    }
}
