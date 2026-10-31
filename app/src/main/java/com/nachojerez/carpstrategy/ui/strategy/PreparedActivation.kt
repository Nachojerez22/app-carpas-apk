package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.data.reservoir.ReservoirRepository
import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.guided.GuidedSessionManager
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Activar la salida preparada (fase 7.21), común a Estrategia y a «Ver la preparación»: cuál se
 * puede activar ahora ([PreparedOuting.activatableAt]) y empezar con ella la sesión guiada.
 */
@Singleton
class PreparedActivation @Inject constructor(
    localSettings: LocalSettings,
    private val reservoirs: ReservoirRepository,
    private val settings: SettingsRepository,
    private val manager: GuidedSessionManager,
    private val clock: Clock,
) {
    private val prepared = localSettings.observe(KEY_PREPARED_OUTING).map(PreparedOutingJson::decode)

    /** Cada minuto: al acabar el horario legal deja de ofrecerse. */
    private val ticker = flow {
        while (true) {
            emit(clock.instant())
            delay(60_000)
        }
    }

    /** La preparación de hoy para el embalse en el que estás; null si no hay ninguna que valga ahora. */
    fun observeToday(): Flow<PreparedOuting?> = combine(prepared, reservoirs.observeActive(), settings.observeLocation(), ticker) { p, r, place, now ->
        val today = now.atZone(Formatting.MADRID).toLocalDate()
        val legalEnd = LegalWindow.of(SolarCalculator.sunTimes(today, place.point))?.end
        p?.takeIf { it.activatableAt(now, Formatting.MADRID, r?.reservoir?.id, r?.reservoir?.name, legalEnd) }
    }.distinctUntilChanged()

    /** Empieza (o devuelve, si ya hay una en curso) la sesión guiada con lo preparado. */
    suspend fun activate(prepared: PreparedOuting): Long = manager.startPrepared(prepared)
}
