package com.nachojerez.carpstrategy.domain.reservoir

import com.nachojerez.carpstrategy.domain.rules.Evidence

/** Qué cambia en la estrategia de la carpa por las especies que la acompañan (§5.11). */
data class SpeciesEffects(
    /** Cebos de anzuelo selectivos (boilie, chufa, pop-up): hay peces que se comen los pequeños. */
    val selective: Boolean = false,
    /** Cebos duros que aguanten (cangrejo, pez gato, carpín). */
    val hardBaits: Boolean = false,
    /** Menos partícula suelta (maíz, cañamón) en el cebado. */
    val lessParticles: Boolean = false,
    /** Montaje y bajo más fuertes (siluro). */
    val strongRig: Boolean = false,
    val notes: List<Pair<FishSpecies, Evidence>> = emptyList(),
)

/** 🟡/🟣 Ajustes por especie (CONOCIMIENTO.md §5.11). Función pura. */
object SpeciesRules {
    fun effects(species: Set<FishSpecies>): SpeciesEffects {
        val notes = mutableListOf<Pair<FishSpecies, Evidence>>()
        var selective = false
        var hard = false
        var lessParticles = false
        var strong = false
        if (FishSpecies.BARBEL in species || FishSpecies.NASE in species) {
            selective = true
            lessParticles = true
            if (FishSpecies.BARBEL in species) notes += FishSpecies.BARBEL to Evidence.YELLOW
            if (FishSpecies.NASE in species) notes += FishSpecies.NASE to Evidence.YELLOW
        }
        if (FishSpecies.CRAYFISH in species) {
            hard = true
            notes += FishSpecies.CRAYFISH to Evidence.YELLOW
        }
        if (FishSpecies.CRUCIAN in species || FishSpecies.BULLHEAD in species) {
            selective = true
            hard = true
            if (FishSpecies.CRUCIAN in species) notes += FishSpecies.CRUCIAN to Evidence.PURPLE
            if (FishSpecies.BULLHEAD in species) notes += FishSpecies.BULLHEAD to Evidence.PURPLE
        }
        if (FishSpecies.TENCH in species) notes += FishSpecies.TENCH to Evidence.YELLOW
        if (FishSpecies.CATFISH in species) {
            strong = true
            notes += FishSpecies.CATFISH to Evidence.PURPLE
        }
        listOf(FishSpecies.BLACK_BASS, FishSpecies.PIKE, FishSpecies.PIKEPERCH).filter { it in species }.forEach { notes += it to Evidence.PURPLE }
        if (FishSpecies.SUNFISH in species || FishSpecies.BLEAK in species) {
            selective = true
            if (FishSpecies.SUNFISH in species) notes += FishSpecies.SUNFISH to Evidence.PURPLE
            if (FishSpecies.BLEAK in species) notes += FishSpecies.BLEAK to Evidence.PURPLE
        }
        return SpeciesEffects(selective, hard, lessParticles, strong, notes)
    }
}
