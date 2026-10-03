package com.nachojerez.carpstrategy.data.repository

import com.nachojerez.carpstrategy.data.local.ForecastDao
import com.nachojerez.carpstrategy.data.local.ForecastHourEntity
import com.nachojerez.carpstrategy.data.local.ForecastMetaEntity
import com.nachojerez.carpstrategy.data.local.ForecastWithHours
import com.nachojerez.carpstrategy.data.local.ObservationDao
import com.nachojerez.carpstrategy.data.local.ObservationEntity
import com.nachojerez.carpstrategy.data.local.ObservationMetaEntity
import com.nachojerez.carpstrategy.data.local.ObservationsWithStation
import com.nachojerez.carpstrategy.data.local.StationDao
import com.nachojerez.carpstrategy.data.local.StationEntity
import com.nachojerez.carpstrategy.data.local.SyncStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** DAOs en memoria que imitan las consultas de Room (sin Android). */
class FakeForecastDao : ForecastDao {
    private val metas = MutableStateFlow<Map<String, ForecastMetaEntity>>(emptyMap())
    private val hours = MutableStateFlow<List<ForecastHourEntity>>(emptyList())

    override fun observe(locationKey: String): Flow<ForecastWithHours?> = metas.map { m ->
        m[locationKey]?.let { meta -> ForecastWithHours(meta, hours.value.filter { it.locationKey == locationKey }) }
    }

    override suspend fun deleteHours(locationKey: String) {
        hours.value = hours.value.filterNot { it.locationKey == locationKey }
    }

    override suspend fun insertHours(hours: List<ForecastHourEntity>) {
        this.hours.value = this.hours.value + hours
    }

    override suspend fun upsertMeta(meta: ForecastMetaEntity) {
        metas.value = metas.value + (meta.locationKey to meta)
    }

    override suspend fun replace(meta: ForecastMetaEntity, hours: List<ForecastHourEntity>) {
        deleteHours(meta.locationKey)
        insertHours(hours)
        upsertMeta(meta)
    }
}

class FakeObservationDao(private val stationDao: FakeStationDao) : ObservationDao {
    private val metas = MutableStateFlow<Map<String, ObservationMetaEntity>>(emptyMap())
    val observations = MutableStateFlow<List<ObservationEntity>>(emptyList())

    override fun observe(locationKey: String): Flow<ObservationsWithStation?> = metas.map { m ->
        m[locationKey]?.let { meta ->
            ObservationsWithStation(
                meta = meta,
                station = stationDao.stations.find { it.id == meta.stationId },
                observations = observations.value.filter { it.stationId == meta.stationId },
            )
        }
    }

    override suspend fun deleteForStation(stationId: String) {
        observations.value = observations.value.filterNot { it.stationId == stationId }
    }

    override suspend fun insertObservations(observations: List<ObservationEntity>) {
        this.observations.value = this.observations.value + observations
    }

    override suspend fun upsertMeta(meta: ObservationMetaEntity) {
        metas.value = metas.value + (meta.locationKey to meta)
    }

    override suspend fun deleteOrphans() {
        val used = metas.value.values.map { it.stationId }.toSet()
        observations.value = observations.value.filter { it.stationId in used }
    }

    override suspend fun replace(meta: ObservationMetaEntity, observations: List<ObservationEntity>) {
        deleteForStation(meta.stationId)
        insertObservations(observations)
        upsertMeta(meta)
        deleteOrphans()
    }
}

class FakeStationDao : StationDao {
    var stations: List<StationEntity> = emptyList()
    private val syncStates = mutableMapOf<String, SyncStateEntity>()

    override suspend fun getAll(): List<StationEntity> = stations
    override suspend fun deleteAll() { stations = emptyList() }
    override suspend fun insertAll(stations: List<StationEntity>) { this.stations = this.stations + stations }
    override suspend fun getSyncState(key: String): SyncStateEntity? = syncStates[key]
    override suspend fun upsertSyncState(state: SyncStateEntity) { syncStates[state.key] = state }
    override suspend fun replaceAll(stations: List<StationEntity>, syncState: SyncStateEntity) {
        deleteAll()
        insertAll(stations)
        upsertSyncState(syncState)
    }
}
