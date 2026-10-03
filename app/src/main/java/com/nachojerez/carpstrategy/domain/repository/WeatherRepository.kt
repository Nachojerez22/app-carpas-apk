package com.nachojerez.carpstrategy.domain.repository

import com.nachojerez.carpstrategy.domain.model.Cached
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.MultiModelForecast
import com.nachojerez.carpstrategy.domain.model.NearbyObservations
import com.nachojerez.carpstrategy.domain.model.RefreshOutcome
import kotlinx.coroutines.flow.Flow

/**
 * Datos meteorológicos offline-first: la caché local es la única fuente de verdad que se
 * observa. `refresh*` descarga datos nuevos y los guarda; si falla, la caché anterior se mantiene.
 */
interface WeatherRepository {
    fun observeForecast(location: GeoPoint): Flow<Cached<MultiModelForecast>?>
    suspend fun refreshForecast(location: GeoPoint): RefreshOutcome

    fun observeObservations(location: GeoPoint): Flow<Cached<NearbyObservations>?>
    suspend fun refreshObservations(location: GeoPoint): RefreshOutcome
}
