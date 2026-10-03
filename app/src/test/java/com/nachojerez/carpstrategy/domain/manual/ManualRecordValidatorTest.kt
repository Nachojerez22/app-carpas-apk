package com.nachojerez.carpstrategy.domain.manual

import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ManualRecordValidatorTest {
    private val now = Instant.parse("2026-10-03T10:00:00Z") // 12:00 en Madrid
    private val brovales = GeoPoint(38.35, -6.70)

    private fun validate(raw: RawRecord, location: GeoPoint = brovales) = ManualRecordValidator.validate(
        raw = raw,
        recordNumber = 1,
        location = location,
        appLocation = brovales,
        origin = ManualOrigin.Typed,
        now = now,
    )

    private fun codes(result: ValidationResult) = result.issues.map { it.code }

    private val ok = RawRecord(
        time = "2026-10-03T08:00",
        source = "Termómetro propio",
        values = mapOf("temp_agua_superficie_c" to RawValue.Number(19.5)),
    )

    @Test
    fun `registro horario valido en hora local de Madrid`() {
        val result = validate(ok)
        val record = result.record!!
        assertEquals(RecordPeriod.At(Instant.parse("2026-10-03T06:00:00Z")), record.period)
        assertEquals(mapOf(ManualField.WATER_TEMP_SURFACE to 19.5), record.values)
        assertEquals("Termómetro propio", record.source)
        assertTrue(result.issues.isEmpty())
    }

    @ParameterizedTest
    @CsvSource(
        "2026-10-03T08:00, 2026-10-03T06:00:00Z",
        "2026-10-03T08:00:00, 2026-10-03T06:00:00Z",
        "2026-10-03 08:00, 2026-10-03T06:00:00Z",
        "2026-10-03T06:00Z, 2026-10-03T06:00:00Z",
        "2026-10-03T08:00+02:00, 2026-10-03T06:00:00Z",
        "2026-01-15T08:00, 2026-01-15T07:00:00Z",
    )
    fun `formatos de hora admitidos`(text: String, expected: String) {
        assertEquals(Instant.parse(expected), ManualRecordValidator.parseTime(text, ZoneId.of("Europe/Madrid")))
    }

    @Test
    fun `numeros como texto con coma`() {
        val result = validate(ok.copy(values = mapOf("temp_aire_c" to RawValue.Text("14,2"), "presion_hpa" to RawValue.Text(" 1018.4 "))))
        assertEquals(14.2, result.record!!.values[ManualField.AIR_TEMPERATURE])
        assertEquals(1018.4, result.record!!.values[ManualField.PRESSURE_MSL])
    }

    @Test
    fun `campos vacios del formulario se ignoran`() {
        val result = validate(ok.copy(values = ok.values + ("temp_aire_c" to RawValue.Text("  "))))
        assertEquals(1, result.record!!.values.size)
    }

    @Test
    fun `fuente obligatoria`() {
        val result = validate(ok.copy(source = "  "))
        assertNull(result.record)
        assertEquals(listOf(IssueCode.MISSING_SOURCE), codes(result))
    }

    @Test
    fun `hora o fecha, pero no ambas ni ninguna`() {
        assertEquals(listOf(IssueCode.BOTH_TIME_AND_DATE), codes(validate(ok.copy(date = "2026-10-02"))))
        assertEquals(listOf(IssueCode.MISSING_PERIOD), codes(validate(ok.copy(time = null))))
        assertEquals(listOf(IssueCode.INVALID_TIME), codes(validate(ok.copy(time = "ayer por la tarde"))))
        assertEquals(
            listOf(IssueCode.INVALID_DATE),
            codes(validate(RawRecord(date = "2026-13-01", source = "x", values = mapOf("lluvia_dia_mm" to RawValue.Number(1.0))))),
        )
    }

    @Test
    fun `no admite horas ni fechas futuras salvo un margen de una hora`() {
        assertNotNull(validate(ok.copy(time = "2026-10-03T12:59")).record) // 10:59 UTC
        assertEquals(listOf(IssueCode.FUTURE_PERIOD), codes(validate(ok.copy(time = "2026-10-03T13:01"))))
        val tomorrow = RawRecord(date = "2026-10-04", source = "x", values = mapOf("lluvia_dia_mm" to RawValue.Number(1.0)))
        assertEquals(listOf(IssueCode.FUTURE_PERIOD), codes(validate(tomorrow)))
    }

    @Test
    fun `fuera de rango, no numerico y no entero`() {
        val result = validate(
            ok.copy(
                values = mapOf(
                    "presion_hpa" to RawValue.Number(10184.0),
                    "temp_aire_c" to RawValue.Text("templado"),
                    "turbidez" to RawValue.Number(2.5),
                ),
            ),
        )
        assertNull(result.record)
        assertEquals(
            setOf(IssueCode.OUT_OF_RANGE, IssueCode.NOT_A_NUMBER, IssueCode.NOT_AN_INTEGER),
            codes(result).toSet(),
        )
        val outOfRange = result.issues.first { it.code == IssueCode.OUT_OF_RANGE }
        assertEquals("presion_hpa", outOfRange.field)
        assertEquals(listOf("10184", "900", "1100"), outOfRange.args)
    }

    @Test
    fun `campos de dia y de hora no se mezclan`() {
        val dailyInHourly = validate(ok.copy(values = mapOf("lluvia_dia_mm" to RawValue.Number(3.0))))
        assertEquals(listOf(IssueCode.FIELD_NOT_ALLOWED), codes(dailyInHourly))

        val hourlyInDaily = validate(RawRecord(date = "2026-10-02", source = "x", values = mapOf("temp_aire_c" to RawValue.Number(20.0))))
        assertEquals(listOf(IssueCode.FIELD_NOT_ALLOWED), codes(hourlyInDaily))

        val level = validate(RawRecord(date = "2026-09-28", source = "MITECO", values = mapOf("nivel_embalse_hm3" to RawValue.Number(4.0))))
        assertEquals(RecordPeriod.Day(LocalDate.parse("2026-09-28")), level.record!!.period)
    }

    @Test
    fun `sin valores es un error`() {
        assertEquals(listOf(IssueCode.NO_VALUES), codes(validate(ok.copy(values = emptyMap()))))
        assertEquals(listOf(IssueCode.NO_VALUES), codes(validate(ok.copy(values = mapOf("temp_aire_c" to null)))))
    }

    @Test
    fun `avisos que no impiden guardar`() {
        val result = validate(
            ok.copy(values = mapOf("temp_agua_fondo_c" to RawValue.Number(18.0), "temp_aire" to RawValue.Number(14.0))),
            location = GeoPoint(38.88, -6.97), // Badajoz, ~60 km
        )
        assertNotNull(result.record)
        assertTrue(result.errors.isEmpty())
        assertEquals(
            setOf(IssueCode.UNKNOWN_FIELD, IssueCode.BOTTOM_WITHOUT_DEPTH, IssueCode.FAR_FROM_LOCATION),
            result.warnings.map { it.code }.toSet(),
        )
    }

    @Test
    fun `conserva el id al editar y recorta las notas`() {
        val record = ManualRecordValidator.validate(
            raw = ok.copy(notes = "  agua algo turbia  "),
            recordNumber = null,
            location = brovales,
            appLocation = brovales,
            origin = ManualOrigin.Typed,
            now = now,
            existingId = 42,
        ).record!!
        assertEquals(42, record.id)
        assertEquals("agua algo turbia", record.notes)
        assertEquals(now, record.createdAt)
    }
}
