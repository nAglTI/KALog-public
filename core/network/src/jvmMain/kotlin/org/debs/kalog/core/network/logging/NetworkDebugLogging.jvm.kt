package org.debs.kalog.core.network.logging

internal actual fun defaultPlatformDebugBuild(): Boolean =
    // FIXME: Defaulting to true exposes network logs on Desktop JVM unless the property is overridden.
    System.getProperty("kalog.http.debug")?.toBooleanStrictOrNull() ?: true

internal actual fun platformHttpLog(message: String) {
    println("[KALog][HTTP] $message")
}
