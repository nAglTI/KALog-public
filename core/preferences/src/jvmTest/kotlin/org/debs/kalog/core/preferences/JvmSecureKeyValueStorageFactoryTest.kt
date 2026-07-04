package org.debs.kalog.core.preferences

import java.util.Locale
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class JvmSecureKeyValueStorageFactoryTest {
    @Test
    fun windowsDpapiStorageRoundTripsSecret() = runBlocking {
        if (!isWindows()) return@runBlocking

        val appId = TEST_APPLICATION_ID
        val name = testStorageName()
        val storage = JvmSecureKeyValueStorageFactory(appId).create(name)

        try {
            storage.putString("private-key", "super-secret-value")

            assertEquals("super-secret-value", storage.getStringOrNull("private-key"))
        } finally {
            storage.clear()
            removePreferencesNode(appId, name)
        }
    }

    @Test
    fun windowsDpapiStorageDoesNotReturnLegacyPlaintextPreference() = runBlocking {
        if (!isWindows()) return@runBlocking

        val appId = TEST_APPLICATION_ID
        val name = testStorageName()
        val key = "legacy-private-key"
        val preferences = securePreferencesNode(appId, name)
        preferences.put(key, "plain-secret")
        preferences.flush()

        val storage = JvmSecureKeyValueStorageFactory(appId).create(name)

        try {
            assertNull(storage.getStringOrNull(key))
            assertNull(preferences.get(key, null))
        } finally {
            storage.clear()
            removePreferencesNode(appId, name)
        }
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").orEmpty().lowercase(Locale.US).contains("windows")
    }

    private fun testStorageName(): String = "secure_test_${System.nanoTime()}"

    private fun securePreferencesNode(appId: String, name: String): Preferences {
        return Preferences.userRoot().node("$appId.secure.$name")
    }

    private fun removePreferencesNode(appId: String, name: String) {
        val node = securePreferencesNode(appId, name)
        node.removeNode()
        Preferences.userRoot().flush()
    }

    private companion object {
        private const val TEST_APPLICATION_ID = "org.debs.kalog.test"
    }
}
