package com.nachojerez.carpstrategy.domain.model

/** Punto geográfico en grados decimales (WGS84). */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "Latitud fuera de rango: $latitude" }
        require(longitude in -180.0..180.0) { "Longitud fuera de rango: $longitude" }
    }
}

/** Ubicación con nombre visible para el usuario. */
data class FishingLocation(val name: String, val point: GeoPoint)

/**
 * Ubicación por defecto: embalse de Brovales (Jerez de los Caballeros, Badajoz), junto a la presa.
 * Coordenadas según docs/CONOCIMIENTO.md §8 (≈38,35 N, −6,70 O). Configurable en la app.
 */
object DefaultLocation {
    val value = FishingLocation(
        name = "Embalse de Brovales",
        point = GeoPoint(latitude = 38.35, longitude = -6.70),
    )
}
