package org.debs.kalog

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.util.Locale
import javax.swing.JOptionPane

internal object JvmStartupAppLock {
    fun unlockOrExit(): StartupUnlockDecision {
        if (!isWindows()) return StartupUnlockDecision(allowed = true)

        val result = runCatching { requestWindowsHelloVerification() }
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
                    result.message.ifBlank { WINDOWS_HELLO_UNAVAILABLE_MESSAGE },
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

    private const val WINDOWS_HELLO_UNAVAILABLE_MESSAGE =
        "Windows Hello or a device PIN/password is required to open Mayday Chat."
    private const val REDUCED_PROTECTION_MESSAGE =
        "Mayday Chat opened without Windows Hello or a Windows PIN/password because system authentication is not available. Account access, backup import, UUID registration, and key generation are disabled until you set up Windows Hello, a PIN, or a device password in Windows settings."

    private const val ERROR_SUCCESS = 0
    private const val ERROR_INVALID_PARAMETER = 87
    private const val ERROR_CANCELLED = 1223
    private const val CREDUIWIN_ENUMERATE_CURRENT_USER = 0x00000200
    private const val CREDUIWIN_PACK_WINDOWS_HELLO_CREDENTIALS = 0x80000000.toInt()

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
