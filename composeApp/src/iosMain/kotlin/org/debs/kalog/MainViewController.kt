package org.debs.kalog

import androidx.compose.ui.window.ComposeUIViewController
import org.debs.kalog.di.initKoin

fun MainViewController() = run {
    initKoin()
    ComposeUIViewController(
        configure = {
            enforceStrictPlistSanityCheck = false
        },
    ) { App() }
}
