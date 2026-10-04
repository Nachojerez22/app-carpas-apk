package com.nachojerez.carpstrategy

import android.app.Application
import com.nachojerez.carpstrategy.data.diagnostics.CrashReporter
import com.nachojerez.carpstrategy.data.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CarpStrategyApp : Application() {
    @Inject lateinit var crashReporter: CrashReporter

    @Inject lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
        // Si hay cuenta de Google, baja lo último de Drive y sube cada cambio.
        syncManager.start()
    }
}
