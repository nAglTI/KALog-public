@file:OptIn(
    com.russhwolf.settings.ExperimentalSettingsApi::class,
    com.russhwolf.settings.ExperimentalSettingsImplementation::class,
)

package org.debs.kalog.core.preferences

import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import com.russhwolf.settings.coroutines.toSuspendSettings

class IosSecureSettingsFactory(
    private val appId: String = "org.debs.kalog",
) : SecureSettingsFactory {
    override fun create(name: String) = KeychainSettings("$appId.$name").toSuspendSettings()
}
