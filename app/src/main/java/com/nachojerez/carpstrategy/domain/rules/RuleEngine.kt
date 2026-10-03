package com.nachojerez.carpstrategy.domain.rules

import java.time.Instant

/** Comprobación de una condición con el valor real del contexto (para explicar el porqué). */
data class ConditionCheck(val condition: Condition, val actual: Any?, val satisfied: Boolean)

/** Regla que se ha cumplido, con el factor efectivo que ha aplicado (1 = no modifica). */
data class ActiveRule(
    val rule: Rule,
    val checks: List<ConditionCheck>,
    val appliedFactor: Double,
    /** Para reglas de pertenencia: valor del parámetro usado. */
    val membershipInput: Double? = null,
)

data class LevelResult(
    val level: RuleLevel,
    /** Producto de los factores efectivos del nivel (0–1). */
    val value: Double,
    val rules: List<ActiveRule>,
)

/** Demanda alimentaria esperada por la temperatura del agua (§5.1). Nunca en gramos. */
enum class FeedingDemand { VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH, UNCERTAIN_HEAT }

enum class FavorabilityBand { VERY_UNFAVORABLE, UNFAVORABLE, INTERMEDIATE, FAVORABLE, VERY_FAVORABLE }

data class AdviceItem(val text: String, val evidence: Evidence, val ruleId: String, val level: RuleLevel)

data class StrategyResult(
    val evaluatedAt: Instant,
    /** Algún filtro duro impide recomendar sesión. */
    val blocked: Boolean,
    val blockingRules: List<ActiveRule>,
    /** 0–1; null si falta la temperatura del agua (la variable principal). */
    val favorability: Double?,
    val band: FavorabilityBand?,
    val levels: List<LevelResult>,
    /** Nivel con el valor más bajo: el que limita el resultado. */
    val limitingLevel: RuleLevel?,
    val demand: FeedingDemand?,
    val advice: Map<StrategyField, List<AdviceItem>>,
    val activeRules: List<ActiveRule>,
    /** Reglas que no se han podido evaluar por falta de datos, con los parámetros que faltan. */
    val notEvaluable: List<Pair<Rule, List<RuleParameter>>>,
)

/**
 * Motor de reglas: cadena de filtros y multiplicadores por niveles, NO suma de puntos
 * (CONOCIMIENTO.md §0.3 y §4).
 *
 * 1. Nivel 0: si se cumple algún filtro duro, no se recomienda sesión.
 * 2. Niveles 1–4: cada nivel vale el producto de los factores efectivos de sus reglas activas
 *    (sin reglas activas vale 1). El factor efectivo es `1 − peso · (1 − factor)`, con factores
 *    ≤ 1: un nivel nunca sube a otro, solo puede limitar.
 * 3. Valoración = producto de los niveles 1–4. Así, si un nivel inferior es bajo, los superiores
 *    no lo compensan. Se informa del nivel limitante.
 * 4. Las reglas de peso 0 (rojo, exploratorias, avisos) se muestran pero no modifican nada.
 */
object RuleEngine {

