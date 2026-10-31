package com.nachojerez.carpstrategy.data.sync

import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TombstonesTest {
    private val now = Instant.parse("2026-10-31T10:00:00Z")

    private fun session(created: Long, start: Long = created / 1000) =
        SessionRow(startEpochSecond = start, latitude = 38.2, longitude = -6.75, rods = 2, catchesJson = "[]", createdAtEpochMs = created)

    private fun record(period: String, created: Long = 1000) =
        RecordRow(period, 1L, null, 38.2, -6.75, "38.20,-6.75", "Mi termómetro", "typed", null, "{}", null, created)

    @Test
    fun `lo que falta desde la ultima sincronizacion queda marcado`() {
        val before = SyncSnapshot(sessions = listOf(session(5000), session(9000)), records = listOf(record("t:1"), record("t:2")))
        val after = SyncSnapshot(sessions = listOf(session(9000)), records = listOf(record("t:1")))
        val marks = Tombstones.deletedSince(Tombstones.keysOf(before), after, now)
        assertEquals(
            setOf(Tombstone(Tombstones.SESSION, "5000|5", now.toEpochMilli()), Tombstone(Tombstones.RECORD, "38.20,-6.75|t:2|Mi termómetro", now.toEpochMilli())),
            marks.toSet(),
        )
        assertTrue(Tombstones.deletedSince(null, after, now).isEmpty())
    }

    @Test
    fun `una sesion borrada en un movil no vuelve al unir`() {
        val mark = Tombstone(Tombstones.SESSION, "5000|5", now.toEpochMilli())
        val phone = Tombstones.withTombstones(SyncSnapshot(sessions = listOf(session(9000))), listOf(mark))
        val drive = SyncSnapshot(sessions = listOf(session(5000), session(9000), session(12000)))
        val merged = mergeSnapshots(phone, drive, now)
        assertEquals(listOf(9000L, 12000L), merged.sessions.map { it.createdAtEpochMs })
        // La marca sigue viajando para el resto de móviles.
        assertEquals(listOf(mark), Tombstones.of(merged))
        // Y vale también si el borrado vino del otro lado.
        assertEquals(listOf(9000L, 12000L), mergeSnapshots(drive, phone, now).sessions.map { it.createdAtEpochMs })
    }

    @Test
    fun `cambiar la hora de inicio no deja la sesion duplicada`() {
        val original = session(5000, start = 100)
        val edited = session(5000, start = 160)
        val marks = Tombstones.deletedSince(Tombstones.keysOf(SyncSnapshot(sessions = listOf(original))), SyncSnapshot(sessions = listOf(edited)), now)
        val phone = Tombstones.withTombstones(SyncSnapshot(sessions = listOf(edited)), marks)
        val merged = mergeSnapshots(phone, SyncSnapshot(sessions = listOf(original)), now)
        assertEquals(listOf(160L), merged.sessions.map { it.startEpochSecond })
    }

    @Test
    fun `un registro creado despues del borrado con la misma clave se queda`() {
        val mark = Tombstone(Tombstones.RECORD, "38.20,-6.75|t:1|Mi termómetro", 2000)
        val kept = Tombstones.apply(SyncSnapshot(records = listOf(record("t:1", created = 3000))), listOf(mark))
        assertEquals(1, kept.records.size)
        val gone = Tombstones.apply(SyncSnapshot(records = listOf(record("t:1", created = 1000))), listOf(mark))
        assertTrue(gone.records.isEmpty())
    }

    @Test
    fun `las marcas se juntan sin repetir y caducan al ano`() {
        val old = Tombstone(Tombstones.SESSION, "1|1", now.minus(Duration.ofDays(400)).toEpochMilli())
        val a = Tombstone(Tombstones.SESSION, "2|2", now.minus(Duration.ofDays(3)).toEpochMilli())
        val b = a.copy(deletedAtMs = now.minus(Duration.ofDays(1)).toEpochMilli())
        assertEquals(listOf(b), Tombstones.union(listOf(a, old), listOf(b), now))
        assertEquals(listOf(b), Tombstones.decode(Tombstones.encode(listOf(b))))
        assertTrue(Tombstones.decode("basura").isEmpty())
    }
}
