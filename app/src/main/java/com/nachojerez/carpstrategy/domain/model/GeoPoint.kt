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

/** Ubicación por defecto: Brovales / Jerez de los Caballeros (Badajoz). Configurable en la app. */
object DefaultLocation {
    val value = FishingLocation(
        name = "Brovales / Jerez de los Caballeros",
        point = GeoPoint(latitude = 38.37, longitude = -6.88),
    )
}
