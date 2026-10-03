package com.nachojerez.carpstrategy.domain.rules

enum class ParameterType { NUMBER, BOOLEAN, TEXT }

/**
 * Parámetros que pueden usar las reglas. [key] es el nombre en rules.json (docs/REGLAS.md).
 * Añadir uno aquí exige rellenarlo en [RuleContextBuilder].
 */
enum class RuleParameter(val key: String, val type: ParameterType, val allowedValues: Set<String> = emptySet()) {
    WATER_TEMP("temp_agua_c", ParameterType.NUMBER),
    WATER_MEASURED("agua_medida", ParameterType.BOOLEAN),
    WATER_TREND_3D("tendencia_agua_3d_c", ParameterType.NUMBER),
    SEASON("estacion", ParameterType.TEXT, setOf("invierno", "primavera", "verano", "otono")),
    AIR_TEMP_24H("temp_aire_24h_c", ParameterType.NUMBER),
    AIR_TREND("tendencia_aire_c", ParameterType.NUMBER),
    HOT_DAYS("dias_calor", ParameterType.NUMBER),
    COLD_DAYS("dias_frio", ParameterType.NUMBER),
    WIND_24H("viento_24h_kmh", ParameterType.NUMBER),
    WIND_PERSISTENCE_24H("viento_persistencia_24h", ParameterType.NUMBER),
    GUST_MAX_24H("racha_max_24h_kmh", ParameterType.NUMBER),
    UNCERTAIN_WIND_HOURS("horas_viento_incierto", ParameterType.NUMBER),
    RAIN_24H("lluvia_24h_mm", ParameterType.NUMBER),
    RAIN_72H("lluvia_72h_mm", ParameterType.NUMBER),
    RUNOFF("escorrentia", ParameterType.BOOLEAN),
    LEVEL_DELTA_7D("nivel_delta_7d_hm3", ParameterType.NUMBER),
    LEVEL_PERCENT("nivel_pct", ParameterType.NUMBER),
    PRESSURE_DELTA_24H("presion_delta_24h_hpa", ParameterType.NUMBER),
    CLOUD_COVER("nubosidad_pct", ParameterType.NUMBER),
    MOON_ILLUMINATION("luna_iluminacion", ParameterType.NUMBER),
    LEGAL_WINDOW_AVAILABLE("ventana_legal_disponible", ParameterType.BOOLEAN),
    IN_LEGAL_HOURS("en_horario_legal", ParameterType.BOOLEAN),
    WEEKEND("fin_de_semana", ParameterType.BOOLEAN),
    MONTH("mes", ParameterType.NUMBER),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }
        fun fromKey(key: String): RuleParameter? = byKey[key]

        /** Marcadores que se pueden usar en los textos de estrategia: {viento_desde}. */
        val PLACEHOLDERS = setOf("viento_desde", "viento_hacia")
    }
}

/** Valores del contexto. Un parámetro ausente significa "sin datos". */
data class RuleContext(
    val numbers: Map<RuleParameter, Double> = emptyMap(),
    val booleans: Map<RuleParameter, Boolean> = emptyMap(),
    val texts: Map<RuleParameter, String> = emptyMap(),
    /** Sustituciones para los marcadores de los textos. */
    val placeholders: Map<String, String> = emptyMap(),
) {
    fun has(parameter: RuleParameter) = parameter in numbers || parameter in booleans || parameter in texts
}
