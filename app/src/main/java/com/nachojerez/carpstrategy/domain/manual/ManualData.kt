package com.nachojerez.carpstrategy.domain.manual

import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Instant
import java.time.LocalDate

/** Variables horarias que se combinan entre fuentes según la prioridad elegida. */
enum class WeatherVariable {
    AIR_TEMPERATURE,
    PRESSURE_MSL,
    WIND_SPEED,
    WIND_DIRECTION,
    WIND_GUSTS,
    CLOUD_COVER,
    PRECIPITATION,
    RELATIVE_HUMIDITY,
    SHORTWAVE_RADIATION,
    WATER_TEMP_SURFACE,
    WATER_TEMP_BOTTOM,
    BOTTOM_DEPTH,
    TURBIDITY,
}

/** Si un campo se mide en un instante (`hora`), describe un día (`fecha`) o admite ambos. */
enum class FieldScope { HOURLY, DAILY, ANY }

/**
 * Campos que se pueden introducir a mano o importar. [key] es el nombre exacto en el JSON
 * (docs/FORMATO_DATOS.md). Los rangos rechazan errores de tecleo, no valores raros reales.
 */
enum class ManualField(
    val key: String,
    val min: Double,
    val max: Double,
    val scope: FieldScope,
    val variable: WeatherVariable? = null,
    val integer: Boolean = false,
) {
    AIR_TEMPERATURE("temp_aire_c", -30.0, 55.0, FieldScope.HOURLY, WeatherVariable.AIR_TEMPERATURE),
    PRESSURE_MSL("presion_hpa", 900.0, 1100.0, FieldScope.HOURLY, WeatherVariable.PRESSURE_MSL),
    WIND_SPEED("viento_kmh", 0.0, 250.0, FieldScope.HOURLY, WeatherVariable.WIND_SPEED),
    WIND_DIRECTION("viento_dir_grados", 0.0, 360.0, FieldScope.HOURLY, WeatherVariable.WIND_DIRECTION),
    WIND_GUSTS("racha_kmh", 0.0, 300.0, FieldScope.HOURLY, WeatherVariable.WIND_GUSTS),
    CLOUD_COVER("nubosidad_pct", 0.0, 100.0, FieldScope.HOURLY, WeatherVariable.CLOUD_COVER),
    PRECIPITATION("lluvia_mm", 0.0, 500.0, FieldScope.HOURLY, WeatherVariable.PRECIPITATION),
    RELATIVE_HUMIDITY("humedad_pct", 0.0, 100.0, FieldScope.HOURLY, WeatherVariable.RELATIVE_HUMIDITY),
    WATER_TEMP_SURFACE("temp_agua_superficie_c", -1.0, 40.0, FieldScope.HOURLY, WeatherVariable.WATER_TEMP_SURFACE),
    WATER_TEMP_BOTTOM("temp_agua_fondo_c", -1.0, 40.0, FieldScope.HOURLY, WeatherVariable.WATER_TEMP_BOTTOM),
    BOTTOM_DEPTH("profundidad_fondo_m", 0.0, 100.0, FieldScope.HOURLY, WeatherVariable.BOTTOM_DEPTH),
    TURBIDITY("turbidez", 1.0, 5.0, FieldScope.HOURLY, WeatherVariable.TURBIDITY, integer = true),
    DAILY_PRECIPITATION("lluvia_dia_mm", 0.0, 1000.0, FieldScope.DAILY),
    RESERVOIR_VOLUME("nivel_embalse_hm3", 0.0, 100.0, FieldScope.ANY),
    RESERVOIR_PERCENT("nivel_embalse_pct", 0.0, 110.0, FieldScope.ANY),
    RESERVOIR_ELEVATION("cota_embalse_m", 0.0, 2000.0, FieldScope.ANY),
    ;

    fun allowedFor(daily: Boolean): Boolean = when (scope) {
        FieldScope.ANY -> true
        FieldScope.HOURLY -> !daily
        FieldScope.DAILY -> daily
    }

    companion object {
        private val byKey = entries.associateBy { it.key }
        fun fromKey(key: String): ManualField? = byKey[key]
        fun forVariable(variable: WeatherVariable): ManualField? = entries.firstOrNull { it.variable == variable }
    }
}

/** Cómo llegó el dato: tecleado en la app o importado de un archivo (no verificado). */
sealed interface ManualOrigin {
    data object Typed : ManualOrigin
    data class Imported(val fileName: String) : ManualOrigin
}

/** Momento de un registro: una hora concreta o un día completo. */
sealed interface RecordPeriod {
    data class At(val time: Instant) : RecordPeriod
    data class Day(val date: LocalDate) : RecordPeriod
}

/** Registro introducido por el usuario. [source] (la "fuente") es obligatoria. */
data class ManualRecord(
    val id: Long = 0,
    val period: RecordPeriod,
    val location: GeoPoint,
    val source: String,
    val origin: ManualOrigin,
    val values: Map<ManualField, Double>,
    val notes: String? = null,
    val createdAt: Instant,
)
