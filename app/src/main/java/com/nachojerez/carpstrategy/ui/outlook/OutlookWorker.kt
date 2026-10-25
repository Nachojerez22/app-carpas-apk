package com.nachojerez.carpstrategy.ui.outlook

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
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nachojerez.carpstrategy.MainActivity
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.data.reservoir.ReservoirRepository
import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import com.nachojerez.carpstrategy.domain.repository.JournalRepository
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.repository.WeatherRepository
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import com.nachojerez.carpstrategy.domain.usecase.ObserveRawWeatherUseCase
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.strategy.buildStrategyState
import com.nachojerez.carpstrategy.ui.strategy.nextOuting
import com.nachojerez.carpstrategy.ui.strategy.shouldNotifyOutlook
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * «Mañana pinta bien» (fase 7.17): por la tarde evalúa la próxima salida con las mismas reglas que
 * Estrategia y, si sale favorable según tus datos, avisa una vez (sin prometer peces). Si no hay
 * red, usa la previsión guardada.
 */
@HiltWorker
class OutlookWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val weather: WeatherRepository,
    private val observeRawWeather: ObserveRawWeatherUseCase,
    private val rulesRepository: RulesRepository,
    private val journal: JournalRepository,
    private val reservoirs: ReservoirRepository,
    private val local: LocalSettings,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val now = clock.instant()
        // Si el sistema lo retrasa (ahorro de batería), no se avisa de madrugada.
        if (now.atZone(Formatting.MADRID).hour !in OutlookScheduler.WINDOW_HOURS) return Result.success()
        val place = settings.observeLocation().first()
        val target = nextOuting(now, place.point, Formatting.MADRID) ?: return Result.success()
        withTimeoutOrNull(REFRESH_TIMEOUT_MS) { weather.refreshForecast(place.point) }
        val state = buildStrategyState(observeRawWeather(place.point).first(), rulesRepository.load(), target, place.point, journal.observeSessions().first())
        val result = state.result ?: return Result.success()
        val date = target.atZone(Formatting.MADRID).toLocalDate()
        val last = local.get(KEY_LAST_NOTIFIED)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (!shouldNotifyOutlook(result.band, result.blocked, date, last)) return Result.success()
        val name = reservoirs.observeActive().first()?.reservoir?.name ?: place.name
        if (notify(name, result.band ?: return Result.success(), target)) local.put(KEY_LAST_NOTIFIED, date.toString())
        return Result.success()
    }

    private fun notify(reservoir: String, band: FavorabilityBand, target: java.time.Instant): Boolean {
        val context = applicationContext
        val manager = NotificationManagerCompat.from(context)
        val allowed = manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        if (!allowed) return false
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.outlook_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.outlook_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val res = context.resources
        val quality = res.getString(if (band == FavorabilityBand.VERY_FAVORABLE) R.string.outlook_very_favorable else R.string.outlook_favorable)
        val open = PendingIntent.getActivity(
            context, REQUEST_OPEN,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = res.getString(R.string.outlook_text, Formatting.hour(target), quality)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_strategy)
            .setContentTitle(res.getString(R.string.outlook_title, reservoir))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        const val CHANNEL_ID = "prevision_salida"
        const val NOTIFICATION_ID = 7300
        const val REQUEST_OPEN = 7310
        const val REFRESH_TIMEOUT_MS = 20_000L

        /** Fecha de la última salida avisada (ajuste local): un aviso por salida. */
        const val KEY_LAST_NOTIFIED = "outlook_last_notified"
    }
}
