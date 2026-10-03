package com.nachojerez.carpstrategy.data.repository

import app.cash.turbine.test
import com.nachojerez.carpstrategy.data.remote.DataSourceException
import com.nachojerez.carpstrategy.data.remote.aemet.AemetDataSource
import com.nachojerez.carpstrategy.data.remote.openmeteo.OpenMeteoDataSource
import com.nachojerez.carpstrategy.domain.model.DataError
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.HourlyWeather
import com.nachojerez.carpstrategy.domain.model.MultiModelForecast
import com.nachojerez.carpstrategy.domain.model.RefreshOutcome
import com.nachojerez.carpstrategy.domain.model.StationObservation
import com.nachojerez.carpstrategy.domain.model.WeatherModel
import com.nachojerez.carpstrategy.domain.model.WeatherStation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WeatherRepositoryImplTest {
    private val now = Instant.parse("2026-10-03T08:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val brovales = GeoPoint(38.35, -6.70)

    private val openMeteo = mockk<OpenMeteoDataSource>()
    private val aemet = mockk<AemetDataSource>()
    private val stationDao = FakeStationDao()
    private val forecastDao = FakeForecastDao()
    private val observationDao = FakeObservationDao(stationDao)

    private fun repository(dispatcher: TestDispatcher) = WeatherRepositoryImpl(
        openMeteo, aemet, forecastDao, observationDao, stationDao, clock, dispatcher,
    )

    private fun hour(epoch: Long, t: Double) =
        HourlyWeather(Instant.ofEpochSecond(epoch), t, 1018.0, 5.0, 200.0, 10.0, 50.0, 0.0, 100.0)

    private val forecast = MultiModelForecast(
        requested = brovales,
        gridPoint = GeoPoint(38.375, -6.6875),
        elevationM = 305.0,
        timezone = "Europe/Madrid",
        series = mapOf(
            WeatherModel.ICON_EU to listOf(hour(1_790_377_200, 21.8), hour(1_790_373_600, 24.4)),
            WeatherModel.ECMWF_IFS025 to listOf(hour(1_790_373_600, 24.7)),
        ),
    )

    private val jerez = WeatherStation("JEREZ", "JEREZ", "BADAJOZ", GeoPoint(38.32, -6.77), 511.0)
    private val fregenal = WeatherStation("FREGENAL", "FREGENAL", "BADAJOZ", GeoPoint(38.17, -6.65), 560.0)
    private val madrid = WeatherStation("MADRID", "MADRID", "MADRID", GeoPoint(40.41, -3.68), 667.0)

    private fun observation(stationId: String, epoch: Long) = StationObservation(
        stationId, Instant.ofEpochSecond(epoch), 16.4, 1018.4, 5.4, 200.0, 15.1, 0.0, 81.0,
    )

    @Test
    fun `sin cache no hay prevision`() = runTest {
        repository(StandardTestDispatcher(testScheduler)).observeForecast(brovales).test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `refrescar guarda la prevision con su hora de descarga y ordenada`() = runTest {
        coEvery { openMeteo.fetchForecast(brovales, any()) } returns forecast
        val repo = repository(StandardTestDispatcher(testScheduler))

        assertEquals(RefreshOutcome.Success, repo.refreshForecast(brovales))

        repo.observeForecast(brovales).test {
            val cached = awaitItem()!!
            assertEquals(now, cached.fetchedAt)
            assertEquals(GeoPoint(38.375, -6.6875), cached.data.gridPoint)
            assertEquals(listOf(24.4, 21.8), cached.data.series.getValue(WeatherModel.ICON_EU).map { it.temperatureC })
            assertEquals(1, cached.data.series.getValue(WeatherModel.ECMWF_IFS025).size)
        }
    }

    @Test
    fun `si la descarga falla se conserva la cache anterior`() = runTest {
        coEvery { openMeteo.fetchForecast(brovales, any()) } returns forecast
        val repo = repository(StandardTestDispatcher(testScheduler))
        repo.refreshForecast(brovales)

        coEvery { openMeteo.fetchForecast(brovales, any()) } throws DataSourceException(DataError.NETWORK)
        assertEquals(RefreshOutcome.Failure(DataError.NETWORK, "NETWORK"), repo.refreshForecast(brovales))

        repo.observeForecast(brovales).test {
            assertEquals(now, awaitItem()!!.fetchedAt)
        }
    }

    @Test
    fun `una prevision sin modelos es un fallo sin datos`() = runTest {
        coEvery { openMeteo.fetchForecast(brovales, any()) } returns forecast.copy(series = emptyMap())
        val outcome = repository(StandardTestDispatcher(testScheduler)).refreshForecast(brovales)
        assertEquals(DataError.NO_DATA, (outcome as RefreshOutcome.Failure).error)
    }

    @Test
    fun `usa la estacion mas cercana con datos y descarta las lejanas`() = runTest {
        coEvery { aemet.fetchStations() } returns listOf(madrid, fregenal, jerez)
        coEvery { aemet.fetchObservations("JEREZ") } throws DataSourceException(DataError.NO_DATA)
        coEvery { aemet.fetchObservations("FREGENAL") } returns listOf(observation("FREGENAL", 1_791_010_800))
        val repo = repository(StandardTestDispatcher(testScheduler))

        assertEquals(RefreshOutcome.Success, repo.refreshObservations(brovales))

        repo.observeObservations(brovales).test {
            val cached = awaitItem()!!
            assertEquals("FREGENAL", cached.data.station.id)
            assertEquals(20.0, cached.data.distanceKm, 2.0)
            assertEquals(1, cached.data.observations.size)
            assertEquals(now, cached.fetchedAt)
        }
        coVerify(exactly = 0) { aemet.fetchObservations("MADRID") }
    }

    @Test
    fun `el inventario reciente no se vuelve a descargar`() = runTest {
        coEvery { aemet.fetchStations() } returns listOf(jerez)
        coEvery { aemet.fetchObservations("JEREZ") } returns listOf(observation("JEREZ", 1_791_010_800))
        val repo = repository(StandardTestDispatcher(testScheduler))

        repo.refreshObservations(brovales)
        repo.refreshObservations(brovales)

        coVerify(exactly = 1) { aemet.fetchStations() }
        coVerify(exactly = 2) { aemet.fetchObservations("JEREZ") }
    }

    @Test
    fun `si ninguna estacion cercana tiene datos es un fallo sin datos`() = runTest {
        coEvery { aemet.fetchStations() } returns listOf(jerez, madrid)
        coEvery { aemet.fetchObservations("JEREZ") } returns emptyList()
        val outcome = repository(StandardTestDispatcher(testScheduler)).refreshObservations(brovales)
        assertEquals(DataError.NO_DATA, (outcome as RefreshOutcome.Failure).error)
    }

    @Test
    fun `sin API key se informa del motivo`() = runTest {
        coEvery { aemet.fetchStations() } throws DataSourceException(DataError.MISSING_API_KEY)
        val outcome = repository(StandardTestDispatcher(testScheduler)).refreshObservations(brovales)
        assertEquals(DataError.MISSING_API_KEY, (outcome as RefreshOutcome.Failure).error)
    }

    @Test
    fun `un error distinto de sin datos detiene la busqueda de estaciones`() = runTest {
        coEvery { aemet.fetchStations() } returns listOf(jerez, fregenal)
        coEvery { aemet.fetchObservations("JEREZ") } throws DataSourceException(DataError.RATE_LIMITED)
        val outcome = repository(StandardTestDispatcher(testScheduler)).refreshObservations(brovales)
        assertEquals(DataError.RATE_LIMITED, (outcome as RefreshOutcome.Failure).error)
        coVerify(exactly = 0) { aemet.fetchObservations("FREGENAL") }
    }
}
