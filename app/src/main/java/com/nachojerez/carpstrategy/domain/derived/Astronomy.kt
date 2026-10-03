package com.nachojerez.carpstrategy.domain.derived

import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Orto, ocaso y crepúsculo civil de un día. Null si no ocurre (latitudes polares). */
data class SunTimes(
    val date: LocalDate,
    val civilDawn: Instant?,
    val sunrise: Instant?,
    val sunset: Instant?,
    val civilDusk: Instant?,
)

/** Ventana legal de pesca de un día: de 1 h antes del orto a 1 h después del ocaso (§7). */
data class LegalWindow(val start: Instant, val end: Instant) {
    operator fun contains(time: Instant): Boolean = !time.isBefore(start) && !time.isAfter(end)

    companion object {
        val MARGIN: Duration = Duration.ofHours(1)

        fun of(sun: SunTimes): LegalWindow? {
            val sunrise = sun.sunrise ?: return null
            val sunset = sun.sunset ?: return null
            return LegalWindow(sunrise.minus(MARGIN), sunset.plus(MARGIN))
        }
    }
}

/**
 * Posición del Sol según el algoritmo del NOAA (Meeus simplificado), con precisión de ~1 min en
 * latitudes medias. Sin red ni librerías. Ángulos cenitales: 90,833° (orto/ocaso, incluye
 * refracción y semidiámetro) y 96° (crepúsculo civil).
 */
object SolarCalculator {
    private const val ZENITH_SUNRISE = 90.833
    private const val ZENITH_CIVIL = 96.0

    /** [date] es el día civil de la ubicación; los instantes se devuelven en UTC. */
    fun sunTimes(date: LocalDate, point: GeoPoint): SunTimes = SunTimes(
        date = date,
        civilDawn = event(date, point, ZENITH_CIVIL, rising = true),
        sunrise = event(date, point, ZENITH_SUNRISE, rising = true),
        sunset = event(date, point, ZENITH_SUNRISE, rising = false),
        civilDusk = event(date, point, ZENITH_CIVIL, rising = false),
    )

    private fun event(date: LocalDate, point: GeoPoint, zenith: Double, rising: Boolean): Instant? {
        // Primera aproximación a las 12:00 UTC y una iteración en el instante del evento.
        val midnightUtc = date.atStartOfDay().toInstant(ZoneOffset.UTC)
        var minutesUtc = 720.0
        repeat(2) {
            val jd = julianDay(midnightUtc) + minutesUtc / 1440.0
            minutesUtc = eventMinutesUtc(jd, point, zenith, rising) ?: return null
        }
        return midnightUtc.plusMillis((minutesUtc * 60_000).toLong())
    }

    /** Minutos desde las 00:00 UTC del día de [jd] en que ocurre el evento. */
    private fun eventMinutesUtc(jd: Double, point: GeoPoint, zenith: Double, rising: Boolean): Double? {
        val t = (jd - 2451545.0) / 36525.0
        val l0 = normalize(280.46646 + t * (36000.76983 + t * 0.0003032))
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mRad = Math.toRadians(m)
        val c = sin(mRad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * mRad) * (0.019993 - 0.000101 * t) + sin(3 * mRad) * 0.000289
        val trueLong = l0 + c
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        val lambda = Math.toRadians(trueLong - 0.00569 - 0.00478 * sin(omega))
        val eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
        val eps = Math.toRadians(eps0 + 0.00256 * cos(omega))
        val decl = asin(sin(eps) * sin(lambda))
        val y = tan(eps / 2) * tan(eps / 2)
        val l0Rad = Math.toRadians(l0)
        val eqTime = 4 * Math.toDegrees(
            y * sin(2 * l0Rad) - 2 * e * sin(mRad) + 4 * e * y * sin(mRad) * cos(2 * l0Rad) -
                0.5 * y * y * sin(4 * l0Rad) - 1.25 * e * e * sin(2 * mRad),
        )
        val lat = Math.toRadians(point.latitude)
        val cosH = (cos(Math.toRadians(zenith)) - sin(lat) * sin(decl)) / (cos(lat) * cos(decl))
        if (cosH < -1.0 || cosH > 1.0) return null
        val hourAngle = Math.toDegrees(acos(cosH))
        val solarNoon = 720.0 - 4 * point.longitude - eqTime
        return solarNoon + 4 * (if (rising) -hourAngle else hourAngle)
    }

    private fun julianDay(instant: Instant): Double = instant.epochSecond / 86400.0 + 2440587.5

    private fun normalize(deg: Double) = ((deg % 360.0) + 360.0) % 360.0
}

enum class MoonPhaseName { NEW, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, FULL, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT }

/** Fase lunar. Se registra con peso 0 (sin evidencia en carpa, CONOCIMIENTO.md §3 #11). */
data class MoonInfo(
    /** Días desde la luna nueva (0 a ~29,53). */
    val ageDays: Double,
    /** Fracción iluminada (0 = nueva, 1 = llena). */
    val illumination: Double,
    val phase: MoonPhaseName,
)

/**
 * Edad lunar a partir del mes sinódico medio y una luna nueva de referencia
 * (6/1/2000 18:14 UTC). Error típico < 1 día, suficiente para un dato informativo con peso 0.
 */
object MoonCalculator {
    const val SYNODIC_MONTH_DAYS = 29.530588853
    private val REFERENCE_NEW_MOON: Instant = Instant.parse("2000-01-06T18:14:00Z")

    fun moon(at: Instant): MoonInfo {
        val days = Duration.between(REFERENCE_NEW_MOON, at).toMillis() / 86_400_000.0
        val age = ((days % SYNODIC_MONTH_DAYS) + SYNODIC_MONTH_DAYS) % SYNODIC_MONTH_DAYS
        val angle = 2 * Math.PI * age / SYNODIC_MONTH_DAYS
        val illumination = (1 - cos(angle)) / 2
        val octant = floor(age / SYNODIC_MONTH_DAYS * 8 + 0.5).toInt() % 8
        return MoonInfo(age, illumination, MoonPhaseName.entries[octant])
    }
}
