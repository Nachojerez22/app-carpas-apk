package com.nachojerez.carpstrategy.data.manual

import com.nachojerez.carpstrategy.domain.manual.IssueCode
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.model.FishingLocation
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManualDataJsonTest {
    @Test
    fun `la copia de seguridad se vuelve a importar con los mismos valores`() {
        val point = GeoPoint(38.35, -6.70)
        val now = Instant.parse("2026-10-03T10:00:00Z")
        val records = listOf(
            ManualRecord(
                period = RecordPeriod.At(Instant.parse("2026-10-02T06:00:00Z")), location = point, source = "termómetro",
                origin = ManualOrigin.Typed, values = mapOf(ManualField.WATER_TEMP_SURFACE to 19.5, ManualField.TURBIDITY to 2.0),
                notes = "orilla norte", createdAt = now,
            ),
            ManualRecord(
                period = RecordPeriod.Day(LocalDate.parse("2026-09-28")), location = point, source = "Boletín",
                origin = ManualOrigin.Typed, values = mapOf(ManualField.RESERVOIR_PERCENT to 57.1), createdAt = now,
            ),
        )
        val text = ManualDataJson.export(records, FishingLocation("Embalse de Brovales", point))
        assertTrue(text.contains("\"hora\": \"2026-10-02T08:00\""))
        assertTrue(text.contains("\"turbidez\": 2,"))
        val preview = ManualDataJson.parse(text, "copia.json", point, now)
        assertTrue(preview.canImport, preview.issues.toString())
        // Orden cronológico: primero el registro diario del 28/9.
        assertEquals(records.reversed().map { Triple(it.period, it.values, it.notes) }, preview.records.map { Triple(it.period, it.values, it.notes) })
    }

    private val now = Instant.parse("2026-10-03T10:00:00Z")
    private val brovales = GeoPoint(38.35, -6.70)

    private fun parse(text: String) = ManualDataJson.parse(text, "datos.json", brovales, now)

    private fun wrap(records: String, extra: String = "") =
        """{"formato":"carpstrategy-datos","version":1$extra,"registros":[$records]}"""

    /** El ejemplo documentado (docs/) y el incluido en la app (assets) deben ser idénticos y válidos. */
    @Test
    fun `el ejemplo documentado es valido e igual al de la app`() {
        val docs = File("../docs/ejemplos/datos-ejemplo.json")
        val asset = File("src/main/assets/datos-ejemplo.json")
        assertTrue(docs.exists() && asset.exists(), "Ejecutar desde el módulo app")
        assertEquals(docs.readText(), asset.readText())

        val preview = parse(docs.readText())
        assertTrue(preview.issues.isEmpty(), "Problemas en el ejemplo: ${preview.issues}")
        assertTrue(preview.canImport)
        assertEquals(4, preview.records.size)
        assertEquals(brovales, preview.fileLocation)

        val water = preview.records.first()
        assertEquals(RecordPeriod.At(Instant.parse("2026-10-02T06:00:00Z")), water.period)
        assertEquals(19.5, water.values[ManualField.WATER_TEMP_SURFACE])
        assertEquals(ManualOrigin.Imported("datos.json"), water.origin)

        val level = preview.records[2]
        assertEquals(RecordPeriod.Day(LocalDate.parse("2026-09-28")), level.period)
        assertEquals(57.1, level.values[ManualField.RESERVOIR_PERCENT])
    }

    @Test
    fun `errores de archivo`() {
        fun code(text: String) = parse(text).issues.single().code
        assertEquals(IssueCode.NOT_JSON, code("no es json"))
        assertEquals(IssueCode.NOT_JSON, code("[1,2]"))
        assertEquals(IssueCode.BAD_FORMAT, code("""{"formato":"otro","version":1}"""))
        assertEquals(IssueCode.UNSUPPORTED_VERSION, code("""{"formato":"carpstrategy-datos","version":2}"""))
        assertEquals(IssueCode.INVALID_ZONE, code(wrap("", ""","zona_horaria":"Marte/Olympus"""")))
        assertEquals(IssueCode.INVALID_LOCATION, code(wrap("", ""","ubicacion":{"lat":120,"lon":0}""")))
        assertEquals(IssueCode.NO_RECORDS, code(wrap("")))
        assertEquals(IssueCode.NO_RECORDS, code("""{"formato":"carpstrategy-datos","version":1,"registros":"x"}"""))
    }

    @Test
    fun `todo o nada con los errores numerados por registro`() {
        val preview = parse(
            wrap(
                """
                {"hora":"2026-10-02T08:00","fuente":"a","temp_aire_c":14.2},
                {"hora":"2026-10-02T09:00","temp_aire_c":15.0},
                "texto",
                {"fecha":"2026-10-01","fuente":"b","temp_aire_c":20}
                """,
            ),
        )
        assertFalse(preview.canImport)
        assertTrue(preview.records.isEmpty())
        assertEquals(
            listOf(2 to IssueCode.MISSING_SOURCE, 3 to IssueCode.RECORD_NOT_OBJECT, 4 to IssueCode.FIELD_NOT_ALLOWED),
            preview.errors.map { it.recordNumber to it.code },
        )
    }

    @Test
    fun `avisos de ubicacion lejana una sola vez, claves desconocidas y duplicados`() {
        val preview = parse(
            wrap(
                """
                {"hora":"2026-10-02T08:00","fuente":"a","temp_aire_c":14.2,"temp_aire":14},
                {"hora":"2026-10-02T09:00","fuente":"a","temp_aire_c":15.0},
                {"hora":"2026-10-02T08:00","fuente":"a","temp_aire_c":14.5}
                """,
                ""","ubicacion":{"lat":38.88,"lon":-6.97}""",
            ),
        )
        assertTrue(preview.canImport)
        assertEquals(2, preview.records.size, "El duplicado sustituye al primero")
        assertEquals(14.5, preview.records.first().values[ManualField.AIR_TEMPERATURE])
        assertEquals(
            listOf(
                1 to IssueCode.UNKNOWN_FIELD,
                null to IssueCode.FAR_FROM_LOCATION,
                3 to IssueCode.DUPLICATE_IN_FILE,
            ),
            preview.warnings.map { it.recordNumber to it.code },
        )
        assertEquals(GeoPoint(38.88, -6.97), preview.records.first().location)
    }

    @Test
    fun `sin ubicacion usa la de la app y numeros como texto`() {
        val preview = parse(wrap("""{"hora":"2026-10-02T08:00","fuente":"a","presion_hpa":"1018,4","viento_kmh":null}"""))
        assertTrue(preview.canImport)
        val record = preview.records.single()
        assertEquals(brovales, record.location)
        assertEquals(mapOf(ManualField.PRESSURE_MSL to 1018.4), record.values)
    }

    @Test
    fun `respeta la zona horaria del archivo`() {
        val preview = parse(wrap("""{"hora":"2026-10-02T08:00","fuente":"a","temp_aire_c":10}""", ""","zona_horaria":"UTC""""))
        assertEquals(RecordPeriod.At(Instant.parse("2026-10-02T08:00:00Z")), preview.records.single().period)
    }

    @Test
    fun `limite de registros`() {
        val many = (1..ManualDataJson.MAX_RECORDS + 1).joinToString(",") { """{"fecha":"2026-01-01","fuente":"a$it","lluvia_dia_mm":1}""" }
        assertEquals(IssueCode.TOO_MANY_RECORDS, parse(wrap(many)).issues.single().code)
    }
}
