package org.debs.kalog

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.debs.kalog.di.initKoin

fun main() {
    initKoin()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "KALog",
        ) {
            App()
        }
    }
}
