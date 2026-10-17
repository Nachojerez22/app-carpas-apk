package com.nachojerez.carpstrategy.data.reservoir

import android.content.Context
import com.nachojerez.carpstrategy.data.userdata.SettingDao
import com.nachojerez.carpstrategy.data.userdata.SettingEntity
import com.nachojerez.carpstrategy.domain.model.FishingLocation
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.reservoir.Reservoir
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirContext
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirSheet
import com.nachojerez.carpstrategy.domain.reservoir.Reservoirs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Embalses: el catálogo (assets, solo lectura), los añadidos por el usuario (ajuste
 * `reservoirs`) y las fichas (ajuste `reservoir_sheets`); los dos ajustes se sincronizan.
 * El embalse activo es el más cercano a la ubicación elegida en Lugar: elegir un embalse es
 * poner su nombre y su punto como ubicación (el tiempo y los datos siguen funcionando igual).
 */
@Singleton
class ReservoirRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: SettingDao,
    private val settings: SettingsRepository,
) {
    val catalog: List<Reservoir> by lazy {
        runCatching { context.assets.open(ASSET).bufferedReader().use { ReservoirJson.catalog(it.readText()) } }.getOrDefault(emptyList())
    }

    fun observeCustom(): Flow<List<Reservoir>> = dao.observe(KEY_RESERVOIRS).map(ReservoirJson::decodeCustom).distinctUntilChanged()

    /** Los tuyos primero (para que ganen a uno del catálogo en el mismo sitio). */
    fun observeAll(): Flow<List<Reservoir>> = observeCustom().map { it + catalog }

    fun observeSheets(): Flow<List<ReservoirSheet>> = dao.observe(KEY_SHEETS).map(ReservoirJson::decodeSheets).distinctUntilChanged()

    /** Embalse activo con su ficha; null si la ubicación no está en ningún embalse conocido. */
    fun observeActive(): Flow<ReservoirContext?> = combine(settings.observeLocation(), observeAll(), observeSheets()) { location, all, sheets ->
        active(location, all, sheets)
    }.distinctUntilChanged()

    suspend fun select(reservoir: Reservoir) = settings.setLocation(FishingLocation(reservoir.name, reservoir.point))

    /** Añade un embalse propio y lo deja como activo. */
    suspend fun addCustom(name: String, point: GeoPoint): Reservoir {
        val reservoir = Reservoirs.custom(name, point, UUID.randomUUID().toString().take(8))
        val current = observeCustom().first()
        dao.upsert(SettingEntity(KEY_RESERVOIRS, ReservoirJson.encodeCustom(current + reservoir)))
        select(reservoir)
        return reservoir
    }

    suspend fun removeCustom(id: String) {
        val current = observeCustom().first()
        dao.upsert(SettingEntity(KEY_RESERVOIRS, ReservoirJson.encodeCustom(current.filterNot { it.id == id })))
    }

    suspend fun sheet(reservoirId: String): ReservoirSheet? = observeSheets().first().firstOrNull { it.reservoirId == reservoirId }

    suspend fun saveSheet(sheet: ReservoirSheet) {
        val others = observeSheets().first().filterNot { it.reservoirId == sheet.reservoirId }
        dao.upsert(SettingEntity(KEY_SHEETS, ReservoirJson.encodeSheets(others + sheet)))
    }

    companion object {
        const val ASSET = "embalses.json"
        const val KEY_RESERVOIRS = "reservoirs"
        const val KEY_SHEETS = "reservoir_sheets"

        fun active(location: FishingLocation, all: List<Reservoir>, sheets: List<ReservoirSheet>): ReservoirContext? =
            Reservoirs.nearest(location.point, all)?.let { r -> ReservoirContext(r, sheets.firstOrNull { it.reservoirId == r.id }) }
    }
}
