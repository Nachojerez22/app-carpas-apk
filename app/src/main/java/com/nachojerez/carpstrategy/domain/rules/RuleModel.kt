package com.nachojerez.carpstrategy.domain.rules

/** Niveles de la cadena de filtros (CONOCIMIENTO.md §4). */
enum class RuleLevel(val order: Int) {
    LEGALITY(0),
    HABITAT(1),
    TEMPERATURE(2),
    PHYSICAL(3),
    CATCHABILITY(4),
    EXPLORATORY(5),
    ;

    val isChained: Boolean get() = this in CHAINED

    companion object {
        val CHAINED = listOf(HABITAT, TEMPERATURE, PHYSICAL, CATCHABILITY)
    }
}

enum class RuleType {
    /** Si se cumple, no se recomienda ninguna sesión. */
    HARD_FILTER,

    /** Información o aviso; no modifica la valoración. */
    WARNING,

    /** Si se cumple, multiplica el nivel por [Rule.factor] (≤ 1). */
    MULTIPLIER,

    /** Multiplica el nivel por una curva de pertenencia difusa de un parámetro. */
    MEMBERSHIP,

    /** Aporta estrategia sin modificar la valoración. */
    ADVICE,

    /** Solo se registra (peso 0). */
    RECORD,
}

/** Etiquetas de evidencia (§2) más la normativa. */
enum class Evidence { GREEN, YELLOW, RED, PURPLE, BLUE, REGULATION }

/** Peso inicial: cuánto del factor se aplica (1 − peso · (1 − factor)). */
enum class RuleWeight(val strength: Double) { HIGH(1.0), MEDIUM(0.6), LOW(0.3), ZERO(0.0) }

enum class StrategyField { WHERE, WHEN, BAIT, PRESENTATION, AVOID, NOTES }

sealed interface Condition {
    val parameter: RuleParameter

    data class Range(override val parameter: RuleParameter, val min: Double?, val max: Double?) : Condition
    data class Is(override val parameter: RuleParameter, val value: Boolean) : Condition
    data class OneOf(override val parameter: RuleParameter, val values: Set<String>) : Condition
}

data class StrategyItem(val field: StrategyField, val text: String, val evidence: Evidence?)

/** Curva lineal a trozos; fuera de los extremos se toma el valor del extremo. */
data class Membership(val parameter: RuleParameter, val points: List<Pair<Double, Double>>) {
    fun valueAt(x: Double): Double {
        if (x <= points.first().first) return points.first().second
        if (x >= points.last().first) return points.last().second
        val i = points.indexOfLast { it.first <= x }
        val (x0, y0) = points[i]
        val (x1, y1) = points[i + 1]
        return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
    }
}

data class Rule(
    val id: String,
    val level: RuleLevel,
    val type: RuleType,
    val evidence: Evidence,
    val weight: RuleWeight,
    val description: String,
    val source: String? = null,
    val conditions: List<Condition> = emptyList(),
    val factor: Double? = null,
    val membership: Membership? = null,
    val strategy: List<StrategyItem> = emptyList(),
)

data class RuleSet(
    val rules: List<Rule>,
    /** Fecha de la última revisión de la normativa (se muestra en la app). */
    val regulationReviewed: String?,
    val source: String?,
    /** Huella del contenido de rules.json: identifica con qué reglas se hizo cada valoración. */
    val fingerprint: String? = null,
)
