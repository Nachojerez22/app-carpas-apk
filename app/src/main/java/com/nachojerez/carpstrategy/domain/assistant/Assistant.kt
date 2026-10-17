package com.nachojerez.carpstrategy.domain.assistant

import com.nachojerez.carpstrategy.domain.advice.Lesson
import com.nachojerez.carpstrategy.domain.advice.LessonConditions
import com.nachojerez.carpstrategy.domain.advice.LessonSource
import com.nachojerez.carpstrategy.domain.advice.LessonStatus
import com.nachojerez.carpstrategy.domain.advice.LessonTopic
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GuidedEngine
import com.nachojerez.carpstrategy.domain.guided.Proposal
import com.nachojerez.carpstrategy.domain.guided.ProposalSource
import com.nachojerez.carpstrategy.domain.guided.Situation
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.reservoir.FishSpecies
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirSheet
import com.nachojerez.carpstrategy.domain.reservoir.SheetNote
import com.nachojerez.carpstrategy.domain.reservoir.SpeciesStatus
import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.domain.rules.StrategyField
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Para qué se consultó a la IA. */
enum class AiKind { CHECK_IN, PLAN, REVIEW, SHEET, CHAT }

/**
 * Una consulta a la IA tal como quedó (fase 8: «¿IA o reglas?»): la respuesta (recortada), si
 * pasó la validación, los motivos si no, o el error de red. Si no es válida, mandan las reglas.
 */
data class AiExchange(
    val time: Instant,
    val kind: AiKind,
    val provider: String,
    val model: String,
    val response: String? = null,
    val valid: Boolean = false,
    val issues: List<AiIssue> = emptyList(),
    val error: String? = null,
    /** «MANTENER» o «CAMBIAR 1:COLUMN 2:ZONE». */
    val summary: String? = null,
    /** Primer motivo de la IA (solo si la respuesta es válida), para mostrarlo. */
    val note: String? = null,
)

/** Un cambio propuesto por la IA en una caña (una variable por caña, §5.9.2). */
data class AiChange(
    val rod: Int,
    val kind: StepKind?,
    val action: String,
    val baitName: String? = null,
    val rigName: String? = null,
    val column: Column? = null,
    val spotName: String? = null,
    val reason: String = "",
    val evidence: Evidence? = null,
)

/**
 * Ficha de un embalse según la IA: especies (true está, false no está; las que no sabe no
 * aparecen), si cree que es de abastecimiento, resumen y notas de estrategia.
 */
data class AiSheetDraft(
    val species: Map<FishSpecies, Boolean> = emptyMap(),
    val waterSupply: Boolean? = null,
    val summary: String? = null,
    val notes: List<AiNote> = emptyList(),
)

/**
 * Respuesta en el chat de la sesión: texto para el usuario y, si propone algo, cambios por caña
 * (los mismos tipos que en un aviso, uno por caña), que quedan como propuestas pendientes.
 */
data class AiChatReply(
    val text: String,
    val changes: List<AiChange> = emptyList(),
    val evidence: Evidence? = null,
)

/** Decisión en un aviso: mantener o cambiar (con los cambios por caña). */
data class AiDecision(
    /** null si la IA no dijo MANTENER ni CAMBIAR. */
    val keep: Boolean?,
    val changes: List<AiChange> = emptyList(),
    val reasons: List<String> = emptyList(),
    val evidence: Evidence? = null,
) {
    val summary: String
        get() = when (keep) {
            true -> "MANTENER"
            false -> "CAMBIAR " + changes.joinToString(" ") { "${it.rod}:${it.kind?.name ?: "?"}" }
            null -> "?"
        }
}

/** Plan antes de pescar para una caña. */
data class AiRodPlan(
    val rod: Int,
    val spotName: String? = null,
    val baitName: String? = null,
    val rigName: String? = null,
    val column: Column? = null,
    val distanceM: Double? = null,
    val reason: String = "",
    val evidence: Evidence? = null,
)

data class AiNote(val text: String, val evidence: Evidence?)

/** Consejo de la IA para un apartado de Estrategia (dónde, cuándo, cebo…). */
data class AiAdvice(val field: StrategyField?, val text: String, val evidence: Evidence?)

/** Plan antes de pescar (desde Estrategia). */
data class AiPlan(
    val summary: String,
    val evidence: Evidence?,
    val rods: List<AiRodPlan>,
    val warnings: List<AiNote> = emptyList(),
    /** Consejos por apartado, además del plan por caña. */
    val advice: List<AiAdvice> = emptyList(),
)

