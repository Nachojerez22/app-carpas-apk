package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** De qué trata una lección. */
enum class LessonTopic { BAIT, WHERE, DISTANCE, PRESENTATION, WHEN, GROUNDBAIT, AVOID, OTHER }

/** Quién la propuso: la IA al repasar una sesión o el propio usuario. */
enum class LessonSource { AI, USER }

/** Las de la IA empiezan propuestas; solo las aceptadas se usan. */
enum class LessonStatus { PROPOSED, ACCEPTED, REJECTED }

/** Cuándo vale una lección. Cada condición vacía vale para todo. */
data class LessonConditions(
    val phase: FishingPhase? = null,
    val waterMinC: Double? = null,
    val waterMaxC: Double? = null,
    val dayPart: DayPart? = null,
    val spotName: String? = null,
) {
    fun matches(phase: FishingPhase?, waterC: Double?, dayPart: DayPart?, spotNames: Collection<String>): Boolean {
        if (this.phase != null && phase != null && this.phase != phase) return false
        if (waterC != null) {
            if (waterMinC != null && waterC < waterMinC) return false
            if (waterMaxC != null && waterC > waterMaxC) return false
        }
        if (this.dayPart != null && dayPart != null && this.dayPart != dayPart) return false
        if (spotName != null && spotNames.none { it.trim().equals(spotName.trim(), ignoreCase = true) }) return false
        return true
    }

    val isEmpty: Boolean get() = phase == null && waterMinC == null && waterMaxC == null && dayPart == null && spotName == null
}

/**
 * Conocimiento aprendido en Brovales con tus sesiones (§9, §5.10). Completa o corrige
 * `CONOCIMIENTO.md`: [knowledgeRef] dice qué apartado y [correction] si lo contradice. Se
 * exporta en Markdown para incorporarlo al documento en una versión nueva de la app.
 */
data class Lesson(
    val id: String,
    val createdAt: Instant,
    val topic: LessonTopic,
    val text: String,
    val conditions: LessonConditions = LessonConditions(),
    val evidence: Evidence = Evidence.PURPLE,
    val source: LessonSource,
    val status: LessonStatus,
    /** Sesiones en las que se basa. */
    val sessionIds: List<Long> = emptyList(),
    /** Apartado de CONOCIMIENTO.md que confirma o corrige (p. ej. «§5.8»). */
    val knowledgeRef: String? = null,
    val correction: Boolean = false,
)

/** Operaciones con las lecciones. Funciones puras. */
object Lessons {
    const val MAX_TEXT = 300

    fun accepted(lessons: List<Lesson>): List<Lesson> = lessons.filter { it.status == LessonStatus.ACCEPTED }

    fun matching(lessons: List<Lesson>, phase: FishingPhase?, waterC: Double?, dayPart: DayPart?, spotNames: Collection<String>): List<Lesson> =
        accepted(lessons).filter { it.conditions.matches(phase, waterC, dayPart, spotNames) }

    fun upsert(lessons: List<Lesson>, lesson: Lesson): List<Lesson> =
        if (lessons.any { it.id == lesson.id }) lessons.map { if (it.id == lesson.id) lesson else it } else lessons + lesson

    fun setStatus(lessons: List<Lesson>, id: String, status: LessonStatus): List<Lesson> =
        lessons.map { if (it.id == id) it.copy(status = status) else it }

    /** Lecciones nuevas que no repiten el texto de otra (sin mayúsculas ni espacios de más). */
    fun addNew(lessons: List<Lesson>, new: List<Lesson>): List<Lesson> {
        fun norm(s: String) = s.lowercase().replace(Regex("\\s+"), " ").trim()
        val known = lessons.map { norm(it.text) }.toMutableSet()
        return lessons + new.filter { known.add(norm(it.text)) }
    }

    /**
     * Markdown para incorporar a `CONOCIMIENTO.md`: solo las aceptadas, agrupadas por tema, con
     * sus condiciones, evidencia, sesiones y si corrigen algo.
     */
    fun toMarkdown(lessons: List<Lesson>, zone: ZoneId, now: Instant, topicTitle: (LessonTopic) -> String, conditionsText: (LessonConditions) -> String): String = buildString {
        val date = DateTimeFormatter.ISO_LOCAL_DATE
        appendLine("## Conocimiento aprendido en Brovales (exportado ${date.format(now.atZone(zone))})")
        appendLine()
        appendLine("> Lecciones aceptadas por el usuario a partir de sus sesiones. Evidencia por defecto 🟣 (hipótesis local).")
        val accepted = accepted(lessons)
        if (accepted.isEmpty()) {
            appendLine()
            appendLine("_Aún no hay lecciones aceptadas._")
        }
        accepted.groupBy { it.topic }.toSortedMap().forEach { (topic, list) ->
            appendLine()
            appendLine("### ${topicTitle(topic)}")
            appendLine()
            list.sortedBy { it.createdAt }.forEach { l ->
                val conds = conditionsText(l.conditions).takeIf { it.isNotBlank() }?.let { " — _${it}_" }.orEmpty()
                val ref = l.knowledgeRef?.let { if (l.correction) " · **corrige $it**" else " · confirma $it" }.orEmpty()
                val sessions = if (l.sessionIds.isEmpty()) "" else " · sesiones ${l.sessionIds.joinToString(", ")}"
                val who = if (l.source == LessonSource.AI) "IA" else "usuario"
                appendLine("- ${l.evidence.emoji()} ${l.text.trim()}$conds ($who, ${date.format(l.createdAt.atZone(zone))}$sessions$ref)")
            }
        }
    }

    private fun Evidence.emoji(): String = when (this) {
        Evidence.GREEN -> "🟢"
        Evidence.YELLOW -> "🟡"
        Evidence.RED -> "🔴"
        Evidence.PURPLE -> "🟣"
        Evidence.BLUE -> "🔵"
        Evidence.REGULATION -> "⚖"
    }
}
