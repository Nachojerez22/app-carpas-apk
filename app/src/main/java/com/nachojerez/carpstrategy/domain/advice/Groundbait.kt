package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel

/** Cuánto cebar, en kilos, a partir de tu medida (fase 7.13). */
data class GroundbaitAmount(
    val level: GroundbaitLevel,
    val perRodKg: Double,
    val totalKg: Double,
    /** Al empezar, por caña. */
    val initialPerRodKg: Double,
    /** Lo que queda por caña para los recebados (tras captura o señales). */
    val rebaitPerRodKg: Double,
)

/**
 * Cantidades de cebado (CONOCIMIENTO.md §5.5 y §5.8). No hay cifras fijas: dependen de la
 * densidad de carpas del embalse (🔴), así que la base es **tu medida** de un cebado «normal» por
 * caña y sesión; los factores son proporciones 🟣 a calibrar con el diario. Con poco en invierno y
 * mucho en verano, el invierno queda en ≈ 1/8 del verano (§5.5: 1/5–1/10). Funciones puras.
 */
object GroundbaitAmounts {
    /** 🟣 «Poco» respecto a tu normal. */
    const val LOW_FACTOR = 0.25

    /** 🟣 «Mucho» respecto a tu normal. */
    const val HIGH_FACTOR = 2.0

    /** 🟣 Parte que va al empezar; el resto, en recebados tras captura o señales (§5.8). */
    const val INITIAL_SHARE = 0.5

    /** Límites de tu medida (kg por caña y sesión). */
    const val MIN_NORMAL_KG = 0.05
    const val MAX_NORMAL_KG = 20.0

    /** Lo máximo que acepta la app de la IA por caña: un 50 % por encima de «mucho». */
    fun maxPerRodKg(normalKg: Double): Double = normalKg * HIGH_FACTOR * 1.5

    fun factor(level: GroundbaitLevel): Double = when (level) {
        GroundbaitLevel.LOW -> LOW_FACTOR
        GroundbaitLevel.NORMAL -> 1.0
        GroundbaitLevel.HIGH -> HIGH_FACTOR
    }

    /** Null sin tu medida (o fuera de límites): entonces solo se dice poco, normal o mucho. */
    fun amount(level: GroundbaitLevel, normalKg: Double?, rods: Int): GroundbaitAmount? {
        val normal = normalKg?.takeIf { it in MIN_NORMAL_KG..MAX_NORMAL_KG } ?: return null
        val perRod = normal * factor(level)
        val initial = perRod * INITIAL_SHARE
        return GroundbaitAmount(level, perRod, perRod * rods.coerceAtLeast(1), initial, perRod - initial)
    }

    /** Lee lo que teclea el usuario («1», «1,5», «0.75»); null si no vale. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in MIN_NORMAL_KG..MAX_NORMAL_KG }
}
