package com.nachojerez.carpstrategy.ui.manual

import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.ManualRecordValidator
import com.nachojerez.carpstrategy.domain.manual.RawValue
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditorStateTest {
    private val now = Instant.parse("2026-10-03T06:30:00Z") // 08:30 en Madrid
    private val brovales = GeoPoint(38.35, -6.70)

    @Test
    fun `un registro nuevo empieza en la hora y el dia locales`() {
        val state = EditorState.new(now)
        assertEquals("2026-10-03 08:30", state.time)
        assertEquals("2026-10-03", state.date)
        assertFalse(state.daily)
    }

    @Test
    fun `los campos visibles dependen del tipo`() {
        val hourly = EditorState.new(now).fields
        val daily = EditorState.new(now).copy(daily = true).fields
        assertTrue(ManualField.WATER_TEMP_SURFACE in hourly && ManualField.DAILY_PRECIPITATION !in hourly)
        assertEquals(
            listOf(ManualField.DAILY_PRECIPITATION, ManualField.RESERVOIR_VOLUME, ManualField.RESERVOIR_PERCENT, ManualField.RESERVOIR_ELEVATION),
            daily,
        )
    }

    @Test
    fun `al cambiar a dia se descartan los valores horarios`() {
        val state = EditorState.new(now).copy(
            daily = true,
            source = "MITECO",
            values = mapOf(ManualField.AIR_TEMPERATURE to "20", ManualField.RESERVOIR_VOLUME to "4,0"),
        )
        val raw = state.toRawRecord()
        assertNull(raw.time)
        assertEquals("2026-10-03", raw.date)
        assertEquals(RawValue.Text("4,0"), raw.values[ManualField.RESERVOIR_VOLUME.key])
        assertFalse(ManualField.AIR_TEMPERATURE.key in raw.values)
    }

    @Test
    fun `editar y volver a guardar produce el mismo registro`() {
        val original = ManualRecord(
            id = 7,
            period = RecordPeriod.At(Instant.parse("2026-10-02T06:00:00Z")),
            location = brovales,
            source = "termómetro",
            origin = ManualOrigin.Typed,
            values = mapOf(ManualField.WATER_TEMP_SURFACE to 19.5, ManualField.TURBIDITY to 2.0),
            notes = "nota",
            createdAt = now,
        )
        val state = EditorState.from(original, now)
        assertEquals("2026-10-02 08:00", state.time)
        assertEquals("19,5", state.values[ManualField.WATER_TEMP_SURFACE])
        assertEquals("2", state.values[ManualField.TURBIDITY])

        val again = ManualRecordValidator.validate(
            raw = state.toRawRecord(),
            recordNumber = null,
            location = brovales,
            appLocation = brovales,
            origin = ManualOrigin.Typed,
            now = now,
            existingId = state.id,
        ).record
        assertEquals(original, again)
    }

    @Test
    fun `registro por dia`() {
        val daily = ManualRecord(
            period = RecordPeriod.Day(LocalDate.parse("2026-09-28")),
            location = brovales,
            source = "MITECO",
            origin = ManualOrigin.Imported("x.json"),
            values = mapOf(ManualField.RESERVOIR_PERCENT to 57.1),
            createdAt = now,
        )
        val state = EditorState.from(daily, now)
        assertTrue(state.daily)
        assertEquals("2026-09-28", state.date)
        assertEquals("57,1", state.values[ManualField.RESERVOIR_PERCENT])
    }
}
