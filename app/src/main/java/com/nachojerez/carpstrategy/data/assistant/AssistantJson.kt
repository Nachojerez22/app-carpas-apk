package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.advice.DayPart
import com.nachojerez.carpstrategy.domain.advice.LessonConditions
import com.nachojerez.carpstrategy.domain.advice.LessonTopic
import com.nachojerez.carpstrategy.domain.assistant.AiAdvice
import com.nachojerez.carpstrategy.domain.assistant.AiChange
import com.nachojerez.carpstrategy.domain.assistant.AiChatReply
import com.nachojerez.carpstrategy.domain.assistant.AiDecision
import com.nachojerez.carpstrategy.domain.assistant.AiLessonDraft
import com.nachojerez.carpstrategy.domain.assistant.AiNote
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.assistant.AiRodPlan
import com.nachojerez.carpstrategy.domain.assistant.AiSheetDraft
import com.nachojerez.carpstrategy.domain.assistant.AiSpotPick
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.reservoir.FishSpecies
import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.domain.rules.StrategyField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Lee lo que responde la IA. Tolerante con el envoltorio (```json … ```) pero no con el
 * contenido: un campo que no se entiende queda null y el validador lo rechaza.
 */
object AssistantJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Respuesta guardada en el registro como mucho con esta longitud. */
    const val MAX_STORED = 8_000

    fun objectIn(text: String): JsonObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(text.substring(start, end + 1)).jsonObject }.getOrNull()
    }

    fun parseDecision(text: String): AiDecision? {
        val o = objectIn(text) ?: return null
        val keep = when (o.string("decision")?.uppercase()?.trim()) {
            "MANTENER" -> true
            "CAMBIAR" -> false
            else -> null
        }
        val changes = changes(o)
        val reasons = o.array("motivos").mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        return AiDecision(keep, changes, reasons, evidence(o.string("evidencia")))
    }

    /** Respuesta del chat de la sesión (`{"respuesta":"…","cambios":[…],"evidencia":"🟡"}`). */
    fun parseChat(text: String): AiChatReply? {
        val o = objectIn(text) ?: return null
        val answer = o.string("respuesta") ?: o.string("texto") ?: return null
        return AiChatReply(answer, changes(o), evidence(o.string("evidencia")))
    }

    private fun changes(o: JsonObject): List<AiChange> = o.array("cambios").mapNotNull { e ->
        val c = e as? JsonObject ?: return@mapNotNull null
        AiChange(
            rod = c.int("cana") ?: c.int("caña") ?: -1,
            kind = c.string("tipo")?.let { name -> StepKind.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } },
            action = c.string("accion") ?: c.string("acción").orEmpty(),
            baitName = c.string("cebo"),
            rigName = c.string("montaje"),
            column = c.string("columna")?.let { name -> Column.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } },
            spotName = c.string("puesto"),
            reason = c.string("motivo").orEmpty(),
            evidence = evidence(c.string("evidencia")),
        )
    }

    fun parsePlan(text: String): AiPlan? {
        val o = objectIn(text) ?: return null
        val rods = o.array("canas").mapNotNull { e ->
            val c = e as? JsonObject ?: return@mapNotNull null
            AiRodPlan(
                rod = c.int("cana") ?: c.int("caña") ?: -1,
                spotName = c.string("puesto"),
                baitName = c.string("cebo"),
                rigName = c.string("montaje"),
                column = c.string("columna")?.let { name -> Column.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } },
                distanceM = (c["distancia_m"] as? JsonPrimitive)?.doubleOrNull,
                reason = c.string("motivo").orEmpty(),
                evidence = evidence(c.string("evidencia")),
            )
        }
        val warnings = o.array("avisos").mapNotNull { e ->
            val w = e as? JsonObject ?: return@mapNotNull null
            AiNote(w.string("texto").orEmpty(), evidence(w.string("evidencia")))
        }
        val advice = o.array("consejos").mapNotNull { e ->
            val a = e as? JsonObject ?: return@mapNotNull null
            AiAdvice(field(a.string("apartado")), a.string("texto").orEmpty(), evidence(a.string("evidencia")))
        }
        val best = (o["puesto_recomendado"] as? JsonObject)?.let { b ->
            b.string("puesto")?.takeIf { it.isNotBlank() }?.let { AiSpotPick(it, b.string("motivo").orEmpty(), evidence(b.string("evidencia"))) }
        }
        return AiPlan(o.string("resumen").orEmpty(), evidence(o.string("evidencia")), rods, warnings, advice, best)
    }

    /** Apartado de Estrategia por nombre (WHERE…) o en español (dónde, cuándo…). */
    fun field(text: String?): StrategyField? {
        val t = text?.trim()?.lowercase() ?: return null
        StrategyField.entries.firstOrNull { it.name.equals(t, ignoreCase = true) }?.let { return it }
        return when (t) {
            "donde", "dónde" -> StrategyField.WHERE
            "cuando", "cuándo" -> StrategyField.WHEN
            "cebado", "cebo" -> StrategyField.BAIT
            "presentacion", "presentación" -> StrategyField.PRESENTATION
            "evitar" -> StrategyField.AVOID
            "notas" -> StrategyField.NOTES
            else -> null
        }
    }

    /** Lecciones del repaso de una sesión (`{"lecciones":[…]}`). */
    fun parseLessons(text: String): List<AiLessonDraft>? {
        val o = objectIn(text) ?: return null
        return o.array("lecciones").mapNotNull { e ->
            val l = e as? JsonObject ?: return@mapNotNull null
            val c = l["condiciones"] as? JsonObject
            AiLessonDraft(
                topic = l.string("tema")?.let { t -> LessonTopic.entries.firstOrNull { it.name.equals(t, ignoreCase = true) } ?: topicFromField(t) },
                text = l.string("texto").orEmpty(),
                conditions = LessonConditions(
                    phase = c?.string("fase")?.let { p -> FishingPhase.entries.firstOrNull { it.name.equals(p, ignoreCase = true) } },
                    waterMinC = (c?.get("agua_min_c") as? JsonPrimitive)?.doubleOrNull,
                    waterMaxC = (c?.get("agua_max_c") as? JsonPrimitive)?.doubleOrNull,
                    dayPart = c?.string("momento")?.let { d -> DayPart.entries.firstOrNull { it.name.equals(d, ignoreCase = true) } },
                    spotName = c?.string("puesto"),
                ),
                evidence = evidence(l.string("evidencia")),
                knowledgeRef = l.string("apartado"),
                correction = (l["corrige"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false,
            )
        }
    }

    /** Ficha de un embalse (`{"especies":[…],"abastecimiento":…,"resumen":…,"notas":[…]}`). */
    fun parseSheet(text: String): AiSheetDraft? {
        val o = objectIn(text) ?: return null
        val species = o.array("especies").mapNotNull { e ->
            val sp = e as? JsonObject ?: return@mapNotNull null
            val kind = sp.string("especie")?.let { n -> FishSpecies.entries.firstOrNull { it.name.equals(n.trim(), ignoreCase = true) } } ?: return@mapNotNull null
            val present = sp.bool("presente") ?: return@mapNotNull null
            kind to present
        }.toMap()
        val notes = o.array("notas").mapNotNull { e ->
            val n = e as? JsonObject ?: return@mapNotNull null
            AiNote(n.string("texto").orEmpty(), evidence(n.string("evidencia")))
        }
        return AiSheetDraft(species, o.bool("abastecimiento"), o.string("resumen"), notes)
    }

    private fun topicFromField(text: String): LessonTopic? = when (field(text)) {
        StrategyField.WHERE -> LessonTopic.WHERE
        StrategyField.WHEN -> LessonTopic.WHEN
        StrategyField.BAIT -> LessonTopic.GROUNDBAIT
        StrategyField.PRESENTATION -> LessonTopic.PRESENTATION
        StrategyField.AVOID -> LessonTopic.AVOID
        StrategyField.NOTES -> LessonTopic.OTHER
        null -> null
    }

    /** Etiqueta de evidencia: emoji (🟢🟡🔴🟣🔵⚖) o nombre. */
    fun evidence(text: String?): Evidence? {
        val t = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            t.contains("🟢") -> Evidence.GREEN
            t.contains("🟡") -> Evidence.YELLOW
            t.contains("🔴") -> Evidence.RED
            t.contains("🟣") -> Evidence.PURPLE
            t.contains("🔵") -> Evidence.BLUE
            t.contains("⚖") -> Evidence.REGULATION
            else -> when (t.lowercase()) {
                "green", "verde" -> Evidence.GREEN
                "yellow", "amarillo", "amarilla" -> Evidence.YELLOW
                "red", "rojo", "roja" -> Evidence.RED
                "purple", "morado", "morada", "violeta" -> Evidence.PURPLE
                "blue", "azul" -> Evidence.BLUE
                "regulation", "normativa" -> Evidence.REGULATION
                else -> null
            }
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.lowercase()?.let {
        when (it) {
            "true", "si", "sí" -> true
            "false", "no" -> false
            else -> null
        }
    }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.trim()?.toIntOrNull() }

    private fun JsonObject.array(key: String): List<kotlinx.serialization.json.JsonElement> = (this[key] as? JsonArray).orEmpty()
}
