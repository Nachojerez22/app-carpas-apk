package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.data.reservoir.ReservoirRepository
import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import com.nachojerez.carpstrategy.domain.advice.BaitShelf
import com.nachojerez.carpstrategy.domain.advice.DayPlans
import com.nachojerez.carpstrategy.domain.advice.Lesson
import com.nachojerez.carpstrategy.domain.advice.LessonStatus
import com.nachojerez.carpstrategy.domain.advice.Lessons
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.Spots
import com.nachojerez.carpstrategy.domain.guided.today
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.repository.JournalRepository
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.reservoir.Reservoir
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirContext
import com.nachojerez.carpstrategy.domain.reservoir.Reservoirs
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/*
 * Lo elegido para pescar (fase 7.25): embalse, puesto, cañas, cebos, lecciones y tu medida de
 * cebado. Común a Estrategia y a Preparar («Preparar la próxima salida»).
 */

data class AdviceInputs(
    val spots: List<Spot>,
    val gear: List<GearItem>,
    val lessons: List<Lesson>,
    val sessions: List<Session>,
    val reservoir: ReservoirContext? = null,
    val reservoirs: List<Reservoir> = emptyList(),
    val chosenSpotId: Long? = null,
    val rods: Int = DEFAULT_RODS,
    val groundbaitNormalKg: Double? = null,
)

/** Las entradas de [AdviceInputs] desde los ajustes y el diario. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class StrategyInputsSource @Inject constructor(
    private val settings: SettingsRepository,
    private val journal: JournalRepository,
    private val reservoirs: ReservoirRepository,
    private val localSettings: LocalSettings,
) {
    /** Puesto elegido para el embalse activo (se recuerda por embalse, solo en este móvil). */
    private val chosenSpot = reservoirs.observeActive().map { it?.reservoir?.id }.distinctUntilChanged()
        .flatMapLatest { id -> localSettings.observe(chosenSpotKey(id)).map { it?.toLongOrNull() } }

    private val rods = localSettings.observe(KEY_RODS).map { it?.toIntOrNull()?.coerceIn(1, DayPlans.MAX_RODS) ?: DEFAULT_RODS }

    fun observe(): Flow<AdviceInputs> = combine(
        combine(settings.observeSpots(), settings.observeGear(), settings.observeLessons(), journal.observeSessions(), settings.observeGroundbaitNormalKg()) { spots, gear, lessons, sessions, normal ->
            AdviceInputs(spots, gear, lessons, sessions, groundbaitNormalKg = normal)
        },
        reservoirs.observeActive(),
        reservoirs.observeAll(),
        chosenSpot,
        rods,
    ) { inputs, active, all, chosen, n -> inputs.copy(reservoir = active, reservoirs = all, chosenSpotId = chosen, rods = n) }
}

/** Lo elegido (embalse, puesto, cañas, cebos) y tus datos sobre la valoración de las reglas. Función pura. */
fun withInputs(state: StrategyUiState, inputs: AdviceInputs, now: Instant): StrategyUiState {
    val reservoirId = inputs.reservoir?.reservoir?.id
    val accepted = Lessons.forReservoir(inputs.lessons, reservoirId)
    val spots = Spots.of(inputs.spots, reservoirId)
    // Un puesto borrado o de otro embalse vuelve a «automático».
    val chosenId = inputs.chosenSpotId?.takeIf { id -> spots.any { it.id == id } }
    return state.copy(
        spots = spots,
        chosenSpotId = chosenId,
        rods = inputs.rods,
        gear = inputs.gear.today(),
        allGear = inputs.gear,
        groundbaitNormalKg = inputs.groundbaitNormalKg,
        baitUsage = BaitShelf.usage(inputs.sessions),
        myReservoirs = Reservoirs.withSpots(inputs.spots, inputs.reservoirs, inputs.reservoir?.reservoir),
        lessons = accepted,
        pendingLessons = inputs.lessons.count { it.status == LessonStatus.PROPOSED },
        reservoir = inputs.reservoir,
        advice = adviceFor(
            state.derived, state.result?.evaluatedAt ?: now, inputs.sessions, inputs.gear.today(), spots, accepted,
            inputs.reservoir, inputs.reservoirs, chosenId,
        ),
    )
}
