package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.reservoir.Reservoirs
import java.time.Instant

/** Un cebo usado en el diario: en cuántas sesiones, la última vez y su tipo (si se sabe). */
data class BaitUsage(val name: String, val sessions: Int, val lastUsed: Instant, val baitType: BaitType? = null)

/** «Hoy llevo» y «Mis cebos guardados» (fase 7.13): tus cebos repartidos y ordenados. */
data class BaitShelfView(
    val today: List<GearItem>,
    /** Los que no llevas hoy: primero los que usaste más recientemente, luego por nombre. */
    val saved: List<GearItem>,
    /** Cebos del diario que no están en tu lista (para guardarlos con un toque). */
    val fromDiary: List<BaitUsage>,
    /** Uso de cada cebo de tu lista (por id), para mostrarlo. */
    val usageById: Map<String, BaitUsage>,
)

/**
 * Tus cebos guardados frente a lo que llevas hoy, y lo que ya usaste según el diario (sesiones
 * guiadas por tramos de cada caña o el campo «cebo» de las anotadas a mano). Funciones puras.
 */
object BaitShelf {
    /** Separadores de varios cebos en el campo «cebo» de una sesión anotada a mano. */
    private val SEPARATORS = Regex("""[,;+/]""")

    fun usage(sessions: List<Session>): List<BaitUsage> {
        data class Use(val name: String, val sessionId: Long, val at: Instant, val type: BaitType?)
        val uses = Experience.units(sessions).flatMap { u ->
            u.baitName.orEmpty().split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }.map { Use(it, u.sessionId, u.start, u.baitType) }
        }
        return uses.groupBy { Reservoirs.normalize(it.name) }.values.map { group ->
            val latest = group.maxBy { it.at }
            BaitUsage(
                name = latest.name,
                sessions = group.map { it.sessionId }.distinct().size,
                lastUsed = latest.at,
                baitType = group.mapNotNull { it.type }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
            )
        }.sortedByDescending { it.lastUsed }
    }

    fun view(gear: List<GearItem>, usage: List<BaitUsage>): BaitShelfView {
        val baits = gear.filter { it.category == GearCategory.BAIT }
        val byName = usage.associateBy { Reservoirs.normalize(it.name) }
        fun usageOf(item: GearItem) = byName[Reservoirs.normalize(item.name)] ?: byName[Reservoirs.normalize(item.fullName)]
        val usageById = baits.mapNotNull { item -> usageOf(item)?.let { item.id to it } }.toMap()
        val known = baits.flatMap { listOf(Reservoirs.normalize(it.name), Reservoirs.normalize(it.fullName)) }.toSet()
        return BaitShelfView(
            today = baits.filter { it.available },
            saved = baits.filterNot { it.available }.sortedWith(
                compareByDescending<GearItem> { usageById[it.id]?.lastUsed ?: Instant.MIN }
                    .thenByDescending { usageById[it.id]?.sessions ?: 0 }
                    .thenBy { Reservoirs.normalize(it.fullName) },
            ),
            fromDiary = usage.filter { Reservoirs.normalize(it.name) !in known },
            usageById = usageById,
        )
    }

    /** «Empezar de cero hoy»: ningún cebo marcado (los montajes no cambian). */
    fun clearToday(gear: List<GearItem>): List<GearItem> = gear.map { if (it.category == GearCategory.BAIT) it.copy(available = false) else it }

    /** Un cebo del diario pasa a tu lista y a lo que llevas hoy. */
    fun fromUsage(usage: BaitUsage, id: String): GearItem =
        GearItem(id, GearCategory.BAIT, usage.name.trim(), baitType = usage.baitType ?: BaitType.OTHER, available = true)
}
