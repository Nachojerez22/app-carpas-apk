package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.domain.assistant.AiExchange
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.guided.ActivityPlace
import com.nachojerez.carpstrategy.domain.guided.BaitState
import com.nachojerez.carpstrategy.domain.guided.ChangedVariable
import com.nachojerez.carpstrategy.domain.guided.ChatMessage
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.FieldCondition
import com.nachojerez.carpstrategy.domain.guided.FieldWeather
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
import com.nachojerez.carpstrategy.domain.guided.JumpCount
import com.nachojerez.carpstrategy.domain.guided.SignalLevel
import com.nachojerez.carpstrategy.domain.guided.Species
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.WeatherReport
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.RawRecord
import com.nachojerez.carpstrategy.domain.manual.RawValue
import com.nachojerez.carpstrategy.ui.diary.SessionForm
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
    /** El tiempo del último aviso con sus cambios (previsión; null si aún no hay). */
    val weather: WeatherReport? = null,
    /** Mis puestos, para elegir uno al empezar. */
    val spots: List<Spot> = emptyList(),
    /** Puesto elegido en Estrategia: viene marcado al empezar (fase 7.10). */
    val preferredSpotId: Long? = null,
    /** Cañas elegidas en Estrategia (fase 7.12); null = sin elegir. */
    val preferredRods: Int? = null,
    /** Hay clave del asistente de IA en este móvil. */
    val aiConfigured: Boolean = false,
    val aiBusy: Boolean = false,
    /** Hay un mensaje del chat esperando respuesta de la IA. */
    val chatBusy: Boolean = false,
) {
    /** Conversación con la IA en esta sesión (fase 7.7). */
    val chat: List<ChatMessage> get() = session?.guided?.chat.orEmpty()

    /** Última consulta a la IA en un aviso. */
    val lastAi: AiExchange? get() = session?.guided?.ai?.lastOrNull { it.kind == AiKind.CHECK_IN }

    /** Avisos de toda la sesión (sin repetir los comunes a varias cañas). */
    val messages: Set<GuidedMessage> get() = rods.flatMap { it.messages }.toSet()
}

/** Estado de la pantalla. Función pura: los avisos salen de evaluar cada caña ahora. */
fun buildGuidedState(session: Session?, gear: List<GearItem>, now: Instant, nextCheckIn: Instant?, zone: ZoneId, spots: List<Spot> = emptyList()): GuidedUiState {
    val base = GuidedUiState(
        isLoading = false,
        spots = spots,
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
        weather = FieldWeather.report(record, now),
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
    val seenAt: ActivityPlace? = null,
    val jumps: JumpCount? = null,
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
        seenAt = seenAt,
        jumps = jumps,
    )
}

/** Nombres de las cañas para empezar: los escritos o «» (se mostrará «Caña N»). */
fun rodNamesFor(count: Int, typed: List<String>): List<String> =
    (0 until count.coerceIn(1, GuidedSessions.MAX_RODS)).map { typed.getOrNull(it)?.trim().orEmpty() }

/** Respuesta rápida desde la notificación (sin abrir la app). */
enum class QuickAnswer { NOTHING, NOTHING_ALL, MISSED, CATCH, NOT_WORKING, ACCEPT }

fun QuickAnswer.toCheckIn(now: Instant): CheckIn = when (this) {
    QuickAnswer.MISSED -> CheckIn(now, activity = HookActivity.MISSED)
    QuickAnswer.CATCH -> CheckIn(now, activity = HookActivity.CATCH)
    QuickAnswer.NOT_WORKING -> CheckIn(now, notWorking = true)
    QuickAnswer.NOTHING, QuickAnswer.NOTHING_ALL, QuickAnswer.ACCEPT -> CheckIn(now, baitState = BaitState.NOT_CHECKED)
}

/** Un botón de la notificación: la respuesta y su caña (null = la única o la de la propuesta). */
data class NotificationAction(val answer: QuickAnswer, val rodId: Int? = null)

/** Android enseña como mucho 3 botones en una notificación. */
const val MAX_NOTIFICATION_ACTIONS = 3

/**
 * Botones de la notificación (fase 7.16: con varias cañas, «Carpa» en cada una, para anotarla sin
 * abrir la app). Con 3 cañas en un aviso caben «Nada en ninguna» y las dos primeras: la tercera, en la app.
 */
fun notificationActions(rodIds: List<Int>, pendingProposal: Boolean, checkIn: Boolean): List<NotificationAction> {
    val single = rodIds.singleOrNull()
    val actions = when {
        single == null -> (if (checkIn) listOf(NotificationAction(QuickAnswer.NOTHING_ALL)) else emptyList()) +
            rodIds.map { NotificationAction(QuickAnswer.CATCH, it) }
        pendingProposal -> listOf(QuickAnswer.ACCEPT, QuickAnswer.CATCH, QuickAnswer.NOT_WORKING).map { NotificationAction(it, single) }
        checkIn -> listOf(QuickAnswer.NOTHING, QuickAnswer.MISSED, QuickAnswer.CATCH).map { NotificationAction(it, single) }
        else -> listOf(QuickAnswer.MISSED, QuickAnswer.CATCH, QuickAnswer.NOT_WORKING).map { NotificationAction(it, single) }
    }
    return actions.take(MAX_NOTIFICATION_ACTIONS)
}

/** Agua medida al empezar la sesión guiada (fase 7.16). */
sealed interface WaterInput {
    data object Empty : WaterInput
    data class Valid(val celsius: Double) : WaterInput
    data object Invalid : WaterInput
}

/** «18,5» o «18.5»; vacío = sin medida; fuera de −1…40 °C (como en Datos) = no válido. */
fun parseWaterInput(text: String): WaterInput {
    val clean = text.trim().replace(',', '.')
    if (clean.isEmpty()) return WaterInput.Empty
    val value = clean.toDoubleOrNull() ?: return WaterInput.Invalid
    return if (value in ManualField.WATER_TEMP_SURFACE.min..ManualField.WATER_TEMP_SURFACE.max) WaterInput.Valid(value) else WaterInput.Invalid
}

/** El agua medida va a Datos como medición de la sesión: manda sobre la estimada (CLAUDE.md, regla 7). */
fun waterMeasurementRecord(celsius: Double, now: Instant, zone: ZoneId): RawRecord =
    RawRecord(
        time = MEASUREMENT_TIME.format(now.atZone(zone)),
        source = SessionForm.MEASUREMENT_SOURCE,
        values = mapOf(ManualField.WATER_TEMP_SURFACE.key to RawValue.Number(celsius)),
        notes = null,
    )

private val MEASUREMENT_TIME: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
