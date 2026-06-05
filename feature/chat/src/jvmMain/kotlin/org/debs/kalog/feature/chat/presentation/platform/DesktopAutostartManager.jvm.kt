package org.debs.kalog.feature.chat.presentation.platform

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal actual fun createDesktopAutostartManager(): DesktopAutostartManager = JvmDesktopAutostartManager()

private class JvmDesktopAutostartManager : DesktopAutostartManager {
    private val osName = System.getProperty("os.name").lowercase()
    private val currentExecutable: File? = ProcessHandle.current()
        .info()
        .command()
        .orElse(null)
        ?.let(::File)
        ?.absoluteFile

    private val windowsExecutable: File?
        get() = currentExecutable
            ?.takeIf { osName.contains("win") }
            ?.takeIf { it.extension.equals("exe", ignoreCase = true) }
            ?.takeIf { it.nameWithoutExtension.lowercase() !in setOf("java", "javaw") }

    private val windowsRunCommand: String?
        get() = windowsExecutable
            ?.absolutePath
            ?.let { path -> "\"${path.replace("\"", "\\\"")}\"" }

    private val macAppBundle: File?
        get() {
            if (!osName.contains("mac")) return null

            var current = currentExecutable
            while (current != null) {
                if (current.extension.equals("app", ignoreCase = true) && current.isDirectory) {
                    return current
                }
                current = current.parentFile
            }
            return null
        }

    override val isSupported: Boolean
        get() = windowsExecutable != null || macAppBundle != null

    override suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        when {
            windowsExecutable != null -> readWindowsRunCommand() == windowsRunCommand
            macAppBundle != null -> macLaunchAgentFile.exists()
            else -> false
        }
    }

    override suspend fun setEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        when {
            windowsExecutable != null -> setWindowsAutostart(enabled)
            macAppBundle != null -> setMacAutostart(enabled)
            else -> false
        }
    }

    private fun setWindowsAutostart(enabled: Boolean): Boolean {
        if (!enabled) {
            return runCatching {
                if (windowsRunValueExists()) {
                    Advapi32Util.registryDeleteValue(
                        WinReg.HKEY_CURRENT_USER,
                        WINDOWS_RUN_KEY,
                        WINDOWS_RUN_VALUE_NAME,
                    )
                }
                readWindowsRunCommand() == null
            }.getOrDefault(false)
        }

        val command = windowsRunCommand ?: return false
        return runCatching {
            Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, WINDOWS_RUN_KEY)
            Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER,
                WINDOWS_RUN_KEY,
                WINDOWS_RUN_VALUE_NAME,
                command,
            )
            readWindowsRunCommand() == command
        }.getOrDefault(false)
    }

    private fun setMacAutostart(enabled: Boolean): Boolean {
        val launchAgentFile = macLaunchAgentFile
        if (!enabled) {
            return !launchAgentFile.exists() || launchAgentFile.delete()
        }

        val appBundle = macAppBundle ?: return false
        launchAgentFile.parentFile?.mkdirs()
        launchAgentFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key>
                <string>$MAC_LAUNCH_AGENT_LABEL</string>
                <key>ProgramArguments</key>
                <array>
                    <string>/usr/bin/open</string>
                    <string>-a</string>
                    <string>${appBundle.absolutePath.escapeXml()}</string>
                </array>
                <key>RunAtLoad</key>
                <true/>
            </dict>
            </plist>
            """.trimIndent(),
        )
        return launchAgentFile.exists()
    }

    private val macLaunchAgentFile: File
        get() = File(
            System.getProperty("user.home"),
            "Library/LaunchAgents/$MAC_LAUNCH_AGENT_LABEL.plist",
        )

    private fun readWindowsRunCommand(): String? {
        return runCatching {
            if (windowsRunValueExists()) {
                Advapi32Util.registryGetStringValue(
                    WinReg.HKEY_CURRENT_USER,
                    WINDOWS_RUN_KEY,
                    WINDOWS_RUN_VALUE_NAME,
                )
            } else {
                null
            }
        }.getOrNull()
    }

    private fun windowsRunValueExists(): Boolean {
        return Advapi32Util.registryValueExists(
            WinReg.HKEY_CURRENT_USER,
            WINDOWS_RUN_KEY,
            WINDOWS_RUN_VALUE_NAME,
        )
    }

    private fun String.escapeXml(): String = buildString(length) {
        this@escapeXml.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }

    private companion object {
        private const val MAC_LAUNCH_AGENT_LABEL = "org.debs.kalog.mayday-chat.autostart"
        private const val WINDOWS_RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        private const val WINDOWS_RUN_VALUE_NAME = "Mayday Chat"
    }
}
