package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.advice.WindRules
import com.nachojerez.carpstrategy.domain.derived.Geo
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Viento para pintar el mapa: el de las últimas 24 h (dirección dominante) y aire y agua. */
data class MapWind(
    val fromDeg: Double?,
    val kmh: Double?,
    val persistence: Double? = null,
    val airC: Double? = null,
    val waterC: Double? = null,
    val gustKmh: Double? = null,
)

/** Cómo le da el viento a un puesto del mapa (color del marcador). */
enum class SpotWindKind {
    /** Le favorece según §5.4 (desempate): viento cálido de cara con agua por debajo del óptimo, o frío de espaldas. */
    FAVOURED,
    /** Recibe el viento (de cara) sin que eso le favorezca. */
    RECEIVES,
    /** Resguardado (de espaldas). */
    SHELTERED,
    SIDE,
    /** Viento flojo, o falta la orientación del puesto o el viento. */
    NEUTRAL,
}

/** Orientación y fetch de un puesto calculados con dos puntos del mapa. */
data class SpotGeometry(
    /** Hacia dónde mira la orilla (rumbo al agua, 0–359°). */
    val facingDeg: Int,
    /** Agua abierta delante del puesto hasta la orilla de enfrente (m). */
    val fetchM: Double,
)

/**
 * Puestos en el mapa (fase 7.9). El usuario toca su puesto y la orilla de enfrente, hacia donde
 * lanza: el rumbo entre los dos es la orientación y la distancia es el fetch (§5.9.12). El tiempo
 * no cambia de un puesto a otro (los modelos tienen cuadrículas de 1–11 km): lo que cambia es cómo
 * le da el viento a cada uno. Funciones puras.
 */
object SpotMap {
    /** Menos de esto entre los dos toques no es una orilla de enfrente sino un error de toque. */
    const val MIN_FETCH_M = 20.0

    fun geometry(spot: GeoPoint, opposite: GeoPoint): SpotGeometry? {
        val meters = Geo.haversineKm(spot, opposite) * 1000
        if (meters < MIN_FETCH_M) return null
        return SpotGeometry(Geo.bearingDeg(spot, opposite).roundToInt() % 360, Math.round(meters).toDouble())
    }

    fun windKind(spot: Spot, wind: MapWind?): SpotWindKind {
        val relation = Spots.windRelation(spot.facingDeg, wind?.fromDeg, wind?.kmh) ?: return SpotWindKind.NEUTRAL
        if (WindRules.favours(relation, wind?.airC, wind?.waterC, wind?.kmh, wind?.persistence)) return SpotWindKind.FAVOURED
        return when (relation) {
            WindRelation.FACING -> SpotWindKind.RECEIVES
            WindRelation.BEHIND -> SpotWindKind.SHELTERED
            WindRelation.SIDE -> SpotWindKind.SIDE
            WindRelation.CALM -> SpotWindKind.NEUTRAL
        }
    }

    /** Punto a [meters] de [from] en el rumbo [bearingDeg] (para dibujar la línea de lance). */
    fun destination(from: GeoPoint, bearingDeg: Double, meters: Double): GeoPoint {
        val d = meters / 1000 / Geo.EARTH_RADIUS_KM
        val b = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(b))
        val lon2 = lon1 + atan2(sin(b) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return GeoPoint(Math.toDegrees(lat2), (Math.toDegrees(lon2) + 540) % 360 - 180)
    }
}
