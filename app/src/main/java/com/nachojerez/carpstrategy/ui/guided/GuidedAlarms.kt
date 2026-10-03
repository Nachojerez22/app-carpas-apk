package com.nachojerez.carpstrategy.ui.guided

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.CallSuper
import com.nachojerez.carpstrategy.domain.guided.BaitState
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.HookActivity
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.launch

/**
 * Avisos de la sesión guiada con AlarmManager (sin servicio en primer plano). Exactos si el
 * usuario lo permite; si no, inexactos (Android puede retrasarlos unos minutos).
 */
@Singleton
class CheckInScheduler @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val alarms: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    private fun intent() = Intent(context, GuidedAlarmReceiver::class.java).setAction(ACTION_ALARM)

    private fun pending(flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(context, REQUEST_ALARM, intent(), flags or PendingIntent.FLAG_IMMUTABLE)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun schedule(at: Instant) {
        val operation = pending(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val millis = at.toEpochMilli()
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
            }
        } catch (_: SecurityException) {
            // Permiso retirado justo ahora: aviso inexacto.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        }
    }

    fun cancel() {
        pending(PendingIntent.FLAG_NO_CREATE)?.let { operation ->
            alarms.cancel(operation)
            operation.cancel()
        }
    }

    fun isScheduled(): Boolean = pending(PendingIntent.FLAG_NO_CREATE) != null

    companion object {
        const val ACTION_ALARM = "com.nachojerez.carpstrategy.guided.ALARM"
        private const val REQUEST_ALARM = 7001
    }
}

/**
 * Hilt inyecta los receptores en el `onReceive` que genera; en Kotlin hay que llamar a
 * `super.onReceive`, y no se puede sobre un método abstracto: de ahí esta clase intermedia.
 */
abstract class HiltBroadcastReceiver : BroadcastReceiver() {
    @CallSuper
    override fun onReceive(context: Context, intent: Intent) = Unit
}

/** Salta el aviso: se registra y se muestra la notificación con vibración. */
@AndroidEntryPoint
class GuidedAlarmReceiver : HiltBroadcastReceiver() {
    @Inject lateinit var manager: GuidedSessionManager

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != CheckInScheduler.ACTION_ALARM) return
        val result = goAsync()
        manager.scope.launch {
            try {
                manager.onAlarm()
            } finally {
                result.finish()
            }
        }
    }
}

/** Respuesta rápida desde la notificación (sin abrir la app). */
enum class QuickAnswer { NOTHING, NOTHING_ALL, MISSED, CATCH, NOT_WORKING, ACCEPT }

@AndroidEntryPoint
class GuidedActionReceiver : HiltBroadcastReceiver() {
    @Inject lateinit var manager: GuidedSessionManager

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_ANSWER) return
        val answer = intent.getStringExtra(EXTRA_ANSWER)?.let { name -> QuickAnswer.entries.firstOrNull { it.name == name } } ?: return
        val rodId = intent.getIntExtra(EXTRA_ROD, 0).takeIf { it > 0 }
        val result = goAsync()
        manager.scope.launch {
            try {
                when (answer) {
                    QuickAnswer.ACCEPT -> manager.accept(rodId)
                    QuickAnswer.NOTHING_ALL -> manager.nothingEverywhere()
                    else -> manager.checkIn(rodId) { now -> answer.toCheckIn(now) }
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.nachojerez.carpstrategy.guided.ANSWER"
        const val EXTRA_ANSWER = "respuesta"
        const val EXTRA_ROD = "cana"
    }
}

fun QuickAnswer.toCheckIn(now: Instant): CheckIn = when (this) {
    QuickAnswer.MISSED -> CheckIn(now, activity = HookActivity.MISSED)
    QuickAnswer.CATCH -> CheckIn(now, activity = HookActivity.CATCH)
    QuickAnswer.NOT_WORKING -> CheckIn(now, notWorking = true)
    QuickAnswer.NOTHING, QuickAnswer.NOTHING_ALL, QuickAnswer.ACCEPT -> CheckIn(now, baitState = BaitState.NOT_CHECKED)
}
