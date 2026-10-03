package com.nachojerez.carpstrategy.data.rules

import com.nachojerez.carpstrategy.domain.rules.Condition
import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.domain.rules.Membership
import com.nachojerez.carpstrategy.domain.rules.ParameterType
import com.nachojerez.carpstrategy.domain.rules.Rule
import com.nachojerez.carpstrategy.domain.rules.RuleIssue
import com.nachojerez.carpstrategy.domain.rules.RuleIssueCode
import com.nachojerez.carpstrategy.domain.rules.RuleLevel
import com.nachojerez.carpstrategy.domain.rules.RuleLoadResult
import com.nachojerez.carpstrategy.domain.rules.RuleParameter
import com.nachojerez.carpstrategy.domain.rules.RuleSet
import com.nachojerez.carpstrategy.domain.rules.RuleType
import com.nachojerez.carpstrategy.domain.rules.RuleWeight
import com.nachojerez.carpstrategy.domain.rules.StrategyField
import com.nachojerez.carpstrategy.domain.rules.StrategyItem
import java.security.MessageDigest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Lectura y validación de rules.json (formato `carpstrategy-reglas` v1, docs/REGLAS.md).
 * Si hay cualquier error no se carga ninguna regla y se devuelven todos los errores.
 */
object RulesJson {
    const val FORMAT = "carpstrategy-reglas"
    const val VERSION = 1

    private val ID = Regex("^[a-z0-9_]+$")
    private val PLACEHOLDER = Regex("""\{([a-z_]+)}""")

    private val LEVELS = mapOf(
        "0" to RuleLevel.LEGALITY, "1" to RuleLevel.HABITAT, "2" to RuleLevel.TEMPERATURE,
        "3" to RuleLevel.PHYSICAL, "4" to RuleLevel.CATCHABILITY, "exploratoria" to RuleLevel.EXPLORATORY,
    )
    private val TYPES = mapOf(
        "filtro_duro" to RuleType.HARD_FILTER, "aviso" to RuleType.WARNING, "multiplicador" to RuleType.MULTIPLIER,
        "pertenencia" to RuleType.MEMBERSHIP, "consejo" to RuleType.ADVICE, "registro" to RuleType.RECORD,
    )
    private val EVIDENCE = mapOf(
        "verde" to Evidence.GREEN, "amarillo" to Evidence.YELLOW, "rojo" to Evidence.RED,
        "morado" to Evidence.PURPLE, "azul" to Evidence.BLUE, "normativa" to Evidence.REGULATION,
    )
    private val WEIGHTS = mapOf("alto" to RuleWeight.HIGH, "medio" to RuleWeight.MEDIUM, "bajo" to RuleWeight.LOW, "0" to RuleWeight.ZERO)
    private val FIELDS = mapOf(
        "donde" to StrategyField.WHERE, "cuando" to StrategyField.WHEN, "cebado" to StrategyField.BAIT,
        "presentacion" to StrategyField.PRESENTATION, "evitar" to StrategyField.AVOID, "notas" to StrategyField.NOTES,
    )

