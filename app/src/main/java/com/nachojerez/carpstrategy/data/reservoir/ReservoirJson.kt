package com.nachojerez.carpstrategy.data.reservoir

import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.reservoir.FishSpecies
import com.nachojerez.carpstrategy.domain.reservoir.Reservoir
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirRegime
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirSheet
import com.nachojerez.carpstrategy.domain.reservoir.SheetNote
import com.nachojerez.carpstrategy.domain.reservoir.SpeciesStatus
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * JSON de embalses: el catálogo (`assets/embalses.json`, formato `carpstrategy-embalses` v1,
 * generado con tools/ desde el listado oficial de cotos), los embalses añadidos por el usuario
 * (ajuste `reservoirs`) y las fichas (ajuste `reservoir_sheets`). Los dos ajustes se sincronizan.
 */
object ReservoirJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    @Serializable
    data class EntryDto(
        @SerialName("id") val id: String,
        @SerialName("nombre") val name: String,
        @SerialName("municipio") val municipality: String? = null,
        @SerialName("coto") val coto: String? = null,
        @SerialName("lat") val lat: Double,
        @SerialName("lon") val lon: Double,
        @SerialName("permiso_telematico") val onlinePermit: Boolean = false,
        @SerialName("plazas_asociados") val postsMembers: Int? = null,
        @SerialName("plazas_resto") val postsOthers: Int? = null,
        @SerialName("regimen") val regime: String = ReservoirRegime.GENERAL.name,
        @SerialName("abastecimiento") val waterSupply: Boolean = false,
        @SerialName("propio") val custom: Boolean = false,
    )

    @Serializable
    data class CatalogDto(
        @SerialName("formato") val format: String,
        @SerialName("version") val version: Int,
        @SerialName("fuente") val source: String = "",
        @SerialName("revisado") val reviewed: String = "",
        @SerialName("embalses") val reservoirs: List<EntryDto>,
    )

    @Serializable
    data class SpeciesDto(
        @SerialName("especie") val species: String,
        @SerialName("presente") val present: Boolean,
        @SerialName("confirmada") val confirmed: Boolean = false,
    )

    @Serializable
    data class NoteDto(@SerialName("texto") val text: String, @SerialName("evidencia") val evidence: String = Evidence.PURPLE.name)

    @Serializable
    data class SheetDto(
        @SerialName("embalse") val reservoirId: String,
        @SerialName("especies") val species: List<SpeciesDto> = emptyList(),
        @SerialName("se_puede_cebar") val groundbaitAllowed: Boolean? = null,
        @SerialName("abastecimiento_ia") val aiWaterSupply: Boolean? = null,
        @SerialName("resumen") val summary: String? = null,
        @SerialName("notas") val notes: List<NoteDto> = emptyList(),
        @SerialName("ia") val aiGeneratedAt: String? = null,
        @SerialName("actualizada") val updatedAt: String? = null,
    )

    private inline fun <reified E : Enum<E>> enumOf(name: String?): E? = name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

    private fun instant(s: String?) = s?.let { runCatching { Instant.parse(it) }.getOrNull() }

    fun EntryDto.toDomain() = Reservoir(
        id = id, name = name, municipality = municipality, point = GeoPoint(lat, lon),
        regime = enumOf<ReservoirRegime>(regime) ?: ReservoirRegime.GENERAL, coto = coto, onlinePermit = onlinePermit,
        postsMembers = postsMembers, postsOthers = postsOthers, waterSupplyByName = waterSupply, custom = custom,
    )

    fun Reservoir.toDto() = EntryDto(
        id, name, municipality, coto, point.latitude, point.longitude, onlinePermit, postsMembers, postsOthers, regime.name, waterSupplyByName, custom,
    )

    /** Catálogo; si no se puede leer, vacío (la app sigue con la ubicación a mano). */
    fun parseCatalog(text: String): CatalogDto? = runCatching { json.decodeFromString(CatalogDto.serializer(), text) }.getOrNull()
        ?.takeIf { it.format == "carpstrategy-embalses" && it.version == 1 }

    fun catalog(text: String): List<Reservoir> = parseCatalog(text)?.reservoirs.orEmpty().map { it.toDomain() }

    private val entries = ListSerializer(EntryDto.serializer())
    private val sheets = ListSerializer(SheetDto.serializer())

    fun encodeCustom(list: List<Reservoir>): String = json.encodeToString(entries, list.map { it.toDto().copy(custom = true) })

    fun decodeCustom(text: String?): List<Reservoir> =
        text?.let { runCatching { json.decodeFromString(entries, it) }.getOrNull() }.orEmpty().map { it.toDomain().copy(custom = true) }

    fun ReservoirSheet.toDto() = SheetDto(
        reservoirId,
        species.map { (sp, st) -> SpeciesDto(sp.name, st.present, st.confirmedByUser) },
        groundbaitAllowed, aiWaterSupply, summary, notes.map { NoteDto(it.text, it.evidence.name) },
        aiGeneratedAt?.toString(), updatedAt?.toString(),
    )

    fun SheetDto.toDomain() = ReservoirSheet(
        reservoirId = reservoirId,
        species = species.mapNotNull { s -> enumOf<FishSpecies>(s.species)?.let { it to SpeciesStatus(s.present, s.confirmed) } }.toMap(),
        groundbaitAllowed = groundbaitAllowed,
        aiWaterSupply = aiWaterSupply,
        summary = summary,
        notes = notes.map { SheetNote(it.text, enumOf<Evidence>(it.evidence) ?: Evidence.PURPLE) },
        aiGeneratedAt = instant(aiGeneratedAt),
        updatedAt = instant(updatedAt),
    )

    fun encodeSheets(list: List<ReservoirSheet>): String = json.encodeToString(sheets, list.map { it.toDto() })

    fun decodeSheets(text: String?): List<ReservoirSheet> =
        text?.let { runCatching { json.decodeFromString(sheets, it) }.getOrNull() }.orEmpty().map { it.toDomain() }
}
