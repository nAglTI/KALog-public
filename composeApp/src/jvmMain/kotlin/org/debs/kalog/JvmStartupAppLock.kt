package org.debs.kalog

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.mac.CoreFoundation
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.swing.JOptionPane

internal object JvmStartupAppLock {
    fun unlockOrExit(): StartupUnlockDecision {
        if (!isWindows() && !isMacOs()) return StartupUnlockDecision(allowed = true)

        val result = runCatching {
            if (isMacOs()) requestMacOsVerification() else requestWindowsHelloVerification()
        }
            .getOrElse { error ->
                StartupUnlockResult(
                    status = StartupUnlockStatus.Blocked,
                    message = error.message ?: error::class.java.simpleName,
                )
            }

        return when (result.status) {
            StartupUnlockStatus.Verified -> StartupUnlockDecision(allowed = true)
            StartupUnlockStatus.ReducedProtection -> StartupUnlockDecision(
                allowed = true,
                showReducedProtectionWarning = true,
            )
            StartupUnlockStatus.Blocked -> {
                JOptionPane.showMessageDialog(
                    null,
                    result.message.ifBlank { APP_LOCK_UNAVAILABLE_MESSAGE },
                    "Mayday Chat",
                    JOptionPane.ERROR_MESSAGE,
                )
                StartupUnlockDecision(allowed = false)
            }
        }
    }

    fun showReducedProtectionWarning() {
        JOptionPane.showMessageDialog(
            null,
            REDUCED_PROTECTION_MESSAGE,
            "Mayday Chat",
            JOptionPane.WARNING_MESSAGE,
        )
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name")
            .orEmpty()
            .lowercase(Locale.US)
            .contains("windows")
    }

    private fun isMacOs(): Boolean {
        return System.getProperty("os.name")
            .orEmpty()
            .lowercase(Locale.US)
            .contains("mac")
    }

    private fun requestWindowsHelloVerification(): StartupUnlockResult {
        val resultWithHelloPackaging = promptForWindowsCredentials(
            CREDUIWIN_ENUMERATE_CURRENT_USER or
                CREDUIWIN_PACK_WINDOWS_HELLO_CREDENTIALS,
        )
        val result = if (resultWithHelloPackaging == ERROR_INVALID_PARAMETER) {
            promptForWindowsCredentials(CREDUIWIN_ENUMERATE_CURRENT_USER)
        } else {
            resultWithHelloPackaging
        }

        return when (result) {
            ERROR_SUCCESS -> StartupUnlockResult(
                status = StartupUnlockStatus.Verified,
                message = "",
            )
            ERROR_CANCELLED -> StartupUnlockResult(
                status = StartupUnlockStatus.Blocked,
                message = "Windows authentication was cancelled.",
            )
            else -> StartupUnlockResult(
                status = StartupUnlockStatus.ReducedProtection,
                message = "Windows authentication failed with status $result.",
            )
        }
    }

    private fun requestMacOsVerification(): StartupUnlockResult {
        val setupStatus = ensureMacOsAppLockItem()
        if (setupStatus != ERR_SEC_SUCCESS && setupStatus != ERR_SEC_DUPLICATE_ITEM) {
            return StartupUnlockResult(
                status = StartupUnlockStatus.ReducedProtection,
                message = "macOS authentication is unavailable with OSStatus $setupStatus.",
            )
        }

        val query = macOsAppLockQuery(returnData = true).apply {
            setOwnedValue(
                MacOsStartupSecurityConstants.kSecUseOperationPrompt,
                cfString("Confirm your macOS identity to open Mayday Chat."),
            )
        }
        try {
            val result = PointerByReference()
            val status = MacOsStartupSecurityFramework.INSTANCE.SecItemCopyMatching(query, result)
            result.value?.let { dataPointer ->
                CoreFoundation.CFDataRef(dataPointer).release()
            }
            return when (status) {
                ERR_SEC_SUCCESS -> StartupUnlockResult(
                    status = StartupUnlockStatus.Verified,
                    message = "",
                )
                ERR_SEC_USER_CANCELED -> StartupUnlockResult(
                    status = StartupUnlockStatus.Blocked,
                    message = "macOS authentication was cancelled.",
                )
                else -> StartupUnlockResult(
                    status = StartupUnlockStatus.ReducedProtection,
                    message = "macOS authentication failed with OSStatus $status.",
                )
            }
        } finally {
            query.release()
        }
    }

