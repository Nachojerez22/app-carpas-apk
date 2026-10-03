package com.nachojerez.carpstrategy.domain.derived

import java.time.Duration
import java.time.Instant

enum class FreshnessLevel { FRESH, AGING, STALE }

data class Freshness(val age: Duration, val level: FreshnessLevel)

/** Antigüedad de un dato en caché. Se muestra siempre al usuario cuando no es reciente. */
object FreshnessPolicy {
    val FRESH_MAX: Duration = Duration.ofHours(3)
    val AGING_MAX: Duration = Duration.ofHours(24)

    fun evaluate(fetchedAt: Instant, now: Instant): Freshness {
        val age = Duration.between(fetchedAt, now).let { if (it.isNegative) Duration.ZERO else it }
        val level = when {
            age <= FRESH_MAX -> FreshnessLevel.FRESH
            age <= AGING_MAX -> FreshnessLevel.AGING
            else -> FreshnessLevel.STALE
        }
        return Freshness(age, level)
    }
}
