package org.debs.kalog.core.preferences

import com.russhwolf.settings.coroutines.SuspendSettings
import android.content.Context
import com.russhwolf.settings.ExperimentalSettingsApi
import kotlinx.serialization.json.Json

class AndroidSecureSettingsFactory(
    private val context: Context,
) : SecureSettingsFactory {
    @OptIn(ExperimentalSettingsApi::class)
    override fun create(name: String): SuspendSettings = SecuredSettings(
        securedDataStore = SecuredDataStoreImpl(
            name = name,
            context = context,
            securityDataUtils = SecurityDataUtilsImpl(),
            json = Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        ),
    )
}
