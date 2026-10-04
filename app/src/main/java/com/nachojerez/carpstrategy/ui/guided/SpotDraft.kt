package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.SpotStructure
import com.nachojerez.carpstrategy.domain.journal.FishingZone

/** Formulario de un puesto (nuevo si [id] es null). Profundidad y distancia como texto (coma o punto). */
data class SpotDraft(
    val id: Long? = null,
    val name: String = "",
    val zone: FishingZone? = null,
    val structure: SpotStructure = SpotStructure.OTHER,
    val depth: String = "",
    val distance: String = "",
    val facingDeg: Int? = null,
    val notes: String = "",
) {
    val depthM: Double? get() = parse(depth)
    val distanceM: Double? get() = parse(distance)

    val depthValid: Boolean get() = depth.isBlank() || depthM?.let { it in 0.0..MAX_DEPTH_M } == true
    val distanceValid: Boolean get() = distance.isBlank() || distanceM?.let { it in 0.0..MAX_DISTANCE_M } == true

    val isValid: Boolean get() = name.isNotBlank() && depthValid && distanceValid

    fun toSpot(newId: () -> Long): Spot = Spot(
        id = id ?: newId(),
        name = name.trim(),
        zone = zone,
        structure = structure,
        depthM = depthM,
        distanceM = distanceM,
        facingDeg = facingDeg,
        notes = notes.trim(),
    )

    companion object {
        /** Límites de validación (un embalse pequeño; lances desde orilla). */
        const val MAX_DEPTH_M = 60.0
        const val MAX_DISTANCE_M = 250.0

        private fun parse(text: String): Double? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

        private fun format(value: Double?): String = value?.let { v ->
            if (v == Math.floor(v)) v.toLong().toString() else v.toString().replace('.', ',')
        }.orEmpty()

        fun of(spot: Spot) = SpotDraft(
            id = spot.id,
            name = spot.name,
            zone = spot.zone,
            structure = spot.structure,
            depth = format(spot.depthM),
            distance = format(spot.distanceM),
            facingDeg = spot.facingDeg,
            notes = spot.notes,
        )
    }
}

/** Sustituye el puesto con el mismo id o lo añade al final. */
fun List<Spot>.upsertSpot(spot: Spot): List<Spot> =
    if (any { it.id == spot.id }) map { if (it.id == spot.id) spot else it } else this + spot
