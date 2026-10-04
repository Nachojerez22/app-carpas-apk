package com.nachojerez.carpstrategy.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Metadatos de la última previsión descargada para una ubicación (clave redondeada). */
@Entity(tableName = "forecast_meta")
data class ForecastMetaEntity(
    @PrimaryKey val locationKey: String,
    val requestedLat: Double,
    val requestedLon: Double,
    val gridLat: Double,
    val gridLon: Double,
    val elevationM: Double?,
    val timezone: String,
    val fetchedAtEpochMs: Long,
)

@Entity(
    tableName = "forecast_hour",
    primaryKeys = ["locationKey", "model", "epochSecond"],
)
data class ForecastHourEntity(
    val locationKey: String,
    /** [com.nachojerez.carpstrategy.domain.model.WeatherModel.apiId]. */
    val model: String,
    val epochSecond: Long,
    val temperatureC: Double?,
    val pressureMslHpa: Double?,
    val windSpeedKmh: Double?,
    val windDirectionDeg: Double?,
    val windGustsKmh: Double?,
    val cloudCoverPct: Double?,
    val precipitationMm: Double?,
    val shortwaveRadiationWm2: Double?,
    val weatherCode: Int? = null,
)

data class ForecastWithHours(
    @Embedded val meta: ForecastMetaEntity,
    @Relation(parentColumn = "locationKey", entityColumn = "locationKey")
    val hours: List<ForecastHourEntity>,
)

@Entity(tableName = "station")
data class StationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val province: String?,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
)

@Entity(
    tableName = "observation",
    primaryKeys = ["stationId", "epochSecond"],
)
data class ObservationEntity(
    val stationId: String,
    val epochSecond: Long,
    val temperatureC: Double?,
    val pressureMslHpa: Double?,
    val windSpeedKmh: Double?,
    val windDirectionDeg: Double?,
    val windGustKmh: Double?,
    val precipitationMm: Double?,
    val relativeHumidityPct: Double?,
)

/** Estación elegida para una ubicación y momento de la última descarga de sus observaciones. */
@Entity(tableName = "observation_meta", indices = [Index("stationId")])
data class ObservationMetaEntity(
    @PrimaryKey val locationKey: String,
    val stationId: String,
    val distanceKm: Double,
    val fetchedAtEpochMs: Long,
)

data class ObservationsWithStation(
    @Embedded val meta: ObservationMetaEntity,
    @Relation(parentColumn = "stationId", entityColumn = "id")
    val station: StationEntity?,
    @Relation(parentColumn = "stationId", entityColumn = "stationId")
    val observations: List<ObservationEntity>,
)

/** Marca de tiempo de sincronizaciones globales (p. ej. el inventario de estaciones). */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val fetchedAtEpochMs: Long,
)
