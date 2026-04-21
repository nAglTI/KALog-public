package org.debs.kalog.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

internal interface SecuredDataStore {
    suspend fun keys(): Set<String>

    suspend fun size(): Int

    suspend fun hasKey(key: Preferences.Key<String>): Boolean

    suspend fun <T> putSecurePreference(key: Preferences.Key<String>, value: T, serializer: KSerializer<T>)

    fun <T> getSecurePreference(key: Preferences.Key<String>, defaultValue: T, serializer: KSerializer<T>): Flow<T>

    suspend fun <T> removePreference(key: Preferences.Key<T>)

    suspend fun clearAllPreference()
}

internal class SecuredDataStoreImpl(
    name: String,
    private val context: Context,
    private val securityDataUtils: SecurityDataUtils,
    private val json: Json,
) : SecuredDataStore {
    private val dataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { context.preferencesDataStoreFile("$name.preferences_pb") },
    )
    private val bytesSeparator = "|"
    private val ivSeparator = ":iv:"

    override suspend fun keys(): Set<String> = dataStore.data.first().asMap().keys.map(Preferences.Key<*>::name).toSet()

    override suspend fun size(): Int = dataStore.data.first().asMap().size

    override suspend fun hasKey(key: Preferences.Key<String>): Boolean {
        return dataStore.data.firstOrNull()?.contains(key) ?: false
    }

    override suspend fun <T> putSecurePreference(
        key: Preferences.Key<String>,
        value: T,
        serializer: KSerializer<T>,
    ) {
        dataStore.edit { preferences ->
            val serializedValue = json.encodeToString(serializer, value)
            val (iv, encryptedData) = securityDataUtils.encryptData(serializedValue)
            preferences[key] = encodeSecuredValue(iv, encryptedData)
        }
    }

    override fun <T> getSecurePreference(
        key: Preferences.Key<String>,
        defaultValue: T,
        serializer: KSerializer<T>,
    ): Flow<T> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val securedValue = preferences[key] ?: return@map defaultValue
            val decryptedValue = decodeSecuredValue(securedValue)?.let { (iv, encryptedData) ->
                runCatching { securityDataUtils.decryptData(iv, encryptedData) }.getOrNull()
            } ?: return@map defaultValue

            runCatching { json.decodeFromString(serializer, decryptedValue) }
                .getOrElse { defaultValue }
        }

    override suspend fun <T> removePreference(key: Preferences.Key<T>) {
        dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    override suspend fun clearAllPreference() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private fun encodeSecuredValue(iv: ByteArray, encryptedData: ByteArray): String {
        val ivValue = iv.joinToString(separator = bytesSeparator)
        val encryptedValue = encryptedData.joinToString(separator = bytesSeparator)
        return "$ivValue$ivSeparator$encryptedValue"
    }

    private fun decodeSecuredValue(value: String): Pair<ByteArray, ByteArray>? {
        val parts = value.split(ivSeparator, limit = 2)
        if (parts.size != 2) return null

        val iv = parts[0].split(bytesSeparator).mapNotNull(String::toByteOrNull).toByteArray()
        val encryptedData = parts[1].split(bytesSeparator).mapNotNull(String::toByteOrNull).toByteArray()
        if (iv.isEmpty() || encryptedData.isEmpty()) return null

        return iv to encryptedData
    }
}
