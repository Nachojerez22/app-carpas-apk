package com.nachojerez.carpstrategy.data.userdata

import app.cash.turbine.test
import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.manual.SourcePriority
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** DAO en memoria con el mismo índice único (ubicación, periodo, fuente) que Room. */
private class FakeManualRecordDao : ManualRecordDao {
    val rows = MutableStateFlow<List<ManualRecordEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ManualRecordEntity>> = rows

    override suspend fun upsert(record: ManualRecordEntity): Long {
        if (record.id != 0L && rows.value.any { it.id == record.id }) {
            rows.value = rows.value.map { if (it.id == record.id) record else it }
            return -1
        }
        return insertOrReplace(listOf(record)).single()
    }

    override suspend fun insertOrReplace(records: List<ManualRecordEntity>): List<Long> = records.map { r ->
        val id = nextId++
        rows.value = rows.value.filterNot {
            it.locationKey == r.locationKey && it.periodKey == r.periodKey && it.source == r.source
        } + r.copy(id = id)
        id
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun all(): List<ManualRecordEntity> = rows.value

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}

private class FakeSettingDao : SettingDao {
    private val values = MutableStateFlow<Map<String, String>>(emptyMap())
    override fun observe(key: String): Flow<String?> = values.map { it[key] }
    override suspend fun upsert(setting: SettingEntity) {
        values.value = values.value + (setting.key to setting.value)
    }

    override fun observeAll(): Flow<List<SettingEntity>> = values.map { m -> m.map { SettingEntity(it.key, it.value) } }

    override suspend fun all(): List<SettingEntity> = values.value.map { SettingEntity(it.key, it.value) }

    override suspend fun delete(key: String) {
        values.value = values.value - key
    }
}

class UserDataRepositoriesTest {
    private val brovales = GeoPoint(38.35, -6.70)
    private val created = Instant.parse("2026-10-03T08:00:00Z")

    private fun record(
        period: RecordPeriod = RecordPeriod.At(Instant.parse("2026-10-03T06:00:00Z")),
        location: GeoPoint = brovales,
        source: String = "termómetro",
        origin: ManualOrigin = ManualOrigin.Typed,
        values: Map<ManualField, Double> = mapOf(ManualField.WATER_TEMP_SURFACE to 19.5),
    ) = ManualRecord(0, period, location, source, origin, values, notes = "nota", createdAt = created)

    @Test
    fun `guarda y recupera un registro sin perder nada`() = runTest {
        val repo = ManualDataRepositoryImpl(FakeManualRecordDao())
        val original = record(
            origin = ManualOrigin.Imported("datos.json"),
            values = mapOf(ManualField.WATER_TEMP_SURFACE to 19.5, ManualField.TURBIDITY to 2.0),
        )
        val id = repo.save(original)

        repo.observeRecords(brovales).test {
            assertEquals(listOf(original.copy(id = id)), awaitItem())
        }
    }

    @Test
    fun `registros por dia`() = runTest {
        val repo = ManualDataRepositoryImpl(FakeManualRecordDao())
        val daily = record(
            period = RecordPeriod.Day(LocalDate.parse("2026-09-28")),
            values = mapOf(ManualField.RESERVOIR_VOLUME to 4.0),
        )
        val id = repo.save(daily)
        repo.observeRecords(brovales).test {
            assertEquals(listOf(daily.copy(id = id)), awaitItem())
        }
    }

    @Test
    fun `solo devuelve registros cercanos a la ubicacion`() = runTest {
        val repo = ManualDataRepositoryImpl(FakeManualRecordDao())
        repo.save(record())
        repo.save(record(location = GeoPoint(40.41, -3.70), source = "Madrid"))
        repo.observeRecords(brovales).test {
            assertEquals(listOf("termómetro"), awaitItem().map { it.source })
        }
    }

    @Test
    fun `editar conserva el id y borrar elimina`() = runTest {
        val repo = ManualDataRepositoryImpl(FakeManualRecordDao())
        val id = repo.save(record())
        assertEquals(id, repo.save(record(values = mapOf(ManualField.WATER_TEMP_SURFACE to 20.0)).copy(id = id)))
        repo.observeRecords(brovales).test {
            assertEquals(20.0, awaitItem().single().values[ManualField.WATER_TEMP_SURFACE])
            repo.delete(id)
            assertEquals(emptyList<ManualRecord>(), awaitItem())
        }
    }

    @Test
    fun `reimportar la misma hora y fuente sustituye`() = runTest {
        val repo = ManualDataRepositoryImpl(FakeManualRecordDao())
        repo.import(listOf(record(), record(source = "otra")))
        repo.import(listOf(record(values = mapOf(ManualField.WATER_TEMP_SURFACE to 21.0))))
        repo.observeRecords(brovales).test {
            val rows = awaitItem()
            assertEquals(2, rows.size)
            assertEquals(21.0, rows.first { it.source == "termómetro" }.values[ManualField.WATER_TEMP_SURFACE])
        }
    }

    @Test
    fun `prioridad de fuentes guardada`() = runTest {
        val repo = SettingsRepositoryImpl(FakeSettingDao())
        repo.observeSourcePriority().test {
            assertEquals(SourcePriority.DEFAULT, awaitItem())
            val custom = SourcePriority(listOf(DataSource.AEMET, DataSource.MODELS))
            repo.setSourcePriority(custom)
            assertEquals(custom, awaitItem())
        }
    }
}
