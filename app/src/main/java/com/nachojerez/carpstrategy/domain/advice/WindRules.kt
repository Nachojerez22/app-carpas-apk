package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.WindRelation

/** Viento respecto al agua: cálido (aire más caliente que el agua), frío o sin diferencia clara. */
enum class WindTemperature { WARM, COLD, NEUTRAL }

/**
 * Avisos prácticos del viento (CONOCIMIENTO.md §5.9.12): ayudan a pescar bien donde estés, **no
 * puntúan** al pez.
 */
enum class WindTip {
    /** De cara fuerte: lance más corto, plomo pesado, cebo en bolas o PVA sólido. */
    SHORT_CAST,
    /** Lateral: comba; hilo hundido, puntas bajas, bobbins pesados, líneas separadas. */
    SLACK_LINE,
    /** Oleaje: pitidos falsos; más peso en el indicador antes que menos sensibilidad. */
    FALSE_BEEPS,
    /** De espaldas: buen momento para cebar lejos; el cebo flotante deriva hacia fuera. */
    BAIT_FAR,
    /** Rachas fuertes: anclar bivvy y paraguas; cuidado en la sacada. */
    ANCHOR,
}

/**
 * Viento como modificador de segundo orden (desempate) según CONOCIMIENTO.md §5.4: lo que
 * cuenta es si el viento está más caliente o más frío que el agua y si el agua está por debajo
 * del óptimo, no la dirección por sí sola. Umbrales 🟣 a calibrar con tus datos. Funciones puras.
 */
object WindRules {
    /** 🟣 Diferencia aire − agua a partir de la que el viento cuenta como cálido o frío. */
    const val TEMP_DELTA_C = 2.0

    /** 🟣 Velocidad mínima para que el viento cálido «empuje» hacia la orilla que lo recibe. */
    const val WARM_MIN_KMH = 10.0

    /** 🟢 Por encima de esta temperatura el agua ya no está por debajo del óptimo (§5.1: máxima demanda 22–28 °C). */
    const val OPTIMUM_FROM_C = 22.0

    /** 🟣 Persistencia mínima (0–1) en 24 h para hablar de viento sostenido (≈ 12–24 h de la misma dirección). */
    const val SUSTAINED_PERSISTENCE = 0.7

    /** 🟣 Umbrales de los avisos prácticos (§5.9.12). */
    const val STRONG_FACING_KMH = 15.0
    const val SIDE_KMH = 10.0
    const val WAVES_KMH = 15.0
    const val GUST_ANCHOR_KMH = 40.0

    fun temperature(airC: Double?, waterC: Double?): WindTemperature? {
        if (airC == null || waterC == null) return null
        val delta = airC - waterC
        return when {
            delta >= TEMP_DELTA_C -> WindTemperature.WARM
            delta <= -TEMP_DELTA_C -> WindTemperature.COLD
            else -> WindTemperature.NEUTRAL
        }
    }

    /**
     * ¿Favorece el viento a este puesto? (solo desempate).
     * - Cálido, sostenido, ≥ [WARM_MIN_KMH] y agua por debajo del óptimo → la orilla que lo recibe (🟡).
     * - Frío → la orilla resguardada, con el viento a la espalda (🟡).
     * - Agua ya en el óptimo o por encima (verano) → la dirección no suma (peso 0, 🟣).
     * Sin temperatura del aire o del agua no se puede saber si es cálido o frío: no suma.
     * [persistence] null = desconocida (no se exige).
     */
    fun favours(relation: WindRelation?, airC: Double?, waterC: Double?, windKmh: Double?, persistence: Double? = null): Boolean {
        val temp = temperature(airC, waterC) ?: return false
        return when (relation) {
            WindRelation.FACING -> temp == WindTemperature.WARM &&
                (windKmh ?: 0.0) >= WARM_MIN_KMH &&
                (waterC ?: OPTIMUM_FROM_C) < OPTIMUM_FROM_C &&
                (persistence == null || persistence >= SUSTAINED_PERSISTENCE)
            WindRelation.BEHIND -> temp == WindTemperature.COLD
            else -> false
        }
    }

    /** Avisos prácticos para el puesto con este viento (no cambian dónde está el pez). */
    fun tips(relation: WindRelation?, windKmh: Double?, gustKmh: Double?): List<WindTip> = buildList {
        val speed = windKmh ?: 0.0
        if (relation == WindRelation.FACING && speed >= STRONG_FACING_KMH) add(WindTip.SHORT_CAST)
        if (relation == WindRelation.SIDE && speed >= SIDE_KMH) add(WindTip.SLACK_LINE)
        if (relation == WindRelation.FACING && speed >= WAVES_KMH) add(WindTip.FALSE_BEEPS)
        if (relation == WindRelation.BEHIND) add(WindTip.BAIT_FAR)
        if ((gustKmh ?: 0.0) >= GUST_ANCHOR_KMH) add(WindTip.ANCHOR)
    }
}
