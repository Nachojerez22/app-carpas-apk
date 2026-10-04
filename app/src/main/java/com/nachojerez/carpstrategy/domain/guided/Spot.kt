package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.journal.FishingZone
import kotlin.math.abs

/**
 * Estructura del fondo de un puesto según lo que ha visto o medido el usuario. La batimetría de
 * Brovales no está verificada (§6.3): la app no la supone, solo usa lo que tú anotas.
 */
enum class SpotStructure { SHALLOW_EDGE, FIRST_DROP, OLD_CHANNEL, INLET_BAY, POINT, FLAT, OTHER }

/**
 * Puesto definido por el usuario («Mis puestos»). Profundidad y distancia son las que mediste tú
 * (sonda, plomo marcador o vista); [facingDeg] es hacia dónde mira la orilla (rumbo al agua, 0 =
 * norte, 90 = este): con él se sabe si el viento te da de cara, de espaldas o de lado.
 */
data class Spot(
    val id: Long,
    val name: String,
    val zone: FishingZone? = null,
    val structure: SpotStructure = SpotStructure.OTHER,
    val depthM: Double? = null,
    val distanceM: Double? = null,
    val facingDeg: Int? = null,
    val notes: String = "",
)

/** Viento respecto al puesto. */
enum class WindRelation {
    /** El viento viene del agua hacia ti: tu orilla lo recibe (sotavento, §5.4 🟡). */
    FACING,
    /** Viene de detrás: orilla resguardada, el agua de superficie se aleja. */
    BEHIND,
    SIDE,
    /** Demasiado flojo para importar. */
    CALM,
}

object Spots {
    /** 🟣 Por debajo de esta velocidad el viento no se cuenta (sin datos para fijarlo). */
    const val CALM_WIND_KMH = 6.0

    /** 🟣 Hasta este ángulo entre el viento y la orientación de la orilla es «de cara» o «de espaldas». */
    const val FACING_TOLERANCE_DEG = 45.0

    val FACINGS = listOf(0, 45, 90, 135, 180, 225, 270, 315)

    /**
     * Relación entre el viento (dirección meteorológica: DE DÓNDE viene) y la orilla, que mira
     * hacia [facingDeg]. Si el viento viene de la dirección a la que mira la orilla, sopla desde
     * el agua hacia ti: de cara. Null si falta algún dato.
     */
    fun windRelation(facingDeg: Int?, windFromDeg: Double?, windKmh: Double?): WindRelation? {
        if (facingDeg == null || windFromDeg == null || windKmh == null) return null
        if (windKmh < CALM_WIND_KMH) return WindRelation.CALM
        val diff = angleBetween(facingDeg.toDouble(), windFromDeg)
        return when {
            diff <= FACING_TOLERANCE_DEG -> WindRelation.FACING
            diff >= 180 - FACING_TOLERANCE_DEG -> WindRelation.BEHIND
            else -> WindRelation.SIDE
        }
    }

    /** Ángulo menor entre dos rumbos (0–180°). */
    fun angleBetween(a: Double, b: Double): Double {
        val d = abs(((a - b) % 360 + 360) % 360)
        return if (d > 180) 360 - d else d
    }

    /** Siguiente id libre. */
    fun nextId(spots: List<Spot>): Long = (spots.maxOfOrNull { it.id } ?: 0) + 1
}
