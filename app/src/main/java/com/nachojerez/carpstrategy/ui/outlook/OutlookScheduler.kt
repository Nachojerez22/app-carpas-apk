package com.nachojerez.carpstrategy.ui.outlook

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Programa el aviso «Mañana pinta bien» (fase 7.17) una vez al día por la tarde, con WorkManager
 * (sobrevive a reinicios y no necesita alarmas exactas). Desactivado por defecto; solo en este móvil.
 */
@Singleton
class OutlookScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val local: LocalSettings,
    private val clock: Clock,
) {
    fun observeEnabled(): Flow<Boolean> = local.observe(KEY_ENABLED).map { it == ON }

    suspend fun setEnabled(enabled: Boolean) {
        local.put(KEY_ENABLED, if (enabled) ON else null)
        apply(enabled, ExistingPeriodicWorkPolicy.UPDATE)
    }

    /** Al arrancar la app: si está activado y aún no está programado, se programa. */
    suspend fun ensure() = apply(local.get(KEY_ENABLED) == ON, ExistingPeriodicWorkPolicy.KEEP)

    private fun apply(enabled: Boolean, policy: ExistingPeriodicWorkPolicy) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = clock.instant().atZone(Formatting.MADRID)
        var next = now.toLocalDate().atTime(NOTIFY_HOUR, 0).atZone(Formatting.MADRID)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<OutlookWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    companion object {
        /** Hora del aviso (Madrid): con tiempo para preparar la salida. */
        const val NOTIFY_HOUR = 19

        /** Si el sistema lo retrasa fuera de esta franja, no se avisa (nada de madrugadas). */
        val WINDOW_HOURS = 17..22

        private const val WORK_NAME = "aviso_manana"
        private const val KEY_ENABLED = "outlook_enabled"
        private const val ON = "1"
    }
}
