package com.nachojerez.carpstrategy.domain.journal

import com.nachojerez.carpstrategy.domain.guided.GuidedLog
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.domain.rules.FeedingDemand
import com.nachojerez.carpstrategy.domain.rules.RuleContext
import com.nachojerez.carpstrategy.domain.rules.RuleLevel
import java.time.Duration
import java.time.Instant

/**
 * Puesto codificado por zonas (CONOCIMIENTO.md §9: "zona codificada, no necesariamente GPS
 * exacto"). El detalle libre (p. ej. "carrizal norte") va en [Session.zoneDetail].
 */
enum class FishingZone { NORTH, EAST, SOUTH, WEST }

/** Una captura. Todo opcional salvo que exista: el peso y la hora se anotan si se conocen. */
data class Catch(
    val time: Instant? = null,
    val weightKg: Double? = null,
    val rod: Int? = null,
    val species: String? = null,
)

/** "¿Se cumplió?" lo que dijo la app antes de la sesión. */
enum class Fulfilled { YES, PARTLY, NO }

/**
 * Valoración de la app calculada ANTES de la sesión (§9). Se copia en la sesión y no cambia
 * aunque cambien los datos: sirve para medir la capacidad predictiva sin sesgo retrospectivo.
 */
data class PredictionSnapshot(
    val computedAt: Instant,
    val location: GeoPoint,
    val blocked: Boolean,
    val favorability: Double?,
    val band: FavorabilityBand?,
    val limitingLevel: RuleLevel?,
    val demand: FeedingDemand?,
    /** Parámetros que vieron las reglas (para el aprendizaje con datos propios, §10). */
    val features: FeatureSnapshot? = null,
    /** Valor de cada nivel de la cadena (0–1). */
    val levels: Map<RuleLevel, Double> = emptyMap(),
    /** Reglas activadas: id → factor efectivo aplicado. */
    val activeRules: Map<String, Double> = emptyMap(),
    /** Huella de rules.json con la que se calculó. */
    val rulesFingerprint: String? = null,
)

/**
 * Foto fija de los parámetros del motor de reglas, con las claves de rules.json
 * (`temp_agua_c`, `estacion`…). Sirve para comprobar después, con tus sesiones, qué variables
 * predicen de verdad (§10: no añadir una variable hasta que mejore fuera de muestra).
 */
data class FeatureSnapshot(
    val numbers: Map<String, Double> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val texts: Map<String, String> = emptyMap(),
) {
    companion object {
        fun of(context: RuleContext) = FeatureSnapshot(
            numbers = context.numbers.mapKeys { it.key.key },
            booleans = context.booleans.mapKeys { it.key.key },
            texts = context.texts.mapKeys { it.key.key },
        )
    }
}

/**
 * Contexto rellenado automáticamente al guardar (§9): agua, aire, viento, lluvia, nivel,
 * horario legal y los datos con peso 0 (luna y presión), que solo se registran.
 */
data class SessionContext(
    val legalStart: Instant? = null,
    val legalEnd: Instant? = null,
    val waterTempC: Double? = null,
    val waterMeasured: Boolean = false,
    val airTemp24hC: Double? = null,
    val windKmh: Double? = null,
    val windDirectionDeg: Double? = null,
    val rain72hMm: Double? = null,
    val reservoirPercent: Double? = null,
    /** 🔴 peso 0. */
    val moonIllumination: Double? = null,
    /** 🔴 peso 0. */
    val pressureHpa: Double? = null,
    /** Todos los parámetros de las reglas calculados para el inicio de la sesión. */
    val features: FeatureSnapshot? = null,
)

/**
 * Sesión de pesca. [end] null = sesión en curso. Un bolo es un dato: se guarda siempre con su
 * esfuerzo (horas-caña). [blank] es la respuesta explícita del usuario ("¿Bolo?").
 */
data class Session(
    val id: Long = 0,
    val start: Instant,
    val end: Instant? = null,
    val location: GeoPoint,
    val zone: FishingZone? = null,
    val zoneDetail: String = "",
    val depthM: Double? = null,
    val rods: Int = 2,
    /** Horas-caña tecleadas; si es null se calculan como cañas × duración. */
    val rodHoursOverride: Double? = null,
    val bait: String = "",
    val rig: String = "",
    val groundbaitKg: Double? = null,
    /** Otros pescadores cerca (presión de pesca). */
    val otherAnglers: Int? = null,
    val bites: Int = 0,
    val losses: Int = 0,
    val catches: List<Catch> = emptyList(),
    val blank: Boolean? = null,
    val prediction: PredictionSnapshot? = null,
    val context: SessionContext? = null,
    val fulfilled: Fulfilled? = null,
    val notes: String = "",
    val createdAt: Instant,
    /** Registro de la sesión guiada (tramos, avisos y decisiones); null si se anotó sin guiar. */
    val guided: GuidedLog? = null,
) {
    val isOngoing: Boolean get() = end == null

    val duration: Duration? get() = end?.let { Duration.between(start, it) }

    /** Esfuerzo en horas-caña: el dato tecleado o cañas × duración. Null si está en curso. */
    val rodHours: Double?
        get() = rodHoursOverride ?: duration?.let { rods * it.toMinutes() / 60.0 }

    /** Bolo confirmado: sin capturas y marcado como tal. */
    val isBlank: Boolean get() = catches.isEmpty() && blank == true
}
