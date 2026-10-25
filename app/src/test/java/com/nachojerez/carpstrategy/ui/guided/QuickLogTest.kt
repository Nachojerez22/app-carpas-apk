package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecordValidator
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuickLogTest {
    @Test
    fun `con varias canas, Carpa en cada una y como mucho 3 botones`() {
        assertEquals(
            listOf(NotificationAction(QuickAnswer.NOTHING_ALL), NotificationAction(QuickAnswer.CATCH, 1), NotificationAction(QuickAnswer.CATCH, 2)),
            notificationActions(listOf(1, 2), pendingProposal = false, checkIn = true),
        )
        assertEquals(
            listOf(NotificationAction(QuickAnswer.CATCH, 1), NotificationAction(QuickAnswer.CATCH, 2), NotificationAction(QuickAnswer.CATCH, 3)),
            notificationActions(listOf(1, 2, 3), pendingProposal = false, checkIn = false),
        )
        assertEquals(3, notificationActions(listOf(1, 2, 3), pendingProposal = false, checkIn = true).size)
        // Una caña: como antes.
        assertEquals(
            listOf(QuickAnswer.NOTHING, QuickAnswer.MISSED, QuickAnswer.CATCH),
            notificationActions(listOf(1), pendingProposal = false, checkIn = true).map { it.answer },
        )
        assertEquals(QuickAnswer.ACCEPT, notificationActions(listOf(1), pendingProposal = true, checkIn = false).first().answer)
    }

    @Test
    fun `agua medida al empezar - lectura y registro valido para Datos`() {
        assertEquals(WaterInput.Empty, parseWaterInput("  "))
        assertEquals(WaterInput.Valid(18.5), parseWaterInput("18,5"))
        assertEquals(WaterInput.Valid(18.5), parseWaterInput("18.5"))
        assertEquals(WaterInput.Invalid, parseWaterInput("dieciocho"))
        assertEquals(WaterInput.Invalid, parseWaterInput("55"))

        val now = Instant.parse("2026-10-26T07:40:00Z")
        val point = GeoPoint(38.35, -6.70)
        val raw = waterMeasurementRecord(18.5, now, Formatting.MADRID)
        val checked = ManualRecordValidator.validate(raw, null, point, point, ManualOrigin.Typed, now, Formatting.MADRID)
        assertTrue(checked.errors.isEmpty(), checked.errors.toString())
        assertEquals(18.5, checked.record!!.values[ManualField.WATER_TEMP_SURFACE])
    }
}
