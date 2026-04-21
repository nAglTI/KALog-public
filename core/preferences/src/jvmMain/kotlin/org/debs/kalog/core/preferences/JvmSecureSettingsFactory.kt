@file:OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)

package org.debs.kalog.core.preferences

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.coroutines.toSuspendSettings
import java.util.prefs.Preferences

class JvmSecureSettingsFactory(
    private val applicationId: String,
) : SecureSettingsFactory {
    override fun create(name: String) = PreferencesSettings(
        // TODO replace with OS-level secure storage for desktop once platform requirements are defined.
        Preferences.userRoot().node("$applicationId.secure.$name"),
    ).toSuspendSettings()
}
