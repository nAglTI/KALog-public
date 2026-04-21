package org.debs.kalog

import org.debs.kalog.core.network.logging.NetworkLoggingConfig

fun configureSharedPlatform(isDebugBuild: Boolean) {
    NetworkLoggingConfig.setDebugBuild(isDebugBuild)
}
