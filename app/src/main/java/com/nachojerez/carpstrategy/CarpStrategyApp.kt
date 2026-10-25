package com.nachojerez.carpstrategy

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nachojerez.carpstrategy.data.diagnostics.CrashReporter
import com.nachojerez.carpstrategy.data.sync.SyncManager
import com.nachojerez.carpstrategy.ui.outlook.OutlookScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class CarpStrategyApp : Application(), Configuration.Provider {
    @Inject lateinit var crashReporter: CrashReporter

    @Inject lateinit var syncManager: SyncManager

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var outlook: OutlookScheduler

    /** WorkManager con Hilt (fase 7.17): las tareas reciben sus dependencias. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
        // Si hay cuenta de Google, baja lo último de Drive y sube cada cambio.
        syncManager.start()
        // Si el aviso «Mañana pinta bien» está activado, que siga programado.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { outlook.ensure() } }
    }
}
