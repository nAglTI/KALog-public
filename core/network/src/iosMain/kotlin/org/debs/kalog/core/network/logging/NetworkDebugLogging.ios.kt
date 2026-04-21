package org.debs.kalog.core.network.logging

import kotlin.native.Platform
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun defaultPlatformDebugBuild(): Boolean = Platform.isDebugBinary

internal actual fun platformHttpLog(message: String) {
    message.lineSequence().forEach { line ->
        println("[KALog][HTTP] $line")
    }
}
