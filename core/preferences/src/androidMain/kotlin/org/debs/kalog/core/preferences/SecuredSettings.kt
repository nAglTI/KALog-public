@file:OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)

package org.debs.kalog.core.preferences

import androidx.datastore.preferences.core.stringPreferencesKey
import com.russhwolf.settings.coroutines.FlowSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

internal class SecuredSettings(
    private val securedDataStore: SecuredDataStore,
) : FlowSettings {
    override suspend fun keys(): Set<String> = securedDataStore.keys()

    override suspend fun size(): Int = securedDataStore.size()

    override suspend fun clear() {
        securedDataStore.clearAllPreference()
    }

    override suspend fun remove(key: String) {
        securedDataStore.removePreference(stringPreferencesKey(key))
    }

    override suspend fun hasKey(key: String): Boolean = securedDataStore.hasKey(stringPreferencesKey(key))

    override suspend fun putInt(key: String, value: Int) {
        securedDataStore.putSecurePreference(stringPreferencesKey(key), value, Int.serializer())
    }

    override fun getIntFlow(key: String, defaultValue: Int): Flow<Int> =
        securedDataStore.getSecurePreference(stringPreferencesKey(key), defaultValue, Int.serializer())

    override fun getIntOrNullFlow(key: String): Flow<Int?> =
        securedDataStore.getSecurePreference(stringPreferencesKey(key), null, Int.serializer().nullable)

    override suspend fun putLong(key: String, value: Long) {
        securedDataStore.putSecurePreference(stringPreferencesKey(key), value, Long.serializer())
    }

    override fun getLongFlow(key: String, defaultValue: Long): Flow<Long> =
        securedDataStore.getSecurePreference(stringPreferencesKey(key), defaultValue, Long.serializer())

    override fun getLongOrNullFlow(key: String): Flow<Long?> =
        securedDataStore.getSecurePreference(stringPreferencesKey(key), null, Long.serializer().nullable)

    override suspend fun putString(key: String, value: String) {
        securedDataStore.putSecurePreference(stringPreferencesKey(key), value, String.serializer())
    }

    override fun getStringFlow(key: String, defaultValue: String): Flow<String> =
        securedDataStore.getSecurePreference(stringPreferencesKey(key), defaultValue, String.serializer())

    override fun getStringOrNullFlow(key: String): Flow<String?> =
        securedDataStore.getSecurePreference(stringPreferencesKey(key), null, String.serializer().nullable)

    override suspend fun putFloat(key: String, value: Float) {
        securedDataStore.putSecurePreference(stringPreferencesKey(key), value, Float.serializer())
    }

    override fun getFloatFlow(key: String, defaultValue: Float): Flow<Float> =
        securedDataStore.getSecurePreference(stringPreferencesKey(key), defaultValue, Float.serializer())

    override fun getFloatOrNullFlow(key: String): Flow<Float?> =
        securedDataStore.getSecurePreference(stringPreferencesKey(key), null, Float.serializer().nullable)

    override suspend fun putDouble(key: String, value: Double) {
        securedDataStore.putSecurePreference(stringPreferencesKey(key), value, Double.serializer())
    }

    override fun getDoubleFlow(key: String, defaultValue: Double): Flow<Double> =
        securedDataStore.getSecurePreference(stringPreferencesKey(key), defaultValue, Double.serializer())

    override fun getDoubleOrNullFlow(key: String): Flow<Double?> =
        securedDataStore.getSecurePreference(stringPreferencesKey(key), null, Double.serializer().nullable)

    override suspend fun putBoolean(key: String, value: Boolean) {
        securedDataStore.putSecurePreference(stringPreferencesKey(key), value, Boolean.serializer())
    }

    override fun getBooleanFlow(key: String, defaultValue: Boolean): Flow<Boolean> =
        securedDataStore.getSecurePreference(stringPreferencesKey(key), defaultValue, Boolean.serializer())

    override fun getBooleanOrNullFlow(key: String): Flow<Boolean?> =
        securedDataStore.getSecurePreference(stringPreferencesKey(key), null, Boolean.serializer().nullable)
}
