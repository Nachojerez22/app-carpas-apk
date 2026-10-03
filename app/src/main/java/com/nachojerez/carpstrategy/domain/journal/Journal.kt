package com.nachojerez.carpstrategy.domain.journal

import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.domain.rules.FeedingDemand
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
)

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
) {
    val isOngoing: Boolean get() = end == null

    val duration: Duration? get() = end?.let { Duration.between(start, it) }

    /** Esfuerzo en horas-caña: el dato tecleado o cañas × duración. Null si está en curso. */
    val rodHours: Double?
        get() = rodHoursOverride ?: duration?.let { rods * it.toMinutes() / 60.0 }

    /** Bolo confirmado: sin capturas y marcado como tal. */
    val isBlank: Boolean get() = catches.isEmpty() && blank == true
}
