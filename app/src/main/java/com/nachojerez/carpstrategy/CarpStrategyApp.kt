package com.nachojerez.carpstrategy

import android.app.Application
import com.nachojerez.carpstrategy.data.diagnostics.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CarpStrategyApp : Application() {
    @Inject lateinit var crashReporter: CrashReporter

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
    }
}
