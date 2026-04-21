package org.debs.kalog

import android.app.Application
import android.content.pm.ApplicationInfo
import org.debs.kalog.di.initKoin
import org.koin.android.ext.koin.androidContext

class KalogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        configureSharedPlatform(
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
        )
        initKoin {
            androidContext(this@KalogApplication)
        }
    }
}
