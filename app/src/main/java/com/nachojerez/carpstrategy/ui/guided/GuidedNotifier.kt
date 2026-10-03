package com.nachojerez.carpstrategy.ui.guided

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nachojerez.carpstrategy.MainActivity
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Una sola notificación fija con cronómetro (§5.9.7): solo vibra cuando hay aviso o propuesta
 * nueva, y se responde con sus botones sin abrir la app.
 */
@Singleton
class GuidedNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun canNotify(): Boolean = manager.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.guided_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.guided_channel_description)
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = VIBRATION
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(session: Session, phase: FishingPhase, legalEnd: Instant?, next: Instant?, now: Instant, alert: Boolean, checkIn: Boolean) {
        if (!canNotify()) return
        ensureChannel()
        val res = context.resources
        val log = session.guided ?: return
        val pending = log.pending?.proposal
        val ended = legalEnd != null && !now.isBefore(legalEnd)
        val plan = res.baitText(log.current.bait, log.current.baitName)?.let { res.getString(R.string.guided_notif_plan, it) }
        val text = when {
            ended -> res.getString(R.string.guided_notif_legal_end)
            pending != null -> res.getString(R.string.guided_notif_proposal, res.proposalText(pending, phase))
            checkIn -> res.getString(R.string.guided_notif_check)
            else -> listOfNotNull(plan, next?.let { res.getString(R.string.guided_notif_next, Formatting.clock(it)) }).joinToString(" · ")
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_diary)
            .setContentTitle(res.getString(R.string.guided_notif_title, res.getString(phase.titleRes())))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(!ended)
            .setWhen(session.start.toEpochMilli())
            .setUsesChronometer(!ended)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(!alert)
            .setSilent(!alert)
            .setVibrate(if (alert) VIBRATION else null)
            .setContentIntent(openApp())
        if (!ended) {
            val actions = when {
                pending != null -> listOf(QuickAnswer.ACCEPT to R.string.guided_notif_accept, QuickAnswer.CATCH to R.string.guided_notif_catch, QuickAnswer.NOT_WORKING to R.string.guided_notif_not_working)
                checkIn -> listOf(QuickAnswer.NOTHING to R.string.guided_notif_nothing, QuickAnswer.MISSED to R.string.guided_notif_missed, QuickAnswer.CATCH to R.string.guided_notif_catch)
                else -> listOf(QuickAnswer.MISSED to R.string.guided_notif_missed, QuickAnswer.CATCH to R.string.guided_notif_catch, QuickAnswer.NOT_WORKING to R.string.guided_notif_not_working)
            }
            actions.forEach { (answer, label) -> builder.addAction(0, res.getString(label), answerIntent(answer)) }
        }
        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // El permiso se retiró entre la comprobación y el aviso: se sigue anotando desde la app.
        }
    }

    fun dismiss() = manager.cancel(NOTIFICATION_ID)

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_GUIDED, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun answerIntent(answer: QuickAnswer): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ANSWER + answer.ordinal,
        Intent(context, GuidedActionReceiver::class.java)
            .setAction(GuidedActionReceiver.ACTION_ANSWER)
            .putExtra(GuidedActionReceiver.EXTRA_ANSWER, answer.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_ID = "sesion_guiada"
        const val EXTRA_OPEN_GUIDED = "abrir_sesion_guiada"
        private const val NOTIFICATION_ID = 7000
        private const val REQUEST_OPEN = 7100
        private const val REQUEST_ANSWER = 7200
        private val VIBRATION = longArrayOf(0, 250, 150, 250)
    }
}
