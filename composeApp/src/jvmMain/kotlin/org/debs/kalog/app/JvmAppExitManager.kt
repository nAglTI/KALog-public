package org.debs.kalog.app

import kotlin.system.exitProcess

class JvmAppExitManager : AppExitManager {
    override fun exitApp(): Boolean {
        exitProcess(0)
    }
}