/** Lección que propone la IA al repasar una sesión, antes de validarla. */
data class AiLessonDraft(
    val topic: LessonTopic?,
    val text: String,
    val conditions: LessonConditions = LessonConditions(),
    val evidence: Evidence? = null,
    val knowledgeRef: String? = null,
    val correction: Boolean = false,
)

/** Motivo por el que una respuesta de la IA no se usa (todo o nada). */
enum class AiIssue {
    UNREADABLE,
    UNKNOWN_DECISION,
    CHANGES_WHEN_KEEPING,
    NO_CHANGES,
    UNKNOWN_ROD,
    DUPLICATE_ROD,
    KIND_NOT_ALLOWED,
    ZONE_NOT_ALLOWED,
    AFTER_LEGAL_END,
    STORM_ACTIVE,
    UNKNOWN_BAIT,
    UNKNOWN_RIG,
    UNKNOWN_SPOT,
    PROBABILITY,
    GRAMS,
    NIGHT,
    PROMISE,
    MISSING_EVIDENCE,
    EMPTY_TEXT,
    TEXT_TOO_LONG,
    DISTANCE_OUT_OF_RANGE,
    /** Un consejo sin apartado conocido. */
    UNKNOWN_FIELD,
    /** Menciona una hora fuera del horario legal (⚖). */
    OUTSIDE_LEGAL_HOURS,
}

/** Lo que la app sabe para validar una respuesta: cañas, equipo, puestos, hora legal y tormenta. */
data class AiContext(
    val now: Instant,
    val legalEnd: Instant?,
    val rodIds: Set<Int>,
    val gear: List<GearItem>,
    val spots: List<Spot>,
    val storm: Boolean = false,
    /** Cañas que ya no pueden cambiar de zona (límite de la fase alcanzado). */
    val zoneLimitReached: Set<Int> = emptySet(),
    /** Inicio legal de hoy: con él y [legalEnd] se comprueban las horas que cite la IA. */
    val legalStart: Instant? = null,
    val zone: ZoneId = ZoneId.of("Europe/Madrid"),
)

/**
 * Valida lo que responde la IA con las normas de CONOCIMIENTO.md (§0, §5.9): solo pasos de la
 * escalera, solo tu equipo y tus puestos, sin probabilidades, sin gramos, sin horas nocturnas,
 * sin prometer peces y con etiqueta de evidencia. Con un solo problema la respuesta entera se
 * descarta y mandan las reglas. Funciones puras.
 */
object AiValidator {
    const val MAX_TEXT = 400
    const val MAX_DISTANCE_M = 250.0

    /** Pasos que puede proponer la IA (el plan inicial lo pone la app). */
    val ALLOWED_KINDS: Set<StepKind> = StepKind.entries.toSet() - StepKind.INITIAL

    /** ⚖ A menos de 45 min del fin legal solo cambios de presentación, no de zona (§5.9.2). */
    val ZONE_KINDS: Set<StepKind> = setOf(StepKind.ZONE, StepKind.INFLOW)

    private val PROBABILITY = Regex("""\d+([.,]\d+)?\s*%|probabilidad|\bconfianza\b|\bpor ciento\b""", RegexOption.IGNORE_CASE)
    private val GRAMS = Regex("""\b\d+([.,]\d+)?\s*(g|gr|grs|gramos?|kg|kgs|kilos?|kilogramos?)\b""", RegexOption.IGNORE_CASE)
    private val NIGHT = Regex("""\bnoche\b|\bnocturn\w*|\bmadrugada\b|\bde madrugada\b""", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("""\b([01]?\d|2[0-3])[:h]([0-5]\d)\b""")
    private val PROMISE = Regex("""garantiz\w*|seguro que (pica|picar|habr|vas a)|vas a pescar seguro|hay peces (en|ahí)""", RegexOption.IGNORE_CASE)

    fun textIssues(text: String?, required: Boolean = true, maxLength: Int = MAX_TEXT): Set<AiIssue> = buildSet {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) {
            if (required) add(AiIssue.EMPTY_TEXT)
            return@buildSet
        }
        if (t.length > maxLength) add(AiIssue.TEXT_TOO_LONG)
        if (PROBABILITY.containsMatchIn(t)) add(AiIssue.PROBABILITY)
        if (GRAMS.containsMatchIn(t)) add(AiIssue.GRAMS)
        if (NIGHT.containsMatchIn(t)) add(AiIssue.NIGHT)
        if (PROMISE.containsMatchIn(t)) add(AiIssue.PROMISE)
    }

