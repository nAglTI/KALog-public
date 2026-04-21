package org.debs.kalog.core.network.logging

import android.util.Log

internal actual fun defaultPlatformDebugBuild(): Boolean =
    System.getProperty("kalog.http.debug")?.toBooleanStrictOrNull() ?: false

internal actual fun platformHttpLog(message: String) {
    Log.d("KALogHttp", message)
}
