package com.nachojerez.carpstrategy.domain.rules

/** Error al cargar rules.json. La UI lo traduce con [code] y [args]. */
data class RuleIssue(
    /** Posición de la regla (1, 2, …); null si afecta a todo el archivo. */
    val ruleNumber: Int?,
    val ruleId: String?,
    /** Campo afectado, p. ej. "condiciones.temp_agua_c". */
    val path: String?,
    val code: RuleIssueCode,
    val args: List<String> = emptyList(),
)

enum class RuleIssueCode {
    NOT_JSON,
    BAD_FORMAT,
    UNSUPPORTED_VERSION,
    NO_RULES,
    RULE_NOT_OBJECT,
    MISSING_FIELD,
    INVALID_ID,
    DUPLICATE_ID,
    INVALID_VALUE,
    UNKNOWN_PARAMETER,
    WRONG_PARAMETER_TYPE,
    MIN_GREATER_THAN_MAX,
    EMPTY_CONDITION,
    FACTOR_OUT_OF_RANGE,
    INVALID_MEMBERSHIP,
    RED_WITH_WEIGHT,
    EXPLORATORY_WITH_WEIGHT,
    HARD_FILTER_NOT_LEVEL_0,
    UNKNOWN_PLACEHOLDER,
}

sealed interface RuleLoadResult {
    data class Loaded(val ruleSet: RuleSet) : RuleLoadResult
    data class Invalid(val issues: List<RuleIssue>) : RuleLoadResult
}

/** Origen de las reglas (rules.json en la app). */
interface RulesRepository {
    suspend fun load(): RuleLoadResult
}