    fun parse(text: String): RuleLoadResult {
        val issues = mutableListOf<RuleIssue>()
        fun fileIssue(code: RuleIssueCode, path: String? = null, vararg args: String) =
            RuleLoadResult.Invalid(listOf(RuleIssue(null, null, path, code, args.toList())))

        val root = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return fileIssue(RuleIssueCode.NOT_JSON)
        if (root.string("formato") != FORMAT) return fileIssue(RuleIssueCode.BAD_FORMAT, "formato", FORMAT)
        val version = (root["version"] as? JsonPrimitive)?.intOrNull
        if (version != VERSION) return fileIssue(RuleIssueCode.UNSUPPORTED_VERSION, "version", version?.toString() ?: "?")
        val items = root["reglas"] as? JsonArray
        if (items.isNullOrEmpty()) return fileIssue(RuleIssueCode.NO_RULES, "reglas")

        val rules = mutableListOf<Rule>()
        val seen = mutableSetOf<String>()
        items.forEachIndexed { index, element ->
            val number = index + 1
            val obj = element as? JsonObject
            if (obj == null) {
                issues += RuleIssue(number, null, null, RuleIssueCode.RULE_NOT_OBJECT)
                return@forEachIndexed
            }
            val id = obj.string("id")
            fun issue(code: RuleIssueCode, path: String?, vararg args: String) {
                issues += RuleIssue(number, id, path, code, args.toList())
            }
            val before = issues.size
            when {
                id == null -> issue(RuleIssueCode.MISSING_FIELD, "id")
                !ID.matches(id) -> issue(RuleIssueCode.INVALID_ID, "id", id)
                !seen.add(id) -> issue(RuleIssueCode.DUPLICATE_ID, "id", id)
            }
            fun <T> enumField(key: String, map: Map<String, T>): T? {
                val raw = (obj[key] as? JsonPrimitive)?.contentOrNull
                if (raw == null) {
                    issue(RuleIssueCode.MISSING_FIELD, key)
                    return null
                }
                return map[raw] ?: run {
                    issue(RuleIssueCode.INVALID_VALUE, key, raw, map.keys.joinToString(", "))
                    null
                }
            }
            val level = enumField("nivel", LEVELS)
            val type = enumField("tipo", TYPES)
            val evidence = enumField("evidencia", EVIDENCE)
            val weight = enumField("peso", WEIGHTS)
            val description = obj.string("descripcion")?.takeIf { it.isNotBlank() }
            if (description == null) issue(RuleIssueCode.MISSING_FIELD, "descripcion")

            // Reglas de coherencia con CONOCIMIENTO.md §0.
            if (evidence == Evidence.RED && weight != null && weight != RuleWeight.ZERO) issue(RuleIssueCode.RED_WITH_WEIGHT, "peso")
            if (level == RuleLevel.EXPLORATORY && weight != null && weight != RuleWeight.ZERO) issue(RuleIssueCode.EXPLORATORY_WITH_WEIGHT, "peso")
            if (type == RuleType.HARD_FILTER && level != null && level != RuleLevel.LEGALITY) issue(RuleIssueCode.HARD_FILTER_NOT_LEVEL_0, "nivel")

            val conditions = parseConditions(obj["condiciones"], ::issue)

            var factor: Double? = null
            if (type == RuleType.MULTIPLIER) {
                factor = (obj["factor"] as? JsonPrimitive)?.doubleOrNull
                when {
                    factor == null -> issue(RuleIssueCode.MISSING_FIELD, "factor")
                    factor < 0.0 || factor > 1.0 -> issue(RuleIssueCode.FACTOR_OUT_OF_RANGE, "factor", factor.toString())
                }
            }
            val membership = if (type == RuleType.MEMBERSHIP) parseMembership(obj["pertenencia"], ::issue) else null

            val strategy = parseStrategy(obj["estrategia"], ::issue)

            if (issues.size == before && id != null && level != null && type != null && evidence != null && weight != null && description != null) {
                rules += Rule(
                    id = id,
                    level = level,
                    type = type,
                    evidence = evidence,
                    weight = weight,
                    description = description,
                    source = obj.string("fuente"),
                    conditions = conditions,
                    factor = factor,
                    membership = membership,
                    strategy = strategy,
                )
            }
        }
        return if (issues.isEmpty()) {
            RuleLoadResult.Loaded(RuleSet(rules, root.string("revision_normativa"), root.string("fuente"), fingerprint(text)))
        } else {
            RuleLoadResult.Invalid(issues)
        }
    }