    private fun ensureMacOsAppLockItem(): Int {
        val accessControlError = PointerByReference()
        val accessControl = MacOsStartupSecurityFramework.INSTANCE.SecAccessControlCreateWithFlags(
            allocator = null,
            protection = MacOsStartupSecurityConstants.kSecAttrAccessibleWhenUnlocked,
            flags = K_SEC_ACCESS_CONTROL_USER_PRESENCE,
            error = accessControlError,
        ) ?: return ERR_SEC_AUTH_FAILED

        val query = macOsAppLockQuery(returnData = false).apply {
            setOwnedValue(MacOsStartupSecurityConstants.kSecValueData, cfData("Mayday Chat".toByteArray(StandardCharsets.UTF_8)))
            setOwnedValue(MacOsStartupSecurityConstants.kSecAttrAccessControl, accessControl)
        }
        try {
            return MacOsStartupSecurityFramework.INSTANCE.SecItemAdd(query, null)
        } finally {
            query.release()
        }
    }

    private fun macOsAppLockQuery(returnData: Boolean): CoreFoundation.CFMutableDictionaryRef {
        return mutableDictionary().apply {
            setSharedValue(MacOsStartupSecurityConstants.kSecClass, MacOsStartupSecurityConstants.kSecClassGenericPassword)
            setOwnedValue(MacOsStartupSecurityConstants.kSecAttrService, cfString(MACOS_APP_LOCK_SERVICE))
            setOwnedValue(MacOsStartupSecurityConstants.kSecAttrAccount, cfString(MACOS_APP_LOCK_ACCOUNT))
            if (returnData) {
                setSharedValue(MacOsStartupSecurityConstants.kSecReturnData, MacOsStartupSecurityConstants.kCFBooleanTrue)
                setSharedValue(MacOsStartupSecurityConstants.kSecMatchLimit, MacOsStartupSecurityConstants.kSecMatchLimitOne)
            }
        }
    }

