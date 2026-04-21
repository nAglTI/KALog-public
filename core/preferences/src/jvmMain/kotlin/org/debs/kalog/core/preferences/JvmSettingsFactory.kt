@file:OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)

package org.debs.kalog.core.preferences

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import java.util.prefs.Preferences

class JvmSettingsFactory(
    private val applicationId: String,
) : SettingsFactory {
    override fun create(name: String) = PreferencesSettings(
        Preferences.userRoot().node("$applicationId.$name"),
    ).toFlowSettings()
}