    /**
     * ⚖ Horas citadas en el texto («a las 07:30», «7h30») fuera del horario legal de hoy.
     * Sin ventana legal no se comprueba.
     */
    fun legalHoursIssue(text: String?, ctx: AiContext): AiIssue? {
        val start = ctx.legalStart ?: return null
        val end = ctx.legalEnd ?: return null
        val from = start.atZone(ctx.zone).toLocalTime()
        val to = end.atZone(ctx.zone).toLocalTime()
        val outside = CLOCK.findAll(text.orEmpty()).any { m ->
            val t = LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            t.isBefore(from) || t.isAfter(to)
        }
        return if (outside) AiIssue.OUTSIDE_LEGAL_HOURS else null
    }

    private fun MutableSet<AiIssue>.checkText(text: String?, ctx: AiContext, required: Boolean = true) {
        addAll(textIssues(text, required))
        legalHoursIssue(text, ctx)?.let(::add)
    }

    fun validate(decision: AiDecision, ctx: AiContext): Set<AiIssue> = buildSet {
        val keep = decision.keep
        if (keep == null) add(AiIssue.UNKNOWN_DECISION)
        if (decision.evidence == null) add(AiIssue.MISSING_EVIDENCE)
        if (decision.reasons.isEmpty()) add(AiIssue.EMPTY_TEXT)
        decision.reasons.forEach { checkText(it, ctx) }
        if (keep == true && decision.changes.isNotEmpty()) add(AiIssue.CHANGES_WHEN_KEEPING)
        if (keep == false) {
            if (decision.changes.isEmpty()) add(AiIssue.NO_CHANGES)
            checkChanges(decision.changes, ctx)
        }
    }

    /** Cambios por caña (aviso o chat): caña conocida, uno por caña, tipo permitido, ⚖ zona, equipo y textos. */
    private fun MutableSet<AiIssue>.checkChanges(changes: List<AiChange>, ctx: AiContext) {
        val ended = ctx.legalEnd != null && !ctx.now.isBefore(ctx.legalEnd)
        if (ended) add(AiIssue.AFTER_LEGAL_END)
        if (ctx.storm) add(AiIssue.STORM_ACTIVE)
        val noZone = ctx.legalEnd != null && Duration.between(ctx.now, ctx.legalEnd) <= GuidedEngine.LEGAL_NO_ZONE
        if (changes.map { it.rod }.toSet().size != changes.size) add(AiIssue.DUPLICATE_ROD)
        changes.forEach { c ->
            if (c.rod !in ctx.rodIds) add(AiIssue.UNKNOWN_ROD)
            val kind = c.kind
            if (kind == null || kind !in ALLOWED_KINDS) add(AiIssue.KIND_NOT_ALLOWED)
            if (kind in ZONE_KINDS && (noZone || c.rod in ctx.zoneLimitReached)) add(AiIssue.ZONE_NOT_ALLOWED)
            if (c.evidence == null) add(AiIssue.MISSING_EVIDENCE)
            addAll(gearIssues(c.baitName, c.rigName, c.spotName, ctx))
            checkText(c.action, ctx)
            checkText(c.reason, ctx)
        }
    }

    /** Respuesta del chat: texto válido (más largo que un motivo) y, si propone cambios, que valgan. */
    fun validate(reply: AiChatReply, ctx: AiContext): Set<AiIssue> = buildSet {
        addAll(textIssues(reply.text, maxLength = MAX_CHAT_TEXT))
        legalHoursIssue(reply.text, ctx)?.let(::add)
        if (reply.changes.isNotEmpty()) checkChanges(reply.changes, ctx)
    }

    /** Respuesta del chat como mucho de esta longitud. */
    const val MAX_CHAT_TEXT = 1_200

    fun validate(plan: AiPlan, ctx: AiContext): Set<AiIssue> = buildSet {
        if (plan.evidence == null) add(AiIssue.MISSING_EVIDENCE)
        checkText(plan.summary, ctx)
        if (plan.rods.isEmpty()) add(AiIssue.NO_CHANGES)
        if (plan.rods.map { it.rod }.toSet().size != plan.rods.size) add(AiIssue.DUPLICATE_ROD)
        plan.rods.forEach { r ->
            if (r.rod !in ctx.rodIds) add(AiIssue.UNKNOWN_ROD)
            if (r.evidence == null) add(AiIssue.MISSING_EVIDENCE)
            r.distanceM?.let { if (it < 0 || it > MAX_DISTANCE_M) add(AiIssue.DISTANCE_OUT_OF_RANGE) }
            addAll(gearIssues(r.baitName, r.rigName, r.spotName, ctx))
            checkText(r.reason, ctx)
        }
        plan.warnings.forEach { w ->
            if (w.evidence == null) add(AiIssue.MISSING_EVIDENCE)
            checkText(w.text, ctx)
        }
        plan.advice.forEach { a ->
            if (a.field == null) add(AiIssue.UNKNOWN_FIELD)
            if (a.evidence == null) add(AiIssue.MISSING_EVIDENCE)
            checkText(a.text, ctx)
        }
    }

