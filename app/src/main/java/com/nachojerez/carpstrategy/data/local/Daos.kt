package com.nachojerez.carpstrategy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ForecastDao {
    @Transaction
    @Query("SELECT * FROM forecast_meta WHERE locationKey = :locationKey")
    fun observe(locationKey: String): Flow<ForecastWithHours?>

    @Query("DELETE FROM forecast_hour WHERE locationKey = :locationKey")
    suspend fun deleteHours(locationKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHours(hours: List<ForecastHourEntity>)

    @Upsert
    suspend fun upsertMeta(meta: ForecastMetaEntity)

    /** Sustituye la previsión de una ubicación de forma atómica. */
    @Transaction
    suspend fun replace(meta: ForecastMetaEntity, hours: List<ForecastHourEntity>) {
        deleteHours(meta.locationKey)
        insertHours(hours)
        upsertMeta(meta)
    }
}

@Dao
interface ObservationDao {
    @Transaction
    @Query("SELECT * FROM observation_meta WHERE locationKey = :locationKey")
    fun observe(locationKey: String): Flow<ObservationsWithStation?>

    @Query("DELETE FROM observation WHERE stationId = :stationId")
    suspend fun deleteForStation(stationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservations(observations: List<ObservationEntity>)

    @Upsert
    suspend fun upsertMeta(meta: ObservationMetaEntity)

    /** Borra observaciones de estaciones que ya no usa ninguna ubicación. */
    @Query("DELETE FROM observation WHERE stationId NOT IN (SELECT stationId FROM observation_meta)")
    suspend fun deleteOrphans()

    @Transaction
    suspend fun replace(meta: ObservationMetaEntity, observations: List<ObservationEntity>) {
        deleteForStation(meta.stationId)
        insertObservations(observations)
        upsertMeta(meta)
        deleteOrphans()
    }
}

@Dao
interface StationDao {
    @Query("SELECT * FROM station")
    suspend fun getAll(): List<StationEntity>

    @Query("DELETE FROM station")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<StationEntity>)

    @Query("SELECT * FROM sync_state WHERE `key` = :key")
    suspend fun getSyncState(key: String): SyncStateEntity?

    @Upsert
    suspend fun upsertSyncState(state: SyncStateEntity)

    @Transaction
    suspend fun replaceAll(stations: List<StationEntity>, syncState: SyncStateEntity) {
        deleteAll()
        insertAll(stations)
        upsertSyncState(syncState)
    }
}
