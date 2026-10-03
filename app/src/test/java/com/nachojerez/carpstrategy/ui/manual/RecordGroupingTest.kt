package com.nachojerez.carpstrategy.ui.manual

import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecordGroupingTest {
    private val point = GeoPoint(38.35, -6.70)

    private fun record(id: Long, period: RecordPeriod) = ManualRecord(
        id = id, period = period, location = point, source = "s", origin = ManualOrigin.Typed,
        values = mapOf(ManualField.RESERVOIR_PERCENT to 50.0), createdAt = Instant.EPOCH,
    )

    // 2 oct 23:30 UTC = 3 oct 01:30 en Madrid.
    private val lateNight = record(1, RecordPeriod.At(Instant.parse("2026-10-02T23:30:00Z")))
    private val morning = record(2, RecordPeriod.At(Instant.parse("2026-10-03T06:00:00Z")))
    private val day3 = record(3, RecordPeriod.Day(LocalDate.parse("2026-10-03")))
    private val day1 = record(4, RecordPeriod.Day(LocalDate.parse("2026-10-01")))
    private val all = listOf(lateNight, morning, day3, day1)

    @Test
    fun `agrupa por dia local, del mas reciente al mas antiguo`() {
        val groups = groupRecordsByDay(all, RecordFilter.ALL, Formatting.MADRID)
        assertEquals(listOf(LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-01")), groups.map { it.first })
        // Primero el diario y después los horarios del más reciente al más antiguo.
        assertEquals(listOf(3L, 2L, 1L), groups[0].second.map { it.id })
    }

    @Test
    fun `filtra por hora o por dia`() {
        assertEquals(listOf(2L, 1L), groupRecordsByDay(all, RecordFilter.HOURLY).flatMap { it.second }.map { it.id })
        assertEquals(listOf(3L, 4L), groupRecordsByDay(all, RecordFilter.DAILY).flatMap { it.second }.map { it.id })
    }

    @Test
    fun `los 8 rumbos del selector coinciden con la brujula`() {
        assertEquals(listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO"), COMPASS_POINTS_DEG.map(Formatting::compass))
    }
}