    private fun gearIssues(bait: String?, rig: String?, spot: String?, ctx: AiContext): Set<AiIssue> = buildSet {
        if (bait != null && ctx.gear.find(GearCategory.BAIT, bait) == null) add(AiIssue.UNKNOWN_BAIT)
        if (rig != null && ctx.gear.find(GearCategory.RIG, rig) == null) add(AiIssue.UNKNOWN_RIG)
        if (spot != null && ctx.spots.none { same(it.name, spot) }) add(AiIssue.UNKNOWN_SPOT)
    }

    internal fun same(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

    internal fun List<GearItem>.find(category: GearCategory, name: String): GearItem? =
        firstOrNull { it.category == category && same(it.name, name) }

    /** Una lección: tema conocido, texto válido, evidencia y, si cita un puesto, que sea tuyo. */
    fun validate(draft: AiLessonDraft, spots: List<Spot>): Set<AiIssue> = buildSet {
        if (draft.topic == null) add(AiIssue.UNKNOWN_FIELD)
        if (draft.evidence == null) add(AiIssue.MISSING_EVIDENCE)
        addAll(textIssues(draft.text))
        draft.conditions.spotName?.let { name -> if (spots.none { same(it.name, name) }) add(AiIssue.UNKNOWN_SPOT) }
    }

    /** Lección válida → propuesta pendiente de que el usuario la acepte. */
    fun toLesson(draft: AiLessonDraft, id: String, now: Instant, sessionIds: List<Long>): Lesson = Lesson(
        id = id,
        createdAt = now,
        topic = draft.topic ?: LessonTopic.OTHER,
        text = draft.text.trim(),
        conditions = draft.conditions,
        evidence = draft.evidence ?: Evidence.PURPLE,
        source = LessonSource.AI,
        status = LessonStatus.PROPOSED,
        sessionIds = sessionIds,
        knowledgeRef = draft.knowledgeRef?.trim()?.take(20),
        correction = draft.correction,
    )

    /** Ficha de embalse: resumen y notas con texto válido y evidencia. Las especies son un enum cerrado. */
    fun validate(draft: AiSheetDraft): Set<AiIssue> = buildSet {
        addAll(textIssues(draft.summary))
        draft.notes.forEach { n ->
            if (n.evidence == null) add(AiIssue.MISSING_EVIDENCE)
            addAll(textIssues(n.text))
        }
    }

    /**
     * Ficha válida → ficha guardable. Lo que el usuario ya confirmó (especies y cebado) se
     * respeta; lo que la IA no sabe (null) no se rellena. Todo lo de la IA queda sin confirmar.
     */
    fun toSheet(draft: AiSheetDraft, reservoirId: String, previous: ReservoirSheet?, now: Instant): ReservoirSheet {
        val confirmed = previous?.species.orEmpty().filterValues { it.confirmedByUser }
        val proposed = draft.species.filterKeys { it !in confirmed }.mapValues { (_, present) -> SpeciesStatus(present, confirmedByUser = false) }
        return ReservoirSheet(
            reservoirId = reservoirId,
            species = confirmed + proposed,
            groundbaitAllowed = previous?.groundbaitAllowed,
            aiWaterSupply = draft.waterSupply,
            summary = draft.summary?.trim(),
            notes = draft.notes.take(MAX_SHEET_NOTES).map { SheetNote(it.text.trim(), it.evidence ?: Evidence.PURPLE) },
            aiGeneratedAt = now,
            updatedAt = now,
        )
    }

    const val MAX_SHEET_NOTES = 6

    /** Cambio validado → propuesta pendiente de aceptar o rechazar, como las de las reglas. */
    fun toProposal(change: AiChange, gear: List<GearItem>, now: Instant): Proposal {
        val bait = change.baitName?.let { gear.find(GearCategory.BAIT, it) }
        val rig = change.rigName?.let { gear.find(GearCategory.RIG, it) }
        return Proposal(
            kind = change.kind ?: StepKind.PRESENTATION,
            situation = Situation.ASSISTANT,
            bait = bait?.baitType,
            baitName = bait?.name,
            column = change.column ?: rig?.rigType?.column,
            rigName = rig?.name,
            evidence = change.evidence ?: Evidence.PURPLE,
            createdAt = now,
            source = ProposalSource.AI,
            note = listOf(change.action.trim(), change.reason.trim()).filter { it.isNotEmpty() }.joinToString(" — "),
            spotName = change.spotName?.trim(),
        )
    }
}
