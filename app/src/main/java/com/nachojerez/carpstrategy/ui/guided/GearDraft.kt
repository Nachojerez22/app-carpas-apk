package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.RigType

/** Formulario de un elemento del equipo (nuevo si [id] es null). */
data class GearDraft(
    val id: String? = null,
    val category: GearCategory,
    val name: String = "",
    val baitType: BaitType? = null,
    val rigType: RigType? = null,
) {
    val isValid: Boolean get() = name.isNotBlank() && (if (category == GearCategory.BAIT) baitType != null else rigType != null)

    fun toItem(newId: () -> String): GearItem = GearItem(
        id = id ?: newId(),
        category = category,
        name = name.trim(),
        baitType = baitType.takeIf { category == GearCategory.BAIT },
        rigType = rigType.takeIf { category == GearCategory.RIG },
    )

    companion object {
        fun of(item: GearItem) = GearDraft(item.id, item.category, item.name, item.baitType, item.rigType)
    }
}

/** Sustituye el elemento con el mismo id o lo añade al final. */
fun List<GearItem>.upsert(item: GearItem): List<GearItem> =
    if (any { it.id == item.id }) map { if (it.id == item.id) item else it } else this + item