    private fun parseConditions(
        element: JsonElement?,
        issue: (RuleIssueCode, String?, Array<out String>) -> Unit,
    ): List<Condition> {
        if (element == null || element is JsonNull) return emptyList()
        val obj = element as? JsonObject ?: run {
            issue(RuleIssueCode.INVALID_VALUE, "condiciones", arrayOf("?", "{ … }"))
            return emptyList()
        }
        return obj.mapNotNull { (key, value) ->
            val path = "condiciones.$key"
            val parameter = RuleParameter.fromKey(key) ?: run {
                issue(RuleIssueCode.UNKNOWN_PARAMETER, path, arrayOf(key))
                return@mapNotNull null
            }
            val spec = value as? JsonObject ?: run {
                issue(RuleIssueCode.EMPTY_CONDITION, path, arrayOf())
                return@mapNotNull null
            }
            when (parameter.type) {
                ParameterType.NUMBER -> {
                    if ("es" in spec || "en" in spec) {
                        issue(RuleIssueCode.WRONG_PARAMETER_TYPE, path, arrayOf("min/max"))
                        return@mapNotNull null
                    }
                    val min = (spec["min"] as? JsonPrimitive)?.doubleOrNull
                    val max = (spec["max"] as? JsonPrimitive)?.doubleOrNull
                    when {
                        min == null && max == null -> { issue(RuleIssueCode.EMPTY_CONDITION, path, arrayOf()); null }
                        min != null && max != null && min > max -> { issue(RuleIssueCode.MIN_GREATER_THAN_MAX, path, arrayOf(min.toString(), max.toString())); null }
                        else -> Condition.Range(parameter, min, max)
                    }
                }
                ParameterType.BOOLEAN -> {
                    val value = (spec["es"] as? JsonPrimitive)?.booleanOrNull
                    if (value == null) { issue(RuleIssueCode.WRONG_PARAMETER_TYPE, path, arrayOf("es: true/false")); null } else Condition.Is(parameter, value)
                }
                ParameterType.TEXT -> {
                    val values = (spec["en"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet()
                    when {
                        values.isNullOrEmpty() -> { issue(RuleIssueCode.WRONG_PARAMETER_TYPE, path, arrayOf("en: [ … ]")); null }
                        parameter.allowedValues.isNotEmpty() && !parameter.allowedValues.containsAll(values) -> {
                            issue(RuleIssueCode.INVALID_VALUE, path, arrayOf((values - parameter.allowedValues).joinToString(), parameter.allowedValues.joinToString(", ")))
                            null
                        }
                        else -> Condition.OneOf(parameter, values)
                    }
                }
            }
        }
    }

    private fun parseMembership(
        element: JsonElement?,
        issue: (RuleIssueCode, String?, Array<out String>) -> Unit,
    ): Membership? {
        val obj = element as? JsonObject ?: run {
            issue(RuleIssueCode.MISSING_FIELD, "pertenencia", arrayOf())
            return null
        }
        val key = obj.string("parametro")
        val parameter = key?.let(RuleParameter::fromKey)
        if (parameter == null || parameter.type != ParameterType.NUMBER) {
            issue(RuleIssueCode.INVALID_MEMBERSHIP, "pertenencia.parametro", arrayOf(key ?: "?"))
            return null
        }
        val points = (obj["puntos"] as? JsonArray)?.map { p ->
            val pair = p as? JsonArray
            val x = (pair?.getOrNull(0) as? JsonPrimitive)?.doubleOrNull
            val y = (pair?.getOrNull(1) as? JsonPrimitive)?.doubleOrNull
            if (pair?.size == 2 && x != null && y != null) x to y else null
        }
        val valid = points != null && points.size >= 2 && points.all { it != null } &&
            points.filterNotNull().zipWithNext().all { (a, b) -> a.first < b.first } &&
            points.filterNotNull().all { it.second in 0.0..1.0 }
        if (!valid) {
            issue(RuleIssueCode.INVALID_MEMBERSHIP, "pertenencia.puntos", arrayOf(key))
            return null
        }
        return Membership(parameter, points!!.filterNotNull())
    }

    private fun parseStrategy(
        element: JsonElement?,
        issue: (RuleIssueCode, String?, Array<out String>) -> Unit,
    ): List<StrategyItem> {
        if (element == null || element is JsonNull) return emptyList()
        val array = element as? JsonArray ?: run {
            issue(RuleIssueCode.INVALID_VALUE, "estrategia", arrayOf("?", "[ … ]"))
            return emptyList()
        }
        return array.mapIndexedNotNull { i, item ->
            val path = "estrategia[${i + 1}]"
            val obj = item as? JsonObject
            val field = obj?.string("campo")?.let(FIELDS::get)
            val text = obj?.string("texto")?.takeIf { it.isNotBlank() }
            val evidenceRaw = obj?.string("evidencia")
            val evidence = evidenceRaw?.let(EVIDENCE::get)
            when {
                field == null -> { issue(RuleIssueCode.INVALID_VALUE, "$path.campo", arrayOf(obj?.string("campo") ?: "?", FIELDS.keys.joinToString(", "))); null }
                text == null -> { issue(RuleIssueCode.MISSING_FIELD, "$path.texto", arrayOf()); null }
                evidenceRaw != null && evidence == null -> { issue(RuleIssueCode.INVALID_VALUE, "$path.evidencia", arrayOf(evidenceRaw, EVIDENCE.keys.joinToString(", "))); null }
                else -> {
                    val unknown = PLACEHOLDER.findAll(text).map { it.groupValues[1] }.filterNot { it in RuleParameter.PLACEHOLDERS }.toList()
                    if (unknown.isNotEmpty()) { issue(RuleIssueCode.UNKNOWN_PLACEHOLDER, "$path.texto", arrayOf(unknown.joinToString())); null } else StrategyItem(field, text, evidence)
                }
            }
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

    /** Primeros 12 caracteres hexadecimales del SHA-256 del archivo. */
    fun fingerprint(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
}