    fun evaluate(rules: RuleSet, context: RuleContext, at: Instant): StrategyResult {
        val active = mutableListOf<ActiveRule>()
        val notEvaluable = mutableListOf<Pair<Rule, List<RuleParameter>>>()

        for (rule in rules.rules) {
            val required = rule.conditions.map { it.parameter } + listOfNotNull(rule.membership?.parameter)
            val missing = required.filterNot(context::has).distinct()
            if (missing.isNotEmpty()) {
                notEvaluable += rule to missing
                continue
            }
            val checks = rule.conditions.map { check(it, context) }
            if (!checks.all { it.satisfied }) continue

            val (factor, input) = when (rule.type) {
                RuleType.MULTIPLIER -> effective(rule.factor ?: 1.0, rule.weight) to null
                RuleType.MEMBERSHIP -> {
                    val membership = rule.membership!!
                    val x = context.numbers.getValue(membership.parameter)
                    effective(membership.valueAt(x), rule.weight) to x
                }
                else -> 1.0 to null
            }
            active += ActiveRule(rule, checks, factor, input)
        }

        val blocking = active.filter { it.rule.type == RuleType.HARD_FILTER }
        val levels = RuleLevel.CHAINED.map { level ->
            val levelRules = active.filter { it.rule.level == level }
            LevelResult(level, levelRules.fold(1.0) { acc, r -> acc * r.appliedFactor }, levelRules)
        }
        val waterTemp = context.numbers[RuleParameter.WATER_TEMP]
        val favorability = when {
            blocking.isNotEmpty() -> 0.0
            waterTemp == null -> null
            else -> levels.fold(1.0) { acc, l -> acc * l.value }
        }
        val limiting = if (favorability == null || blocking.isNotEmpty()) null else levels.minByOrNull { it.value }?.takeIf { it.value < 1.0 }?.level

        return StrategyResult(
            evaluatedAt = at,
            blocked = blocking.isNotEmpty(),
            blockingRules = blocking,
            favorability = favorability,
            band = favorability?.let(::band),
            levels = levels,
            limitingLevel = limiting,
            demand = waterTemp?.let(::demand),
            advice = advice(active, context),
            activeRules = active.sortedWith(compareBy({ it.rule.level.order }, { it.rule.evidence.ordinal })),
            notEvaluable = notEvaluable,
        )
    }

    fun effective(factor: Double, weight: RuleWeight): Double =
        (1.0 - weight.strength * (1.0 - factor.coerceIn(0.0, 1.0))).coerceIn(0.0, 1.0)

    fun band(value: Double): FavorabilityBand = when {
        value < 0.2 -> FavorabilityBand.VERY_UNFAVORABLE
        value < 0.4 -> FavorabilityBand.UNFAVORABLE
        value < 0.6 -> FavorabilityBand.INTERMEDIATE
        value < 0.8 -> FavorabilityBand.FAVORABLE
        else -> FavorabilityBand.VERY_FAVORABLE
    }

    /** Bandas de §5.1 (umbrales aproximados de la literatura). */
    fun demand(waterC: Double): FeedingDemand = when {
        waterC < 10 -> FeedingDemand.VERY_LOW
        waterC < 14 -> FeedingDemand.LOW
        waterC < 20 -> FeedingDemand.MEDIUM
        waterC < 24 -> FeedingDemand.HIGH
        waterC <= 28 -> FeedingDemand.VERY_HIGH
        else -> FeedingDemand.UNCERTAIN_HEAT
    }

    private fun check(condition: Condition, context: RuleContext): ConditionCheck = when (condition) {
        is Condition.Range -> {
            val v = context.numbers[condition.parameter]
            ConditionCheck(
                condition,
                v,
                v != null && (condition.min == null || v >= condition.min) && (condition.max == null || v <= condition.max),
            )
        }
        is Condition.Is -> {
            val v = context.booleans[condition.parameter]
            ConditionCheck(condition, v, v == condition.value)
        }
        is Condition.OneOf -> {
            val v = context.texts[condition.parameter]
            ConditionCheck(condition, v, v != null && v in condition.values)
        }
    }

    private fun advice(active: List<ActiveRule>, context: RuleContext): Map<StrategyField, List<AdviceItem>> =
        active
            .sortedWith(compareBy({ it.rule.level.order }, { it.rule.evidence.ordinal }))
            .flatMap { a ->
                a.rule.strategy.map { item ->
                    item.field to AdviceItem(fill(item.text, context), item.evidence ?: a.rule.evidence, a.rule.id, a.rule.level)
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, items) -> items.distinctBy { it.text } }

    private val PLACEHOLDER = Regex("""\{([a-z_]+)\}""")

    fun fill(text: String, context: RuleContext): String =
        PLACEHOLDER.replace(text) { m -> context.placeholders[m.groupValues[1]] ?: "?" }
}
