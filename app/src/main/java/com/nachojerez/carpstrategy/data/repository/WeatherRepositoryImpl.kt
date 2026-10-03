package com.nachojerez.carpstrategy.data.repository

import com.nachojerez.carpstrategy.data.local.ForecastDao
import com.nachojerez.carpstrategy.data.local.ObservationDao
import com.nachojerez.carpstrategy.data.local.ObservationMetaEntity
import com.nachojerez.carpstrategy.data.local.StationDao
import com.nachojerez.carpstrategy.data.local.SyncStateEntity
import com.nachojerez.carpstrategy.data.local.cacheKey
import com.nachojerez.carpstrategy.data.local.toDomain
import com.nachojerez.carpstrategy.data.local.toEntities
import com.nachojerez.carpstrategy.data.local.toEntity
import com.nachojerez.carpstrategy.data.remote.DataSourceException
import com.nachojerez.carpstrategy.data.remote.aemet.AemetDataSource
import com.nachojerez.carpstrategy.data.remote.openmeteo.OpenMeteoDataSource
import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.derived.Geo
import com.nachojerez.carpstrategy.domain.model.Cached
import com.nachojerez.carpstrategy.domain.model.DataError
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.MultiModelForecast
import com.nachojerez.carpstrategy.domain.model.NearbyObservations
import com.nachojerez.carpstrategy.domain.model.RefreshOutcome
import com.nachojerez.carpstrategy.domain.model.WeatherStation
import com.nachojerez.carpstrategy.domain.repository.WeatherRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val openMeteo: OpenMeteoDataSource,
    private val aemet: AemetDataSource,
    private val forecastDao: ForecastDao,
    private val observationDao: ObservationDao,
    private val stationDao: StationDao,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WeatherRepository {

    override fun observeForecast(location: GeoPoint): Flow<Cached<MultiModelForecast>?> =
        forecastDao.observe(location.cacheKey())
            .distinctUntilChanged()
            .map { it?.toDomain() }

    override suspend fun refreshForecast(location: GeoPoint): RefreshOutcome = withContext(ioDispatcher) {
        outcomeOf {
            val forecast = openMeteo.fetchForecast(location)
            if (forecast.series.isEmpty()) throw DataSourceException(DataError.NO_DATA, "Ningún modelo devolvió datos")
            val (meta, hours) = forecast.toEntities(location.cacheKey(), clock.instant())
            forecastDao.replace(meta, hours)
        }
    }

    override fun observeObservations(location: GeoPoint): Flow<Cached<NearbyObservations>?> =
        observationDao.observe(location.cacheKey())
            .distinctUntilChanged()
            .map { it?.toDomain() }

    override suspend fun refreshObservations(location: GeoPoint): RefreshOutcome = withContext(ioDispatcher) {
        outcomeOf {
            val stations = stations()
            val candidates = Geo.nearest(
                origin = location,
                items = stations,
                maxDistanceKm = MAX_STATION_DISTANCE_KM,
                limit = MAX_STATION_CANDIDATES,
            ) { it.point }
            if (candidates.isEmpty()) {
                throw DataSourceException(DataError.NO_DATA, "Ninguna estación a menos de $MAX_STATION_DISTANCE_KM km")
            }
            // Algunas estaciones del inventario no publican observación horaria: se prueba la siguiente.
            for ((station, distanceKm) in candidates) {
                val observations = try {
                    aemet.fetchObservations(station.id)
                } catch (e: DataSourceException) {
                    if (e.error == DataError.NO_DATA) continue else throw e
                }
                if (observations.isEmpty()) continue
                observationDao.replace(
                    meta = ObservationMetaEntity(
                        locationKey = location.cacheKey(),
                        stationId = station.id,
                        distanceKm = distanceKm,
                        fetchedAtEpochMs = clock.instant().toEpochMilli(),
                    ),
                    observations = observations.map { it.toEntity() },
                )
                return@outcomeOf
            }
            throw DataSourceException(DataError.NO_DATA, "Las estaciones cercanas no tienen observaciones recientes")
        }
    }

    /** Inventario de estaciones; cambia muy poco, se renueva cada [STATIONS_MAX_AGE]. */
    private suspend fun stations(): List<WeatherStation> {
        val cached = stationDao.getAll()
        val lastSync = stationDao.getSyncState(STATIONS_SYNC_KEY)?.let { Instant.ofEpochMilli(it.fetchedAtEpochMs) }
        val isFresh = lastSync != null && Duration.between(lastSync, clock.instant()) < STATIONS_MAX_AGE
        if (cached.isNotEmpty() && isFresh) return cached.map { it.toDomain() }

        return try {
            val fetched = aemet.fetchStations()
            if (fetched.isEmpty()) throw DataSourceException(DataError.NO_DATA, "Inventario de estaciones vacío")
            stationDao.replaceAll(
                stations = fetched.map { it.toEntity() },
                syncState = SyncStateEntity(STATIONS_SYNC_KEY, clock.instant().toEpochMilli()),
            )
            fetched
        } catch (e: DataSourceException) {
            // Un inventario antiguo sigue siendo útil si AEMET falla.
            if (cached.isNotEmpty() && e.error != DataError.MISSING_API_KEY) cached.map { it.toDomain() } else throw e
        }
    }

    private inline fun outcomeOf(block: () -> Unit): RefreshOutcome = try {
        block()
        RefreshOutcome.Success
    } catch (e: DataSourceException) {
        RefreshOutcome.Failure(e.error, e.message)
    }

    companion object {
        const val MAX_STATION_DISTANCE_KM = 60.0
        const val MAX_STATION_CANDIDATES = 3
        val STATIONS_MAX_AGE: Duration = Duration.ofDays(30)
        const val STATIONS_SYNC_KEY = "aemet_stations"
    }
}
