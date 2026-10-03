package com.nachojerez.carpstrategy.domain.model

import java.util.Locale

/** Estilo visual elegido en Lugar → Apariencia. Material es el predeterminado. */
enum class AppStyle { MATERIAL, APPLE }

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

data class AppearanceSettings(
    val style: AppStyle = AppStyle.MATERIAL,
    val mode: AppThemeMode = AppThemeMode.SYSTEM,
    /** Solo aplica al estilo Material en Android 12+. */
    val dynamicColor: Boolean = false,
) {
    fun encode(): String = "${style.name};${mode.name};$dynamicColor"

    companion object {
        /** Tolerante: cualquier valor corrupto vuelve al predeterminado. */
        fun decode(text: String?): AppearanceSettings {
            val parts = text?.split(';') ?: return AppearanceSettings()
            return AppearanceSettings(
                style = AppStyle.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: AppStyle.MATERIAL,
                mode = AppThemeMode.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: AppThemeMode.SYSTEM,
                dynamicColor = parts.getOrNull(2) == "true",
            )
        }
    }
}

/** Codificación de la ubicación guardada ("lat;lon;nombre"). Solo se guarda en el móvil. */
object LocationCodec {
    fun encode(location: FishingLocation): String =
        String.format(Locale.ROOT, "%.6f;%.6f;%s", location.point.latitude, location.point.longitude, location.name.replace(";", ","))

    fun decode(text: String?): FishingLocation {
        val parts = text?.split(';', limit = 3) ?: return DefaultLocation.value
        val lat = parts.getOrNull(0)?.toDoubleOrNull()
        val lon = parts.getOrNull(1)?.toDoubleOrNull()
        val name = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) return DefaultLocation.value
        return FishingLocation(name ?: DefaultLocation.value.name, GeoPoint(lat, lon))
    }
}
