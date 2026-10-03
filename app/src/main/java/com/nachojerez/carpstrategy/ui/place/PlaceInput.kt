package com.nachojerez.carpstrategy.ui.place

import com.nachojerez.carpstrategy.domain.model.GeoPoint

/** Error al teclear coordenadas. */
enum class CoordinateError { LATITUDE, LONGITUDE }

/** Resultado de leer las coordenadas tecleadas: un punto válido o el primer campo erróneo. */
sealed interface CoordinateInput {
    data class Valid(val point: GeoPoint) : CoordinateInput
    data class Invalid(val error: CoordinateError) : CoordinateInput
}

/**
 * Lee latitud y longitud en grados decimales; admite coma o punto decimal y espacios.
 * Función pura.
 */
fun parseCoordinates(latitude: String, longitude: String): CoordinateInput {
    fun read(text: String) = text.trim().replace(',', '.').toDoubleOrNull()
    val lat = read(latitude)?.takeIf { it in -90.0..90.0 } ?: return CoordinateInput.Invalid(CoordinateError.LATITUDE)
    val lon = read(longitude)?.takeIf { it in -180.0..180.0 } ?: return CoordinateInput.Invalid(CoordinateError.LONGITUDE)
    return CoordinateInput.Valid(GeoPoint(lat, lon))
}
