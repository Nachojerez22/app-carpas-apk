package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.journal.Catch
import com.nachojerez.carpstrategy.domain.journal.Session
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Lo que necesita cada operación además de la sesión: hora, equipo y zona horaria. */
data class GuidedEnv(val now: Instant, val inventory: List<GearItem>, val zone: ZoneId)

/** Resultado de una acción. [newProposals]: caña → propuesta recién añadida. [messages]: avisos por caña. */
data class GuidedUpdate(
    val session: Session,
    val messages: Map<Int, Set<GuidedMessage>> = emptyMap(),
    val newProposals: Map<Int, Proposal> = emptyMap(),
)

/**
 * Operaciones sobre una [Session] con registro guiado (§5.9), caña a caña. Funciones puras:
 * aplican la acción, actualizan picadas y capturas de la ficha y vuelven a evaluar todas las
 * cañas con [GuidedEngine].
 */
object GuidedSessions {
    const val MAX_RODS = 3

    /** Sesión guiada en curso (como mucho una: la más reciente). */
    fun active(sessions: List<Session>): Session? = sessions.filter { it.isOngoing && it.guided != null }.maxByOrNull { it.start }

    /** Fase con los parámetros guardados al empezar (temperatura del agua y estación de las reglas). */
    fun phaseOf(session: Session): FishingPhase {
        val features = session.context?.features
        val water = features?.numbers?.get("temp_agua_c") ?: session.context?.waterTempC
        return GuidedEngine.phase(water, features?.texts?.get("estacion"))
    }

    /** Fin del horario legal del día de la sesión. */
    fun legalEnd(session: Session, zone: ZoneId): Instant? = session.context?.legalEnd
        ?: LegalWindow.of(SolarCalculator.sunTimes(session.start.atZone(zone).toLocalDate(), session.location))?.end

    /** Presión de pesca alta: fin de semana u otros pescadores cerca (§5.9.2). */
    fun highPressure(session: Session, now: Instant, zone: ZoneId): Boolean {
        val day = now.atZone(zone).dayOfWeek
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY || (session.otherAnglers ?: 0) > 0
    }

    /** Contexto del motor para una caña. */
    fun context(session: Session, rodId: Int, env: GuidedEnv): GuidedContext {
        val record = session.guided
        return GuidedContext(
            phase = phaseOf(session),
            now = env.now,
            legalEnd = legalEnd(session, env.zone),
            inventory = env.inventory,
            highPressure = highPressure(session, env.now, env.zone),
            missingBaits = record?.missingBaits.orEmpty(),
            groundbait = record?.groundbaitOf(rodId),
            lastWindChange = record?.windChanges?.lastOrNull(),
            conditions = record?.activeConditions(env.now).orEmpty(),
        )
    }

    /**
     * Empieza con una caña por nombre («fija», «carrete»), cada una con su plan A pendiente.
     * La segunda caña empieza por otro cebo de la fase si lo llevas, para comparar (🟣).
     */
    fun start(session: Session, rodNames: List<String>, groundbait: GroundbaitLevel?, env: GuidedEnv, spot: Spot? = null): GuidedUpdate {
        val names = rodNames.map { it.trim() }.take(MAX_RODS).ifEmpty { listOf("") }
        val base = session.copy(
            rods = names.size,
            zone = spot?.zone ?: session.zone,
            guided = GuidedRecord(rods = emptyList(), groundbait = groundbait, spot = spot),
        )
        val rods = names.mapIndexed { index, name ->
            val ctx = context(base, index + 1, env)
            RodTrack(index + 1, name, GuidedLog.start(env.now, GuidedEngine.initialProposal(ctx, variant = index)))
        }
        val started = base.copy(guided = base.guided!!.copy(rods = rods))
        return evaluateAll(started, env, appendProposals = false).let { update ->
            update.copy(newProposals = rods.associate { it.id to it.log.pending!!.proposal })
        }
    }

    /**
     * Respuesta a un aviso en una caña. Picada fallada o carpa suman en la ficha (la carpa con su
     * hora y caña); otras especies solo quedan en el registro guiado.
     */
    fun checkIn(session: Session, rodId: Int, checkIn: CheckIn, env: GuidedEnv): GuidedUpdate {
        val record = session.guided ?: return GuidedUpdate(session)
        if (record.rod(rodId) == null) return GuidedUpdate(session)
        val hooked = checkIn.activity == HookActivity.MISSED || checkIn.isCarpCatch
        val updated = session.copy(
            guided = record.updateRod(rodId) { it.withCheckIn(checkIn) },
            bites = session.bites + if (hooked) 1 else 0,
            catches = if (checkIn.isCarpCatch) session.catches + Catch(time = checkIn.time, rod = rodId) else session.catches,
        )
        return refresh(updated, env)
    }

    /** «Nada» en todas las cañas a la vez (botón de la notificación). */
    fun nothingEverywhere(session: Session, env: GuidedEnv): GuidedUpdate {
        val record = session.guided ?: return GuidedUpdate(session)
        val updated = record.rods.fold(record) { acc, rod -> acc.updateRod(rod.id) { it.withCheckIn(CheckIn(env.now)) } }
        return refresh(session.copy(guided = updated), env)
    }

