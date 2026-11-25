package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.domain.guided.BaitSheet
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.RigType
import com.nachojerez.carpstrategy.domain.guided.RodKind
import com.nachojerez.carpstrategy.domain.guided.RodRules

/** Formulario de un elemento del equipo (nuevo si [id] es null). */
data class GearDraft(
    val id: String? = null,
    val category: GearCategory,
    val name: String = "",
    val baitType: BaitType? = null,
    val rigType: RigType? = null,
    val brand: String = "",
    val description: String = "",
    val url: String = "",
    val available: Boolean = true,
    /** La ficha de la IA se conserva al editar (no se teclea). */
    val sheet: BaitSheet? = null,
    /** Caña (fase 7.20): tipo y, si es fija, longitud. */
    val rodKind: RodKind? = null,
    val rodLengthM: Int? = null,
    /** Fija con elástico (null = la de 9 m sí) y carpfishing con cebador (§5.9.15). */
    val rodElastic: Boolean? = null,
    val carpFeeder: Boolean = false,
    val hookSize: Int? = null,
) {
    /** Lo que se ve en el interruptor del elástico: lo guardado o, si no, el de siempre por longitud. */
    val elasticShown: Boolean get() = rodElastic ?: (rodLengthM == RodRules.ELASTIC_DEFAULT_LENGTH_M)

    val isValid: Boolean get() = name.isNotBlank() && when (category) {
        GearCategory.BAIT -> baitType != null
        GearCategory.RIG -> rigType != null
        GearCategory.ROD -> rodKind == RodKind.CARP || (rodKind == RodKind.FIXED && rodLengthM != null)
        GearCategory.HOOK -> hookSize != null
    }

    fun toItem(newId: () -> String): GearItem = GearItem(
        id = id ?: newId(),
        category = category,
        name = name.trim(),
        baitType = baitType.takeIf { category == GearCategory.BAIT },
        rigType = rigType.takeIf { category == GearCategory.RIG },
        brand = brand.trim(),
        description = description.trim(),
        url = url.trim(),
        available = available,
        sheet = sheet.takeIf { category == GearCategory.BAIT },
        rodKind = rodKind.takeIf { category == GearCategory.ROD },
        rodLengthM = rodLengthM.takeIf { category == GearCategory.ROD && rodKind == RodKind.FIXED },
        rodElastic = rodElastic.takeIf { category == GearCategory.ROD && rodKind == RodKind.FIXED },
        carpFeeder = carpFeeder && category == GearCategory.ROD && rodKind == RodKind.CARP,
        hookSize = hookSize.takeIf { category == GearCategory.HOOK },
    )

    companion object {
        fun of(item: GearItem) = GearDraft(
            item.id, item.category, item.name, item.baitType, item.rigType, item.brand, item.description, item.url, item.available, item.sheet,
            item.rodKind, item.rodLengthM, item.rodElastic, item.carpFeeder, item.hookSize,
        )
    }
}

/** Añade el anzuelo del número [size] si no está, o lo quita si ya estaba (§5.9.15). */
fun toggleHookSize(gear: List<GearItem>, size: Int, newId: () -> String): List<GearItem> =
    if (gear.any { it.category == GearCategory.HOOK && it.hookSize == size }) {
        gear.filterNot { it.category == GearCategory.HOOK && it.hookSize == size }
    } else {
        gear + GearItem(newId(), GearCategory.HOOK, "Anzuelo del $size", hookSize = size)
    }

/** Sustituye el elemento con el mismo id o lo añade al final. */
fun List<GearItem>.upsert(item: GearItem): List<GearItem> =
    if (any { it.id == item.id }) map { if (it.id == item.id) item else it } else this + item

/**
 * Ficha de la IA aplicada a un cebo: se guarda la ficha y, si el tipo era «Otro» o no tenía, el
 * tipo que propone la IA. Lo que escribió el usuario no se toca.
 */
fun GearItem.withSheet(sheet: BaitSheet): GearItem = copy(
    sheet = sheet,
    baitType = if ((baitType == null || baitType == BaitType.OTHER) && sheet.type != null) sheet.type else baitType,
)
