package org.debs.kalog.core.preferences

import kotlinx.coroutines.flow.Flow

interface KeyValueStorage {
    fun observeString(key: String, defaultValue: String = ""): Flow<String>

    fun observeBoolean(key: String, defaultValue: Boolean = false): Flow<Boolean>

    suspend fun getStringOrNull(key: String): String?

    suspend fun getBoolean(key: String, defaultValue: Boolean = false): Boolean

    suspend fun putString(key: String, value: String)

    suspend fun putBoolean(key: String, value: Boolean)

    suspend fun remove(key: String)

    suspend fun clear()
}