    private fun mutableDictionary(): CoreFoundation.CFMutableDictionaryRef {
        return MacOsStartupSecurityConstants.coreFoundation.CFDictionaryCreateMutable(
            null,
            CoreFoundation.CFIndex(0),
            MacOsStartupSecurityConstants.cfTypeDictionaryKeyCallbacks,
            MacOsStartupSecurityConstants.cfTypeDictionaryValueCallbacks,
        ) ?: error("Unable to create macOS app lock query.")
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
        return MacOsStartupSecurityConstants.coreFoundation.CFDataCreate(
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

    private fun promptForWindowsCredentials(flags: Int): Int {
        val uiInfo = WindowsCredentialUiInfo().apply {
            cbSize = size()
            hwndParent = Pointer.NULL
            pszMessageText = WString("Confirm your Windows identity to open Mayday Chat.")
            pszCaptionText = WString("Unlock Mayday Chat")
            hbmBanner = Pointer.NULL
            write()
        }

        val authPackage = IntByReference(0)
        val outBuffer = PointerByReference()
        val outBufferSize = IntByReference(0)
        val save = IntByReference(0)

        val status = CredentialUiLibrary.INSTANCE.CredUIPromptForWindowsCredentialsW(
            pUiInfo = uiInfo,
            dwAuthError = 0,
            pulAuthPackage = authPackage,
            pvInAuthBuffer = Pointer.NULL,
            ulInAuthBufferSize = 0,
            ppvOutAuthBuffer = outBuffer,
            pulOutAuthBufferSize = outBufferSize,
            pfSave = save,
            dwFlags = flags,
        )

        clearAndFreeCredentialBuffer(outBuffer.value, outBufferSize.value)
        return status
    }

    private fun clearAndFreeCredentialBuffer(buffer: Pointer?, size: Int) {
        if (buffer == null || buffer == Pointer.NULL) return
        if (size > 0) {
            runCatching { buffer.clear(size.toLong()) }
        }
        Ole32Library.INSTANCE.CoTaskMemFree(buffer)
    }

    data class StartupUnlockDecision(
        val allowed: Boolean,
        val showReducedProtectionWarning: Boolean = false,
    )

    private data class StartupUnlockResult(
        val status: StartupUnlockStatus,
        val message: String,
    )

    private enum class StartupUnlockStatus {
        Verified,
        ReducedProtection,
        Blocked,
    }

    private const val APP_LOCK_UNAVAILABLE_MESSAGE =
        "System authentication is required to open Mayday Chat."
    private const val REDUCED_PROTECTION_MESSAGE =
        "Mayday Chat opened without system authentication because device authentication is not available. Account access, backup import, UUID registration, and key generation are disabled until you set up Windows Hello, Touch ID, or a device password in system settings."

    private const val ERROR_SUCCESS = 0
    private const val ERROR_INVALID_PARAMETER = 87
    private const val ERROR_CANCELLED = 1223
    private const val CREDUIWIN_ENUMERATE_CURRENT_USER = 0x00000200
    private const val CREDUIWIN_PACK_WINDOWS_HELLO_CREDENTIALS = 0x80000000.toInt()

    private const val ERR_SEC_SUCCESS = 0
    private const val ERR_SEC_DUPLICATE_ITEM = -25299
    private const val ERR_SEC_USER_CANCELED = -128
    private const val ERR_SEC_AUTH_FAILED = -25293
    private const val K_SEC_ACCESS_CONTROL_USER_PRESENCE = 1L shl 0
    private const val MACOS_APP_LOCK_SERVICE = "org.debs.kalog.app-lock"
    private const val MACOS_APP_LOCK_ACCOUNT = "startup.v1"

}

@Structure.FieldOrder(
    "cbSize",
    "hwndParent",
    "pszMessageText",
    "pszCaptionText",
    "hbmBanner",
)
internal class WindowsCredentialUiInfo : Structure() {
    @JvmField
    var cbSize: Int = 0

    @JvmField
    var hwndParent: Pointer? = null

    @JvmField
    var pszMessageText: WString? = null

    @JvmField
    var pszCaptionText: WString? = null

    @JvmField
    var hbmBanner: Pointer? = null
}

internal interface CredentialUiLibrary : StdCallLibrary {
    fun CredUIPromptForWindowsCredentialsW(
        pUiInfo: WindowsCredentialUiInfo,
        dwAuthError: Int,
        pulAuthPackage: IntByReference,
        pvInAuthBuffer: Pointer?,
        ulInAuthBufferSize: Int,
        ppvOutAuthBuffer: PointerByReference,
        pulOutAuthBufferSize: IntByReference,
        pfSave: IntByReference?,
        dwFlags: Int,
    ): Int

    companion object {
        val INSTANCE: CredentialUiLibrary = Native.load(
            "Credui",
            CredentialUiLibrary::class.java,
            W32APIOptions.DEFAULT_OPTIONS,
        )
    }
}

internal interface Ole32Library : StdCallLibrary {
    fun CoTaskMemFree(pv: Pointer?)

    companion object {
        val INSTANCE: Ole32Library = Native.load(
            "Ole32",
            Ole32Library::class.java,
            W32APIOptions.DEFAULT_OPTIONS,
        )
    }
}

internal interface MacOsStartupSecurityFramework : Library {
    fun SecAccessControlCreateWithFlags(
        allocator: Pointer?,
        protection: CoreFoundation.CFTypeRef,
        flags: Long,
        error: PointerByReference?,
    ): CoreFoundation.CFTypeRef?

    fun SecItemAdd(
        attributes: CoreFoundation.CFDictionaryRef,
        result: PointerByReference?,
    ): Int

    fun SecItemCopyMatching(
        query: CoreFoundation.CFDictionaryRef,
        result: PointerByReference,
    ): Int

    companion object {
        val INSTANCE: MacOsStartupSecurityFramework = Native.load(
            "Security",
            MacOsStartupSecurityFramework::class.java,
        )
    }
}

internal object MacOsStartupSecurityConstants {
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
    val kSecAttrAccessControl: CoreFoundation.CFStringRef = securityStringConstant("kSecAttrAccessControl")
    val kSecAttrAccessibleWhenUnlocked: CoreFoundation.CFStringRef = securityStringConstant("kSecAttrAccessibleWhenUnlocked")
    val kSecValueData: CoreFoundation.CFStringRef = securityStringConstant("kSecValueData")
    val kSecReturnData: CoreFoundation.CFStringRef = securityStringConstant("kSecReturnData")
    val kSecMatchLimit: CoreFoundation.CFStringRef = securityStringConstant("kSecMatchLimit")
    val kSecMatchLimitOne: CoreFoundation.CFStringRef = securityStringConstant("kSecMatchLimitOne")
    val kSecUseOperationPrompt: CoreFoundation.CFStringRef = securityStringConstant("kSecUseOperationPrompt")

    private fun securityStringConstant(name: String): CoreFoundation.CFStringRef {
        return CoreFoundation.CFStringRef(
            NativeLibrary.getInstance("Security").getGlobalVariableAddress(name).getPointer(0),
        )
    }
}
