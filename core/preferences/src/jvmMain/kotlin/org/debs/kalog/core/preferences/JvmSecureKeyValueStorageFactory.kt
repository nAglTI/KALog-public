package org.debs.kalog.core.preferences

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.platform.mac.CoreFoundation
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt.CRYPTPROTECT_UI_FORBIDDEN
import com.sun.jna.ptr.PointerByReference
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.prefs.Preferences

class JvmSecureKeyValueStorageFactory(
    private val applicationId: String,
) : SecureKeyValueStorageFactory {
    override fun create(name: String): SecureKeyValueStorage {
        val osName = System.getProperty("os.name").orEmpty()
        val normalizedOsName = osName.lowercase(Locale.US)

        return when {
            normalizedOsName.contains("mac") -> MacOsKeychainSecureKeyValueStorage(
                serviceName = secureStorageName(applicationId, name),
            )

            normalizedOsName.contains("windows") -> WindowsDpapiSecureKeyValueStorage(
                applicationId = applicationId,
                name = name,
            )

            else -> UnsupportedJvmSecureKeyValueStorage(osName)
        }
    }

    private fun secureStorageName(applicationId: String, name: String): String = "$applicationId.$name"
}

private class WindowsDpapiSecureKeyValueStorage(
    applicationId: String,
    name: String,
) : SecureKeyValueStorage {
    private val preferences = Preferences.userRoot().node("$applicationId.secure.$name")
    private val entropy = "$applicationId.$name.secure-storage".toByteArray(StandardCharsets.UTF_8)

    override suspend fun getStringOrNull(key: String): String? {
        val storedValue = preferences.get(key, null) ?: return null
        if (!storedValue.startsWith(PROTECTED_VALUE_PREFIX)) {
            remove(key)
            return null
        }

        val encryptedValue = runCatching {
            Base64.getDecoder().decode(storedValue.removePrefix(PROTECTED_VALUE_PREFIX))
        }.getOrNull() ?: return null

        val decryptedValue = runCatching {
            Crypt32Util.cryptUnprotectData(
                encryptedValue,
                entropy,
                CRYPTPROTECT_UI_FORBIDDEN,
                null,
            )
        }.getOrNull() ?: return null

        return decryptedValue.toString(StandardCharsets.UTF_8)
    }

    override suspend fun putString(key: String, value: String) {
        val encryptedValue = Crypt32Util.cryptProtectData(
            value.toByteArray(StandardCharsets.UTF_8),
            entropy,
            CRYPTPROTECT_UI_FORBIDDEN,
            SECURE_STORAGE_DESCRIPTION,
            null,
        )
        preferences.put(key, PROTECTED_VALUE_PREFIX + Base64.getEncoder().encodeToString(encryptedValue))
        preferences.flush()
    }

    override suspend fun remove(key: String) {
        preferences.remove(key)
        preferences.flush()
    }

    override suspend fun clear() {
        preferences.clear()
        preferences.flush()
    }

    private companion object {
        private const val PROTECTED_VALUE_PREFIX = "dpapi:"
        private const val SECURE_STORAGE_DESCRIPTION = "Mayday Chat secure storage"
    }
}

