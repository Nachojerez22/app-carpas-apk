package com.nachojerez.carpstrategy.domain.reservoir

import com.nachojerez.carpstrategy.domain.derived.Geo
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Instant

/** Régimen de pesca de una masa de agua (CONOCIMIENTO.md §7). */
enum class ReservoirRegime { GENERAL, COTO }

/**
 * Embalse o masa de agua. Los del catálogo salen del listado oficial de cotos de Extremadura
 * (`assets/embalses.json`); el usuario puede añadir los suyos con el GPS o con coordenadas.
 */
data class Reservoir(
    val id: String,
    val name: String,
    val municipality: String? = null,
    val point: GeoPoint,
    val regime: ReservoirRegime = ReservoirRegime.GENERAL,
    val coto: String? = null,
    /** Permiso diario por internet (Modelo 50). */
    val onlinePermit: Boolean = false,
    /** Plazas para asociados y para el resto (0 = sin límite en el listado). */
    val postsMembers: Int? = null,
    val postsOthers: Int? = null,
    /** El nombre oficial dice que es de abastecimiento: ⚖ no se puede cebar (§7). */
    val waterSupplyByName: Boolean = false,
    /** Añadido por el usuario (no está en el catálogo). */
    val custom: Boolean = false,
)

/** Especies que pueden compartir el agua con la carpa y cambiar cómo se pesca (§5.11). */
enum class FishSpecies { CARP, BARBEL, NASE, TENCH, CRUCIAN, BULLHEAD, CATFISH, BLACK_BASS, PIKE, PIKEPERCH, SUNFISH, BLEAK, CRAYFISH }

/** Si una especie está o no. Lo que confirma el usuario manda sobre lo que dice la IA. */
data class SpeciesStatus(val present: Boolean, val confirmedByUser: Boolean)

/** Nota de la ficha (estructura, estrategia…) con su evidencia. */
data class SheetNote(val text: String, val evidence: Evidence)

/**
 * Ficha de un embalse: especies, si se puede cebar y notas. La prepara la IA (🟣, sin verificar)
 * y el usuario la confirma o corrige; no se inventa nada: lo desconocido queda sin rellenar.
 */
data class ReservoirSheet(
    val reservoirId: String,
    val species: Map<FishSpecies, SpeciesStatus> = emptyMap(),
    /** Lo que dice el usuario: true sí, false no (abastecimiento, plan del coto…), null sin saber. */
    val groundbaitAllowed: Boolean? = null,
    /** Lo que dice la IA sobre el uso de abastecimiento (sin verificar). */
    val aiWaterSupply: Boolean? = null,
    val summary: String? = null,
    val notes: List<SheetNote> = emptyList(),
    val aiGeneratedAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    /** Especies presentes (confirmadas o propuestas por la IA y no descartadas). */
    val presentSpecies: Set<FishSpecies> get() = species.filterValues { it.present }.keys

    val hasUnconfirmed: Boolean get() = species.values.any { !it.confirmedByUser }
}

/** Embalse activo con su ficha. */
data class ReservoirContext(val reservoir: Reservoir, val sheet: ReservoirSheet?) {
    val isBrovales: Boolean get() = reservoir.id == Reservoirs.BROVALES_ID

    /**
     * ¿Se puede cebar? Lo que dice el usuario manda; si no dice nada y el nombre oficial es de
     * abastecimiento, no (⚖ §7). Null = sin saber (la app avisa de confirmarlo).
     */
    val groundbaitAllowed: Boolean?
        get() = sheet?.groundbaitAllowed ?: if (reservoir.waterSupplyByName) false else null

    /** Posible abastecimiento (IA o nombre) sin respuesta del usuario: hay que confirmarlo. */
    val groundbaitDoubt: Boolean get() = sheet?.groundbaitAllowed == null && (reservoir.waterSupplyByName || sheet?.aiWaterSupply == true)

    val species: Set<FishSpecies> get() = sheet?.presentSpecies.orEmpty()
}

/** Operaciones con embalses. Funciones puras. */
object Reservoirs {
    const val BROVALES_ID = "brovales"

    /** Una ubicación a menos de esto de un embalse se considera ese embalse. */
    const val MATCH_KM = 3.0

    fun nearest(point: GeoPoint, all: List<Reservoir>, maxKm: Double = MATCH_KM): Reservoir? =
        all.map { it to Geo.haversineKm(point, it.point) }.filter { it.second <= maxKm }.minByOrNull { it.second }?.first

    /** El embalse de una sesión, por su ubicación. */
    fun of(session: Session, all: List<Reservoir>): Reservoir? = nearest(session.location, all)

    /** Sesiones pescadas en un embalse. */
    fun sessionsAt(sessions: List<Session>, reservoir: Reservoir, all: List<Reservoir>): List<Session> =
        sessions.filter { of(it, all)?.id == reservoir.id }

    /** Busca por nombre, municipio o coto (sin tildes ni mayúsculas); vacío = todos, por cercanía. */
    fun search(all: List<Reservoir>, query: String, near: GeoPoint?): List<Reservoir> {
        val q = normalize(query)
        val found = if (q.isBlank()) all else all.filter { r -> listOfNotNull(r.name, r.municipality, r.coto).any { normalize(it).contains(q) } }
        return if (near != null) found.sortedBy { Geo.haversineKm(near, it.point) } else found.sortedBy { normalize(it.name) }
    }

    fun normalize(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]+"), "").lowercase().trim()

    /** Embalse añadido por el usuario con el GPS o con coordenadas. */
    fun custom(name: String, point: GeoPoint, idSuffix: String): Reservoir =
        Reservoir(id = "propio-$idSuffix", name = name.trim(), point = point, custom = true)
}

/** Cambios del usuario en una ficha. Funciones puras. */
object ReservoirSheets {
    /** Confirma que una especie está (true) o no (false); null la quita de la ficha. */
    fun setSpecies(sheet: ReservoirSheet?, reservoirId: String, species: FishSpecies, present: Boolean?, now: Instant): ReservoirSheet {
        val base = sheet ?: ReservoirSheet(reservoirId)
        val map = if (present == null) base.species - species else base.species + (species to SpeciesStatus(present, confirmedByUser = true))
        return base.copy(species = map, updatedAt = now)
    }

    /** Confirma lo que la IA propuso (las especies quedan como confirmadas). */
    fun confirmAll(sheet: ReservoirSheet, now: Instant): ReservoirSheet =
        sheet.copy(species = sheet.species.mapValues { it.value.copy(confirmedByUser = true) }, updatedAt = now)

    fun setGroundbait(sheet: ReservoirSheet?, reservoirId: String, allowed: Boolean?, now: Instant): ReservoirSheet =
        (sheet ?: ReservoirSheet(reservoirId)).copy(groundbaitAllowed = allowed, updatedAt = now)
}
