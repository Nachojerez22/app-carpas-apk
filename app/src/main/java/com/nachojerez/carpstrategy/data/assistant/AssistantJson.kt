package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.assistant.AiChange
import com.nachojerez.carpstrategy.domain.assistant.AiDecision
import com.nachojerez.carpstrategy.domain.assistant.AiNote
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.assistant.AiRodPlan
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.rules.Evidence
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
    const val MAX_STORED = 4_000

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
        val changes = o.array("cambios").mapNotNull { e ->
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
        val reasons = o.array("motivos").mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        return AiDecision(keep, changes, reasons, evidence(o.string("evidencia")))
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
        return AiPlan(o.string("resumen").orEmpty(), evidence(o.string("evidencia")), rods, warnings)
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

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.trim()?.toIntOrNull() }

    private fun JsonObject.array(key: String): List<kotlinx.serialization.json.JsonElement> = (this[key] as? JsonArray).orEmpty()
}