    /** El usuario anota que el viento ha cambiado (gira o sube). */
    fun windChanged(session: Session, env: GuidedEnv): GuidedUpdate {
        val record = session.guided ?: return GuidedUpdate(session)
        return refresh(session.copy(guided = record.withWindChange(env.now)), env)
    }

    /** Lluvia, tormenta o entrada de agua turbia empiezan ([active]) o terminan. */
    fun conditionChanged(session: Session, condition: FieldCondition, active: Boolean, env: GuidedEnv): GuidedUpdate {
        val record = session.guided ?: return GuidedUpdate(session)
        return refresh(session.copy(guided = record.withCondition(env.now, condition, active)), env)
    }

    /** El tiempo de un aviso (previsión de la hora en curso); un cambio claro de viento cuenta como tal. */
    fun weather(session: Session, snapshot: WeatherSnapshot, env: GuidedEnv): GuidedUpdate {
        val record = session.guided ?: return GuidedUpdate(session)
        return refresh(session.copy(guided = record.withWeather(snapshot)), env)
    }

    /**
     * Respuesta a «¿Llueve?» o «¿Hay tormenta?». Lo que dices manda sobre la previsión: «sí»
     * activa la condición (lluvia fuerte si [heavy]) y «no» termina la lluvia anotada.
     */
    fun answerWeather(session: Session, question: WeatherQuestion, answer: Boolean, env: GuidedEnv, heavy: Boolean = false): GuidedUpdate {
        var record = session.guided?.withWeatherAnswer(question, answer) ?: return GuidedUpdate(session)
        val active = record.activeConditions(env.now)
        record = when (question) {
            WeatherQuestion.RAIN -> when {
                answer && FieldCondition.LIGHT_RAIN !in active && FieldCondition.HEAVY_RAIN !in active ->
                    record.withCondition(env.now, if (heavy) FieldCondition.HEAVY_RAIN else FieldCondition.LIGHT_RAIN, true)
                !answer -> record.withCondition(env.now, FieldCondition.LIGHT_RAIN, false).withCondition(env.now, FieldCondition.HEAVY_RAIN, false)
                else -> record
            }
            WeatherQuestion.STORM -> if (answer) record.withCondition(env.now, FieldCondition.STORM, true) else record
        }
        return refresh(session.copy(guided = record), env)
    }

    /** Acepta la propuesta pendiente de una caña; cebo y montaje pasan a la ficha. */
    fun accept(session: Session, rodId: Int, env: GuidedEnv): GuidedUpdate {
        val record = session.guided ?: return GuidedUpdate(session)
        if (record.rod(rodId)?.log?.pending == null) return refresh(session, env)
        val accepted = record.updateRod(rodId) { it.accept(env.now) }
        return refresh(session.copy(guided = accepted).withGearSummary(), env)
    }

    /** Rechaza con motivo y comentario; si era el plan A, se propone otro para esa caña. */
    fun reject(session: Session, rodId: Int, reason: RejectReason, comment: String, env: GuidedEnv): GuidedUpdate {
        val record = session.guided ?: return GuidedUpdate(session)
        return refresh(session.copy(guided = record.updateRod(rodId) { it.reject(env.now, reason, comment) }), env)
    }

    /** Vuelve a evaluar todas las cañas y añade las propuestas que toquen (pendientes de decidir). */
    fun refresh(session: Session, env: GuidedEnv): GuidedUpdate = evaluateAll(session, env, appendProposals = true)

    /** Termina: cierra el último tramo de cada caña; el resultado se completa en la ficha. */
    fun finish(session: Session, at: Instant): Session =
        session.copy(end = session.end ?: at, guided = session.guided?.finish(at))

    private fun evaluateAll(session: Session, env: GuidedEnv, appendProposals: Boolean): GuidedUpdate {
        var record = session.guided ?: return GuidedUpdate(session)
        if (!session.isOngoing) return GuidedUpdate(session)
        val messages = mutableMapOf<Int, Set<GuidedMessage>>()
        val proposals = mutableMapOf<Int, Proposal>()
        record.rods.forEachIndexed { index, rod ->
            val current = session.copy(guided = record)
            val evaluation = GuidedEngine.evaluate(rod.log, context(current, rod.id, env), variant = index)
            messages[rod.id] = evaluation.messages
            val proposal = evaluation.proposal
            if (appendProposals && proposal != null) {
                record = record.updateRod(rod.id) { it.withProposal(proposal) }
                proposals[rod.id] = proposal
            }
        }
        return GuidedUpdate(session.copy(guided = record), messages, proposals)
    }

    /** «fija: Maíz · carrete: Boilie fresa» para los campos de cebo y montaje de la ficha. */
    private fun Session.withGearSummary(): Session {
        val rods = guided?.rods ?: return this
        fun summary(value: (Segment) -> String?) = rods.mapNotNull { r ->
            value(r.log.current)?.let { v -> if (rods.size == 1 || r.name.isBlank()) v else "${r.name}: $v" }
        }.joinToString(" · ")
        return copy(
            bait = summary { it.baitName }.ifBlank { bait },
            rig = summary { it.rigName }.ifBlank { rig },
        )
    }
}