private class MacOsKeychainSecureKeyValueStorage(
    private val serviceName: String,
) : SecureKeyValueStorage {
    override suspend fun getStringOrNull(key: String): String? {
        val query = passwordQuery(account = key, returnData = true)
        try {
            val result = PointerByReference()
            val status = SecurityFramework.INSTANCE.SecItemCopyMatching(query, result)
            if (status == ERR_SEC_ITEM_NOT_FOUND) return null
            checkKeychainStatus(status, "read")

            val dataPointer = result.value ?: return null
            val data = CoreFoundation.CFDataRef(dataPointer)
            try {
                val byteCount = data.length
                val bytes = data.bytePtr.getByteArray(0, byteCount)
                return bytes.toString(StandardCharsets.UTF_8)
            } finally {
                data.release()
            }
        } finally {
            query.release()
        }
    }

    override suspend fun putString(key: String, value: String) {
        val valueBytes = value.toByteArray(StandardCharsets.UTF_8)
        val query = passwordQuery(account = key, returnData = false)
        try {
            query.setOwnedValue(KeychainConstants.kSecValueData, cfData(valueBytes))
            when (val status = SecurityFramework.INSTANCE.SecItemAdd(query, null)) {
                ERR_SEC_SUCCESS -> Unit
                ERR_SEC_DUPLICATE_ITEM -> updatePassword(key, valueBytes)
                else -> checkKeychainStatus(status, "write")
            }
        } finally {
            query.release()
        }
    }

    override suspend fun remove(key: String) {
        val query = passwordQuery(account = key, returnData = false)
        try {
            val status = SecurityFramework.INSTANCE.SecItemDelete(query)
            if (status != ERR_SEC_ITEM_NOT_FOUND) {
                checkKeychainStatus(status, "delete")
            }
        } finally {
            query.release()
        }
    }

    override suspend fun clear() {
        val query = passwordQuery(account = null, returnData = false)
        try {
            val status = SecurityFramework.INSTANCE.SecItemDelete(query)
            if (status != ERR_SEC_ITEM_NOT_FOUND) {
                checkKeychainStatus(status, "clear")
            }
        } finally {
            query.release()
        }
    }

    private fun updatePassword(key: String, valueBytes: ByteArray) {
        val query = passwordQuery(account = key, returnData = false)
        val attributes = mutableDictionary()
        try {
            attributes.setOwnedValue(KeychainConstants.kSecValueData, cfData(valueBytes))
            checkKeychainStatus(
                status = SecurityFramework.INSTANCE.SecItemUpdate(query, attributes),
                operation = "update",
            )
        } finally {
            attributes.release()
            query.release()
        }
    }

    private fun passwordQuery(
        account: String?,
        returnData: Boolean,
    ): CoreFoundation.CFMutableDictionaryRef {
        return mutableDictionary().apply {
            setSharedValue(KeychainConstants.kSecClass, KeychainConstants.kSecClassGenericPassword)
            setOwnedValue(KeychainConstants.kSecAttrService, cfString(serviceName))
            if (account != null) {
                setOwnedValue(KeychainConstants.kSecAttrAccount, cfString(account))
            }
            if (returnData) {
                setSharedValue(KeychainConstants.kSecReturnData, KeychainConstants.kCFBooleanTrue)
                setSharedValue(KeychainConstants.kSecMatchLimit, KeychainConstants.kSecMatchLimitOne)
            }
        }
    }

    private fun mutableDictionary(): CoreFoundation.CFMutableDictionaryRef {
        return KeychainConstants.coreFoundation.CFDictionaryCreateMutable(
            null,
            CoreFoundation.CFIndex(0),
            KeychainConstants.cfTypeDictionaryKeyCallbacks,
            KeychainConstants.cfTypeDictionaryValueCallbacks,
        ) ?: error("Unable to create macOS Keychain query.")
    }

    private fun cfString(value: String): CoreFoundation.CFStringRef {
        return CoreFoundation.CFStringRef.createCFString(value)
            ?: error("Unable to create CoreFoundation string.")
    }

    private fun cfData(bytes: ByteArray): CoreFoundation.CFDataRef {
        val memory = Memory(bytes.size.toLong().coerceAtLeast(1L))
        if (bytes.isNotEmpty()) {
            memory.write(0, bytes, 0, bytes.size)
        }
        return KeychainConstants.coreFoundation.CFDataCreate(
            null,
            if (bytes.isEmpty()) Pointer.NULL else memory,
            CoreFoundation.CFIndex(bytes.size.toLong()),
        ) ?: error("Unable to create CoreFoundation data.")
    }

    private fun CoreFoundation.CFMutableDictionaryRef.setSharedValue(
        key: CoreFoundation.CFStringRef,
        value: CoreFoundation.CFTypeRef,
    ) {
        setValue(key, value)
    }

    private fun CoreFoundation.CFMutableDictionaryRef.setOwnedValue(
        key: CoreFoundation.CFStringRef,
        value: CoreFoundation.CFTypeRef,
    ) {
        setValue(key, value)
        value.release()
    }

    private fun checkKeychainStatus(status: Int, operation: String) {
        check(status == ERR_SEC_SUCCESS) {
            "macOS Keychain $operation failed with OSStatus $status."
        }
    }

    private companion object {
        private const val ERR_SEC_SUCCESS = 0
        private const val ERR_SEC_DUPLICATE_ITEM = -25299
        private const val ERR_SEC_ITEM_NOT_FOUND = -25300
    }
}

private interface SecurityFramework : Library {
    fun SecItemAdd(
        attributes: CoreFoundation.CFDictionaryRef,
        result: PointerByReference?,
    ): Int

    fun SecItemCopyMatching(
        query: CoreFoundation.CFDictionaryRef,
        result: PointerByReference,
    ): Int

    fun SecItemUpdate(
        query: CoreFoundation.CFDictionaryRef,
        attributesToUpdate: CoreFoundation.CFDictionaryRef,
    ): Int

    fun SecItemDelete(query: CoreFoundation.CFDictionaryRef): Int

    companion object {
        val INSTANCE: SecurityFramework = Native.load("Security", SecurityFramework::class.java)
    }
}

private object KeychainConstants {
    val coreFoundation: CoreFoundation = CoreFoundation.INSTANCE
    val cfTypeDictionaryKeyCallbacks: Pointer = NativeLibrary
        .getInstance("CoreFoundation")
        .getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks")
    val cfTypeDictionaryValueCallbacks: Pointer = NativeLibrary
        .getInstance("CoreFoundation")
        .getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks")
    val kCFBooleanTrue: CoreFoundation.CFBooleanRef = CoreFoundation.CFBooleanRef(
        NativeLibrary.getInstance("CoreFoundation").getGlobalVariableAddress("kCFBooleanTrue").getPointer(0),
    )
    val kSecClass: CoreFoundation.CFStringRef = securityStringConstant("kSecClass")
    val kSecClassGenericPassword: CoreFoundation.CFStringRef = securityStringConstant("kSecClassGenericPassword")
    val kSecAttrService: CoreFoundation.CFStringRef = securityStringConstant("kSecAttrService")
    val kSecAttrAccount: CoreFoundation.CFStringRef = securityStringConstant("kSecAttrAccount")
    val kSecValueData: CoreFoundation.CFStringRef = securityStringConstant("kSecValueData")
    val kSecReturnData: CoreFoundation.CFStringRef = securityStringConstant("kSecReturnData")
    val kSecMatchLimit: CoreFoundation.CFStringRef = securityStringConstant("kSecMatchLimit")
    val kSecMatchLimitOne: CoreFoundation.CFStringRef = securityStringConstant("kSecMatchLimitOne")

    private fun securityStringConstant(name: String): CoreFoundation.CFStringRef {
        return CoreFoundation.CFStringRef(
            NativeLibrary.getInstance("Security").getGlobalVariableAddress(name).getPointer(0),
        )
    }
}

private class UnsupportedJvmSecureKeyValueStorage(
    private val osName: String,
) : SecureKeyValueStorage {
    override suspend fun getStringOrNull(key: String): String? = unavailable()

    override suspend fun putString(key: String, value: String) = unavailable()

    override suspend fun remove(key: String) = unavailable()

    override suspend fun clear() = unavailable()

    private fun unavailable(): Nothing {
        error("Secure storage is unavailable for desktop OS '$osName'.")
    }
}
