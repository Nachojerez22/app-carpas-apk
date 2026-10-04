package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * El tiempo en un aviso de la sesión guiada: la PREVISIÓN de la hora en curso (serie combinada
 * según tu prioridad de fuentes) y la temperatura del agua (medida o estimada). Se guarda con
 * cada aviso para aprender después (§9). Lo que diga el usuario en el puesto manda: por eso se
 * pregunta «¿Llueve?» y se guarda la respuesta.
 */
data class WeatherSnapshot(
    val time: Instant,
    val airC: Double? = null,
    val windKmh: Double? = null,
    /** De dónde viene el viento (0 = norte). */
    val windFromDeg: Double? = null,
    val gustKmh: Double? = null,
    /** 🔴 peso 0. */
    val cloudPct: Double? = null,
    val precipitationMm: Double? = null,
    /** Código WMO de Open-Meteo (95–99 = tormenta). */
    val weatherCode: Int? = null,
    /** 🔴 peso 0. */
    val pressureHpa: Double? = null,
    val waterC: Double? = null,
    /** El agua viene de tu medida (si no, es una estimación desde el aire). */
    val waterMeasured: Boolean = false,
    val sunset: Instant? = null,
    val legalEnd: Instant? = null,
    /** Respuesta del usuario a «¿Llueve?» en este aviso (null = sin contestar). */
    val rainAnswer: Boolean? = null,
    /** Respuesta a «¿Hay tormenta?» (null = sin contestar). */
    val stormAnswer: Boolean? = null,
) {
    val forecastStorm: Boolean get() = weatherCode != null && weatherCode in FieldWeather.STORM_CODES

    val forecastRain: Boolean
        get() = forecastStorm ||
            (precipitationMm ?: 0.0) >= FieldWeather.RAIN_MM_PER_HOUR ||
            (weatherCode != null && weatherCode in FieldWeather.RAIN_CODES)
}

/** Qué ha cambiado entre dos avisos. Los textos y las etiquetas salen de [kind]. */
enum class WeatherChangeKind(val evidence: Evidence, val weightZero: Boolean = false) {
    AIR_UP(Evidence.YELLOW),
    AIR_DOWN(Evidence.YELLOW),
    WIND_UP(Evidence.YELLOW),
    WIND_DOWN(Evidence.YELLOW),
    WIND_SHIFT(Evidence.YELLOW),
    GUSTS(Evidence.PURPLE),
    CLOUDING(Evidence.RED, weightZero = true),
    CLEARING(Evidence.RED, weightZero = true),
    PRESSURE_UP(Evidence.RED, weightZero = true),
    PRESSURE_DOWN(Evidence.RED, weightZero = true),
    RAIN_START(Evidence.YELLOW),
    RAIN_STOP(Evidence.YELLOW),
    STORM(Evidence.GREEN),
    WATER_UP(Evidence.YELLOW),
    WATER_DOWN(Evidence.YELLOW),
}

/** Un cambio con los valores de antes y después (si los hay) para mostrarlos. */
data class WeatherChange(val kind: WeatherChangeKind, val from: Double? = null, val to: Double? = null)

/** Pregunta que la app hace cuando la previsión y lo anotado no cuadran. */
enum class WeatherQuestion { RAIN, STORM }

/** Lo que se muestra en un aviso: el tiempo ahora, los cambios y la luz que queda. */
data class WeatherReport(
    val current: WeatherSnapshot,
    val sinceStart: List<WeatherChange>,
    val sinceLast: List<WeatherChange>,
    val wind: WindRelation?,
    val toSunset: Duration?,
    val toLegalEnd: Duration?,
    val question: WeatherQuestion?,
)

/**
 * Cambios del tiempo durante la sesión guiada. Función pura. Los umbrales son 🟣 (criterio
 * propio, sin estudio): sirven para no avisar de variaciones que el modelo no distingue del ruido.
 */
object FieldWeather {
    /** 🟣 Cambio del aire que se avisa (°C). */
    const val AIR_DELTA_C = 1.5

    /** 🟣 Cambio de la velocidad media del viento (km/h). */
    const val WIND_DELTA_KMH = 8.0

    /** 🟣 Giro del viento que se avisa (°), solo si sopla al menos [Spots.CALM_WIND_KMH]. */
    const val WIND_SHIFT_DEG = 60.0

    /** 🟣 Rachas que se avisan (km/h). */
    const val GUSTS_KMH = 40.0

    /** 🔴 peso 0: cambio de nubosidad que se muestra (puntos %). */
    const val CLOUD_DELTA_PCT = 40.0

    /** 🔴 peso 0: cambio de presión que se muestra (hPa). */
    const val PRESSURE_DELTA_HPA = 2.0

    /** 🟣 Precipitación horaria a partir de la que se considera que llueve (mm/h). */
    const val RAIN_MM_PER_HOUR = 0.2

