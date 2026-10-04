package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.domain.guided.BaitState
import com.nachojerez.carpstrategy.domain.guided.ChangedVariable
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.FieldCondition
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.GuidedEngine
import com.nachojerez.carpstrategy.domain.guided.GuidedEnv
import com.nachojerez.carpstrategy.domain.guided.GuidedLog
import com.nachojerez.carpstrategy.domain.guided.GuidedMessage
import com.nachojerez.carpstrategy.domain.guided.GuidedSessions
import com.nachojerez.carpstrategy.domain.guided.HookActivity
import com.nachojerez.carpstrategy.domain.guided.SignalLevel
import com.nachojerez.carpstrategy.domain.guided.Species
import com.nachojerez.carpstrategy.domain.journal.Session
import java.time.Instant
import java.time.ZoneId

/** Una caña en pantalla: su registro, sus avisos y el cebado vigente. */
data class RodUi(
    val id: Int,
    val name: String,
    val log: GuidedLog,
    val messages: Set<GuidedMessage>,
    val groundbait: GroundbaitLevel?,
)

data class GuidedUiState(
    val isLoading: Boolean = true,
    /** Sesión guiada en curso; null si no hay ninguna. */
    val session: Session? = null,
    val phase: FishingPhase? = null,
    val legalEnd: Instant? = null,
    val rods: List<RodUi> = emptyList(),
    val nextCheckIn: Instant? = null,
    val baits: Int = 0,
    val rigs: Int = 0,
    val now: Instant = Instant.EPOCH,
    val starting: Boolean = false,
    /** Última respuesta anotada desde la pantalla (para confirmarlo). */
    val lastSaved: Instant? = null,
    val canNotify: Boolean = true,
    val exactAlarms: Boolean = true,
    /** Lluvia, tormenta o entrada de agua activas ahora. */
    val conditions: Set<FieldCondition> = emptySet(),
) {
    /** Avisos de toda la sesión (sin repetir los comunes a varias cañas). */
    val messages: Set<GuidedMessage> get() = rods.flatMap { it.messages }.toSet()
}

/** Estado de la pantalla. Función pura: los avisos salen de evaluar cada caña ahora. */
fun buildGuidedState(session: Session?, gear: List<GearItem>, now: Instant, nextCheckIn: Instant?, zone: ZoneId): GuidedUiState {
    val base = GuidedUiState(
        isLoading = false,
        baits = gear.count { it.category == GearCategory.BAIT },
        rigs = gear.count { it.category == GearCategory.RIG },
        now = now,
    )
    val record = session?.guided ?: return base
    val env = GuidedEnv(now, gear, zone)
    val rods = record.rods.mapIndexed { index, rod ->
        val ctx = GuidedSessions.context(session, rod.id, env)
        RodUi(rod.id, rod.name, rod.log, GuidedEngine.evaluate(rod.log, ctx, index).messages, ctx.groundbait)
    }
    return base.copy(
        session = session,
        phase = GuidedSessions.phaseOf(session),
        legalEnd = GuidedSessions.legalEnd(session, zone),
        rods = rods,
        nextCheckIn = nextCheckIn,
        conditions = record.activeConditions(now),
    )
}

/** Lo que el usuario anota en una caña desde la pantalla. */
data class CheckInDraft(
    val signals: SignalLevel = SignalLevel.NONE,
    val activity: HookActivity = HookActivity.NOTHING,
    val baitState: BaitState = BaitState.NOT_CHECKED,
    val notWorking: Boolean = false,
    val change: ChangedVariable? = null,
    val species: Species? = null,
    val rebait: GroundbaitLevel? = null,
) {
    fun toCheckIn(now: Instant) = CheckIn(
        time = now,
        signals = signals,
        activity = activity,
        baitState = baitState,
        notWorking = notWorking,
        userChange = change,
        species = species.takeIf { activity == HookActivity.CATCH && it != Species.CARP },
        rebait = rebait,
    )
}

/** Nombres de las cañas para empezar: los escritos o «» (se mostrará «Caña N»). */
fun rodNamesFor(count: Int, typed: List<String>): List<String> =
    (0 until count.coerceIn(1, GuidedSessions.MAX_RODS)).map { typed.getOrNull(it)?.trim().orEmpty() }
