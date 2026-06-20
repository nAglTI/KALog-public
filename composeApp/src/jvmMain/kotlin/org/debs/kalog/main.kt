package org.debs.kalog

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import java.awt.GraphicsEnvironment
import java.awt.Taskbar
import javax.imageio.ImageIO
import org.debs.kalog.di.initKoin

fun main() {
    val startupUnlock = JvmStartupAppLock.unlockOrExit()
    if (!startupUnlock.allowed) return

    initKoin()
    configureDesktopAppIcon()

    application {
        val trayState = rememberTrayState()
        val appIcon = painterResource("icons/mayday-chat.png")
        var isWindowVisible by remember { mutableStateOf(true) }
        var trayNotificationShown by remember { mutableStateOf(false) }
        var reducedProtectionWarningShown by remember { mutableStateOf(false) }

        Tray(
            icon = appIcon,
            state = trayState,
            tooltip = "Mayday Chat",
            onAction = { isWindowVisible = true },
        ) {
            Item(
                text = "Open Mayday Chat",
                onClick = { isWindowVisible = true },
            )
            Item(
                text = "Exit",
                onClick = ::exitApplication,
            )
        }

        Window(
            visible = isWindowVisible,
            onCloseRequest = {
                isWindowVisible = false
                if (!trayNotificationShown) {
                    trayNotificationShown = true
                    trayState.sendNotification(
                        Notification(
                            title = "Mayday Chat",
                            message = "Mayday Chat is still running in the system tray.",
                        ),
                    )
                }
            },
            title = "Mayday Chat",
            icon = appIcon,
        ) {
            if (startupUnlock.showReducedProtectionWarning && !reducedProtectionWarningShown) {
                LaunchedEffect(Unit) {
                    reducedProtectionWarningShown = true
                    JvmStartupAppLock.showReducedProtectionWarning()
                }
            }
            App(protectedDeviceLockAvailable = !startupUnlock.showReducedProtectionWarning)
        }
    }
}

private fun configureDesktopAppIcon() {
    if (GraphicsEnvironment.isHeadless()) return
    runCatching {
        if (!Taskbar.isTaskbarSupported()) return
        val taskbar = Taskbar.getTaskbar()
        if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return
        val iconUrl = Thread.currentThread()
            .contextClassLoader
            .getResource("icons/mayday-chat.png")
            ?: return
        taskbar.iconImage = ImageIO.read(iconUrl)
    }
}
