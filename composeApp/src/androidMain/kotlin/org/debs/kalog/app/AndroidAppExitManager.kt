package org.debs.kalog.app

import android.os.Process
import kotlin.system.exitProcess

class AndroidAppExitManager : AppExitManager {
    override fun exitApp(): Boolean {
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}
