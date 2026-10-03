package com.nachojerez.carpstrategy.data.userdata

import com.nachojerez.carpstrategy.data.userdata.UserDataMappers.toDomain
import com.nachojerez.carpstrategy.data.userdata.UserDataMappers.toEntity
import com.nachojerez.carpstrategy.domain.derived.Geo
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.SourcePriority
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.repository.ManualDataRepository
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class ManualDataRepositoryImpl @Inject constructor(
    private val dao: ManualRecordDao,
) : ManualDataRepository {

    override fun observeRecords(location: GeoPoint, radiusKm: Double): Flow<List<ManualRecord>> =
        dao.observeAll().map { rows ->
            rows.mapNotNull { it.toDomain() }
                .filter { Geo.haversineKm(it.location, location) <= radiusKm }
        }.distinctUntilChanged()

    override suspend fun save(record: ManualRecord): Long {
        val id = dao.upsert(record.toEntity())
        // Upsert devuelve -1 cuando actualiza una fila existente.
        return if (id == -1L) record.id else id
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun import(records: List<ManualRecord>): Int {
        if (records.isEmpty()) return 0
        return dao.insertOrReplace(records.map { it.copy(id = 0).toEntity() }).size
    }
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dao: SettingDao,
) : SettingsRepository {
    override fun observeSourcePriority(): Flow<SourcePriority> =
        dao.observe(KEY_SOURCE_PRIORITY).map(SourcePriority::decode).distinctUntilChanged()

    override suspend fun setSourcePriority(priority: SourcePriority) =
        dao.upsert(SettingEntity(KEY_SOURCE_PRIORITY, priority.encode()))

    companion object {
        const val KEY_SOURCE_PRIORITY = "source_priority"
    }
}
