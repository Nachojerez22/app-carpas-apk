package com.nachojerez.carpstrategy.data.local

import com.nachojerez.carpstrategy.domain.model.Cached
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.HourlyWeather
import com.nachojerez.carpstrategy.domain.model.MultiModelForecast
import com.nachojerez.carpstrategy.domain.model.NearbyObservations
import com.nachojerez.carpstrategy.domain.model.StationObservation
import com.nachojerez.carpstrategy.domain.model.WeatherModel
import com.nachojerez.carpstrategy.domain.model.WeatherStation
import java.time.Instant
import java.util.Locale

/** Clave de caché: coordenadas redondeadas a 0,01° (~1 km). */
fun GeoPoint.cacheKey(): String = String.format(Locale.ROOT, "%.2f,%.2f", latitude, longitude)

fun MultiModelForecast.toEntities(
    locationKey: String,
    fetchedAt: Instant,
): Pair<ForecastMetaEntity, List<ForecastHourEntity>> {
    val meta = ForecastMetaEntity(
        locationKey = locationKey,
        requestedLat = requested.latitude,
        requestedLon = requested.longitude,
        gridLat = gridPoint.latitude,
        gridLon = gridPoint.longitude,
        elevationM = elevationM,
        timezone = timezone,
        fetchedAtEpochMs = fetchedAt.toEpochMilli(),
    )
    val hours = series.flatMap { (model, list) ->
        list.map { h ->
            ForecastHourEntity(
                locationKey = locationKey,
                model = model.apiId,
                epochSecond = h.time.epochSecond,
                temperatureC = h.temperatureC,
                pressureMslHpa = h.pressureMslHpa,
                windSpeedKmh = h.windSpeedKmh,
                windDirectionDeg = h.windDirectionDeg,
                windGustsKmh = h.windGustsKmh,
                cloudCoverPct = h.cloudCoverPct,
                precipitationMm = h.precipitationMm,
                shortwaveRadiationWm2 = h.shortwaveRadiationWm2,
                weatherCode = h.weatherCode,
            )
        }
    }
    return meta to hours
}

fun ForecastWithHours.toDomain(): Cached<MultiModelForecast> {
    val series = hours
        .mapNotNull { entity -> WeatherModel.fromApiId(entity.model)?.let { it to entity } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, list) ->
            list.sortedBy { it.epochSecond }.map { e ->
                HourlyWeather(
                    time = Instant.ofEpochSecond(e.epochSecond),
                    temperatureC = e.temperatureC,
                    pressureMslHpa = e.pressureMslHpa,
                    windSpeedKmh = e.windSpeedKmh,
                    windDirectionDeg = e.windDirectionDeg,
                    windGustsKmh = e.windGustsKmh,
                    cloudCoverPct = e.cloudCoverPct,
                    precipitationMm = e.precipitationMm,
                    shortwaveRadiationWm2 = e.shortwaveRadiationWm2,
                    weatherCode = e.weatherCode,
                )
            }
        }
    return Cached(
        data = MultiModelForecast(
            requested = GeoPoint(meta.requestedLat, meta.requestedLon),
            gridPoint = GeoPoint(meta.gridLat, meta.gridLon),
            elevationM = meta.elevationM,
            timezone = meta.timezone,
            series = series,
        ),
        fetchedAt = Instant.ofEpochMilli(meta.fetchedAtEpochMs),
    )
}

fun WeatherStation.toEntity() = StationEntity(
    id = id,
    name = name,
    province = province,
    latitude = point.latitude,
    longitude = point.longitude,
    altitudeM = altitudeM,
)

fun StationEntity.toDomain() = WeatherStation(
    id = id,
    name = name,
    province = province,
    point = GeoPoint(latitude, longitude),
    altitudeM = altitudeM,
)

fun StationObservation.toEntity() = ObservationEntity(
    stationId = stationId,
    epochSecond = time.epochSecond,
    temperatureC = temperatureC,
    pressureMslHpa = pressureMslHpa,
    windSpeedKmh = windSpeedKmh,
    windDirectionDeg = windDirectionDeg,
    windGustKmh = windGustKmh,
    precipitationMm = precipitationMm,
    relativeHumidityPct = relativeHumidityPct,
)

fun ObservationEntity.toDomain() = StationObservation(
    stationId = stationId,
    time = Instant.ofEpochSecond(epochSecond),
    temperatureC = temperatureC,
    pressureMslHpa = pressureMslHpa,
    windSpeedKmh = windSpeedKmh,
    windDirectionDeg = windDirectionDeg,
    windGustKmh = windGustKmh,
    precipitationMm = precipitationMm,
    relativeHumidityPct = relativeHumidityPct,
)

/** Null si la estación ya no está en el inventario guardado. */
fun ObservationsWithStation.toDomain(): Cached<NearbyObservations>? {
    val station = station ?: return null
    return Cached(
        data = NearbyObservations(
            station = station.toDomain(),
            distanceKm = meta.distanceKm,
            observations = observations.sortedBy { it.epochSecond }.map { it.toDomain() },
        ),
        fetchedAt = Instant.ofEpochMilli(meta.fetchedAtEpochMs),
    )
}
