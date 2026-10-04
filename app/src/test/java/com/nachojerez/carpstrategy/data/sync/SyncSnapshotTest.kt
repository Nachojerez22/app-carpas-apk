package com.nachojerez.carpstrategy.data.sync

import com.nachojerez.carpstrategy.data.userdata.SessionEntity
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncSnapshotTest {
    private fun record(period: String, source: String = "Mi termómetro", values: String = """{"temp_agua_c":18.5}""") =
        RecordRow(period, 1L, null, 38.2, -6.75, "38.20,-6.75", source, "typed", null, values, null, 1000)

    private fun session(created: Long, bites: Int = 0) = SessionRow(
        startEpochSecond = created / 1000, latitude = 38.2, longitude = -6.75, rods = 2, catchesJson = "[]", createdAtEpochMs = created, bites = bites,
    )

    private val a = SyncSnapshot(
        records = listOf(record("t:1"), record("t:2")),
        settings = listOf(SettingRow("gear", "[]"), SettingRow("location", "brovales")),
        sessions = listOf(session(5000), session(9000)),
        predictions = listOf(PredictionRow(10, 1, "x", "{}")),
    )

    @Test
    fun `la huella no depende del orden y cambia con el contenido`() {
        val shuffled = a.copy(records = a.records.reversed(), sessions = a.sessions.reversed(), settings = a.settings.reversed())
        assertEquals(a.hash(), shuffled.hash())
        assertNotEquals(a.hash(), a.copy(sessions = listOf(session(5000, bites = 1), session(9000))).hash())
    }

    @Test
    fun `ida y vuelta del archivo de Drive y formatos ajenos`() {
        val text = SyncJson.encode(a, Instant.parse("2026-10-04T10:00:00Z"), "0.8.2")
        assertTrue(text.contains("\"formato\":\"carpstrategy-sync\""))
        assertEquals(a.canonical(), SyncJson.decode(text))
        assertNull(SyncJson.decode("""{"formato":"carpstrategy-diario","version":1,"subido":"x","datos":{}}"""))
        assertNull(SyncJson.decode("""{"formato":"carpstrategy-sync","version":99,"subido":"x","datos":{}}"""))
        assertNull(SyncJson.decode("basura"))
    }

    @Test
    fun `unir dos fotos no duplica y gana la preferida`() {
        val other = SyncSnapshot(
            records = listOf(record("t:2", values = """{"temp_agua_c":20}"""), record("t:3")),
            settings = listOf(SettingRow("gear", "[otro]")),
            sessions = listOf(session(9000, bites = 3), session(12000)),
        )
        val merged = mergeSnapshots(a, other)
        assertEquals(listOf("t:1", "t:2", "t:3"), merged.records.map { it.periodKey })
        assertEquals("""{"temp_agua_c":18.5}""", merged.records.first { it.periodKey == "t:2" }.valuesJson)
        assertEquals("[]", merged.settings.first { it.key == "gear" }.value)
        assertEquals(listOf(5000L, 9000L, 12000L), merged.sessions.map { it.createdAtEpochMs })
        assertEquals(0, merged.sessions.first { it.createdAtEpochMs == 9000L }.bites)
    }

    @Test
    fun `decision de sincronizacion`() {
        assertEquals(SyncAction.NONE, decideSync("L", localEmpty = true, lastSyncedHash = null, remoteHash = null))
        assertEquals(SyncAction.UPLOAD, decideSync("L", localEmpty = false, lastSyncedHash = null, remoteHash = null))
        assertEquals(SyncAction.NONE, decideSync("X", localEmpty = false, lastSyncedHash = "A", remoteHash = "X"))
        // Móvil nuevo y vacío: tira de Drive. Con datos: se unen.
        assertEquals(SyncAction.DOWNLOAD, decideSync("L", localEmpty = true, lastSyncedHash = null, remoteHash = "R"))
        assertEquals(SyncAction.MERGE, decideSync("L", localEmpty = false, lastSyncedHash = null, remoteHash = "R"))
        // Solo cambió Drive (otro móvil): bajar. Solo cambió este móvil: subir. Los dos: unir.
        assertEquals(SyncAction.DOWNLOAD, decideSync("A", localEmpty = false, lastSyncedHash = "A", remoteHash = "R"))
        assertEquals(SyncAction.UPLOAD, decideSync("L", localEmpty = false, lastSyncedHash = "A", remoteHash = "A"))
        assertEquals(SyncAction.MERGE, decideSync("L", localEmpty = false, lastSyncedHash = "A", remoteHash = "R"))
    }

    @Test
    fun `entidad y fila sin perder campos`() {
        val entity = SessionEntity(
            id = 7, startEpochSecond = 100, endEpochSecond = 200, latitude = 1.0, longitude = 2.0, zone = "NORTH", zoneDetail = "carrizal",
            depthM = 3.5, rods = 2, rodHoursOverride = null, bait = "maíz", rig = "pelo", groundbaitKg = 1.0, otherAnglers = 0, bites = 4,
            losses = 1, catchesJson = "[]", blank = false, predictionJson = "{}", contextJson = "{}", fulfilled = "YES", notes = "n",
            createdAtEpochMs = 99, guidedJson = "{\"canas\":[]}",
        )
        assertEquals(entity, entity.toRow().toEntity(id = 7))
        assertTrue(SyncSnapshot.isLocalOnly("local_sync_hash"))
    }
}
