package org.debs.kalog.core.preferences

import kotlinx.coroutines.flow.Flow

interface SecureKeyValueStorage {
    suspend fun getStringOrNull(key: String): String?

    suspend fun putString(key: String, value: String)

    suspend fun remove(key: String)

    suspend fun clear()
}