    /** 🟣 Cambio del agua que se avisa (°C). La estimada cambia muy despacio. */
    const val WATER_DELTA_C = 0.3

    /** Códigos WMO de tormenta (Open-Meteo). */
    val STORM_CODES = 95..99

    /** Códigos WMO de llovizna, lluvia y chubascos. */
    val RAIN_CODES = (51..67) + (80..82)

    fun changes(from: WeatherSnapshot, to: WeatherSnapshot): List<WeatherChange> = buildList {
        delta(from.airC, to.airC, AIR_DELTA_C, WeatherChangeKind.AIR_UP, WeatherChangeKind.AIR_DOWN)?.let(::add)
        delta(from.windKmh, to.windKmh, WIND_DELTA_KMH, WeatherChangeKind.WIND_UP, WeatherChangeKind.WIND_DOWN)?.let(::add)
        if (from.windFromDeg != null && to.windFromDeg != null &&
            (from.windKmh ?: 0.0) >= Spots.CALM_WIND_KMH && (to.windKmh ?: 0.0) >= Spots.CALM_WIND_KMH &&
            Spots.angleBetween(from.windFromDeg, to.windFromDeg) >= WIND_SHIFT_DEG
        ) {
            add(WeatherChange(WeatherChangeKind.WIND_SHIFT, from.windFromDeg, to.windFromDeg))
        }
        if ((to.gustKmh ?: 0.0) >= GUSTS_KMH && (from.gustKmh ?: 0.0) < GUSTS_KMH) {
            add(WeatherChange(WeatherChangeKind.GUSTS, from.gustKmh, to.gustKmh))
        }
        delta(from.cloudPct, to.cloudPct, CLOUD_DELTA_PCT, WeatherChangeKind.CLOUDING, WeatherChangeKind.CLEARING)?.let(::add)
        delta(from.pressureHpa, to.pressureHpa, PRESSURE_DELTA_HPA, WeatherChangeKind.PRESSURE_UP, WeatherChangeKind.PRESSURE_DOWN)?.let(::add)
        if (to.forecastStorm && !from.forecastStorm) add(WeatherChange(WeatherChangeKind.STORM))
        if (to.forecastRain != from.forecastRain) {
            add(WeatherChange(if (to.forecastRain) WeatherChangeKind.RAIN_START else WeatherChangeKind.RAIN_STOP, from.precipitationMm, to.precipitationMm))
        }
        delta(from.waterC, to.waterC, WATER_DELTA_C, WeatherChangeKind.WATER_UP, WeatherChangeKind.WATER_DOWN)?.let(::add)
    }

    /**
     * Informe del último aviso. [userRain] y [userStorm] son las condiciones que el usuario tiene
     * activas: si la previsión no cuadra con ellas y aún no ha contestado, se pregunta.
     */
    fun report(record: GuidedRecord, now: Instant, facingDeg: Int? = record.spot?.facingDeg): WeatherReport? {
        val snapshots = record.weather
        val current = snapshots.lastOrNull() ?: return null
        val first = snapshots.first()
        val previous = snapshots.getOrNull(snapshots.size - 2)
        val active = record.activeConditions(now)
        val userRain = FieldCondition.LIGHT_RAIN in active || FieldCondition.HEAVY_RAIN in active
        val userStorm = FieldCondition.STORM in active
        val question = when {
            current.forecastStorm && !userStorm && current.stormAnswer == null -> WeatherQuestion.STORM
            current.forecastRain != userRain && current.rainAnswer == null -> WeatherQuestion.RAIN
            else -> null
        }
        return WeatherReport(
            current = current,
            sinceStart = if (first === current) emptyList() else changes(first, current),
            sinceLast = previous?.let { changes(it, current) }.orEmpty(),
            wind = Spots.windRelation(facingDeg, current.windFromDeg, current.windKmh),
            toSunset = current.sunset?.let { Duration.between(now, it) },
            toLegalEnd = current.legalEnd?.let { Duration.between(now, it) },
            question = question,
        )
    }

    /** ¿Hay que anotar un cambio de viento? Giro o subida clara frente al aviso anterior (previsión, 🟡). */
    fun windChangedSince(previous: WeatherSnapshot?, current: WeatherSnapshot): Boolean {
        previous ?: return false
        return changes(previous, current).any { it.kind == WeatherChangeKind.WIND_SHIFT || it.kind == WeatherChangeKind.WIND_UP }
    }

    private fun delta(from: Double?, to: Double?, threshold: Double, up: WeatherChangeKind, down: WeatherChangeKind): WeatherChange? {
        if (from == null || to == null) return null
        val d = to - from
        if (abs(d) < threshold) return null
        return WeatherChange(if (d > 0) up else down, from, to)
    }
}
