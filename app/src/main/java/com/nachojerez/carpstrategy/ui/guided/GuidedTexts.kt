package com.nachojerez.carpstrategy.ui.guided

import android.content.res.Resources
import androidx.annotation.StringRes
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.guided.BaitState
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.ChangedVariable
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.Decision
import com.nachojerez.carpstrategy.domain.guided.FieldCondition
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.GuidedMessage
import com.nachojerez.carpstrategy.domain.guided.HookActivity
import com.nachojerez.carpstrategy.domain.guided.Proposal
import com.nachojerez.carpstrategy.domain.guided.RejectReason
import com.nachojerez.carpstrategy.domain.guided.RigType
import com.nachojerez.carpstrategy.domain.guided.SignalLevel
import com.nachojerez.carpstrategy.domain.guided.Species
import com.nachojerez.carpstrategy.domain.guided.Situation
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.rules.Evidence

// Textos de la sesión guiada. Se usan con Resources para servir a la pantalla y a la notificación.

@StringRes
fun FishingPhase.titleRes(): Int = when (this) {
    FishingPhase.WINTER -> R.string.guided_phase_winter
    FishingPhase.SPRING -> R.string.guided_phase_spring
    FishingPhase.SUMMER -> R.string.guided_phase_summer
    FishingPhase.HEAT -> R.string.guided_phase_heat
    FishingPhase.AUTUMN -> R.string.guided_phase_autumn
}

@StringRes
fun Column.titleRes(): Int = when (this) {
    Column.BOTTOM -> R.string.column_bottom
    Column.POPUP -> R.string.column_popup
    Column.ZIG -> R.string.column_zig
    Column.SURFACE -> R.string.column_surface
}

@StringRes
fun BaitType.titleRes(): Int = when (this) {
    BaitType.BOILIE -> R.string.bait_boilie
    BaitType.BOILIE_HARD -> R.string.bait_boilie_hard
    BaitType.PELLET -> R.string.bait_pellet
    BaitType.MAIZE -> R.string.bait_maize
    BaitType.HEMP -> R.string.bait_hemp
    BaitType.TIGERNUT -> R.string.bait_tigernut
    BaitType.PASTE -> R.string.bait_paste
    BaitType.BREAD -> R.string.bait_bread
    BaitType.POPUP -> R.string.bait_popup
    BaitType.PVA -> R.string.bait_pva
    BaitType.WORM -> R.string.bait_worm
    BaitType.OTHER -> R.string.bait_other
}

@StringRes
fun RigType.titleRes(): Int = when (this) {
    RigType.HAIR_BOTTOM -> R.string.rig_hair_bottom
    RigType.POPUP -> R.string.rig_popup
    RigType.ZIG -> R.string.rig_zig
    RigType.SURFACE -> R.string.rig_surface
    RigType.METHOD -> R.string.rig_method
    RigType.OTHER -> R.string.rig_other
}

@StringRes
fun RejectReason.titleRes(): Int = when (this) {
    RejectReason.NO_BAIT -> R.string.guided_reason_no_bait
    RejectReason.NOT_CONVINCED -> R.string.guided_reason_not_convinced
    RejectReason.ALREADY_TRIED -> R.string.guided_reason_already_tried
    RejectReason.CONDITIONS_DIFFER -> R.string.guided_reason_conditions
    RejectReason.OTHER -> R.string.guided_reason_other
}

@StringRes
fun Decision.titleRes(): Int = when (this) {
    Decision.PENDING -> R.string.guided_decision_pending
    Decision.ACCEPTED -> R.string.guided_decision_accepted
    Decision.REJECTED -> R.string.guided_decision_rejected
}

@StringRes
fun SignalLevel.titleRes(): Int = when (this) {
    SignalLevel.NONE -> R.string.guided_signal_none
    SignalLevel.INDIRECT -> R.string.guided_signal_indirect
    SignalLevel.DIRECT -> R.string.guided_signal_direct
}

@StringRes
fun HookActivity.titleRes(): Int = when (this) {
    HookActivity.NOTHING -> R.string.guided_activity_nothing
    HookActivity.TOUCHES -> R.string.guided_activity_touches
    HookActivity.MISSED -> R.string.guided_activity_missed
    HookActivity.CATCH -> R.string.guided_activity_catch
}

@StringRes
fun BaitState.titleRes(): Int = when (this) {
    BaitState.NOT_CHECKED -> R.string.guided_bait_not_checked
    BaitState.INTACT -> R.string.guided_bait_intact
    BaitState.NIBBLED -> R.string.guided_bait_nibbled
    BaitState.GONE -> R.string.guided_bait_gone
}

@StringRes
fun ChangedVariable.titleRes(): Int = when (this) {
    ChangedVariable.BAIT -> R.string.guided_change_bait
    ChangedVariable.RIG -> R.string.guided_change_rig
    ChangedVariable.COLUMN -> R.string.guided_change_column
    ChangedVariable.DISTANCE -> R.string.guided_change_distance
    ChangedVariable.ZONE -> R.string.guided_change_zone
}

@StringRes
fun Situation.titleRes(): Int = when (this) {
    Situation.START -> R.string.guided_situation_start
    Situation.NO_SIGNALS -> R.string.guided_situation_no_signals
    Situation.SIGNALS_NO_BITES -> R.string.guided_situation_signals
    Situation.TOUCHES -> R.string.guided_situation_touches
    Situation.CRAB_OR_SMALL_FISH -> R.string.guided_situation_crab
    Situation.OTHER_FISH -> R.string.guided_situation_other_fish
}

@StringRes
fun Species.titleRes(): Int = when (this) {
    Species.CARP -> R.string.species_carp
    Species.BARBEL -> R.string.species_barbel
    Species.NASE -> R.string.species_nase
    Species.BLACK_BASS -> R.string.species_black_bass
    Species.SMALL -> R.string.species_small
}

@StringRes
fun GroundbaitLevel.titleRes(): Int = when (this) {
    GroundbaitLevel.LOW -> R.string.groundbait_low
    GroundbaitLevel.NORMAL -> R.string.groundbait_normal
    GroundbaitLevel.HIGH -> R.string.groundbait_high
}

@StringRes
fun GuidedMessage.titleRes(): Int = when (this) {
    GuidedMessage.KEEP_AFTER_CATCH -> R.string.guided_msg_keep
    GuidedMessage.WINTER_PATIENCE -> R.string.guided_msg_winter
    GuidedMessage.FORCED_IN_WINTER -> R.string.guided_msg_forced_winter
    GuidedMessage.LEGAL_END_SOON -> R.string.guided_msg_legal_end
    GuidedMessage.NO_ZONE_CHANGES_LEGAL -> R.string.guided_msg_no_zone
    GuidedMessage.ZONE_LIMIT_REACHED -> R.string.guided_msg_zone_limit
    GuidedMessage.NO_EQUIVALENT_BAIT -> R.string.guided_msg_no_equivalent
    GuidedMessage.PREPARE_BAIT -> R.string.guided_msg_prepare
    GuidedMessage.REDUCE_GROUNDBAIT -> R.string.guided_msg_reduce_groundbait
    GuidedMessage.WINTER_GROUNDBAIT -> R.string.guided_msg_winter_groundbait
    GuidedMessage.WIND_CHANGED -> R.string.guided_msg_wind
    GuidedMessage.STORM_SAFETY -> R.string.guided_msg_storm
    GuidedMessage.LIGHT_RAIN_KEEP -> R.string.guided_msg_light_rain
    GuidedMessage.HEAVY_RAIN_WATCH -> R.string.guided_msg_heavy_rain
    GuidedMessage.INFLOW_EDGE -> R.string.guided_msg_inflow_edge
    GuidedMessage.INFLOW_SUMMER_STORM -> R.string.guided_msg_inflow_summer_storm
    GuidedMessage.INFLOW_WINTER_AVOID -> R.string.guided_msg_inflow_winter
}

@StringRes
fun FieldCondition.titleRes(): Int = when (this) {
    FieldCondition.LIGHT_RAIN -> R.string.condition_light_rain
    FieldCondition.HEAVY_RAIN -> R.string.condition_heavy_rain
    FieldCondition.STORM -> R.string.condition_storm
    FieldCondition.MUDDY_INFLOW -> R.string.condition_muddy_inflow
}

/** Etiqueta de evidencia de cada aviso (§5.9). */
val GuidedMessage.evidence: Evidence
    get() = when (this) {
        GuidedMessage.KEEP_AFTER_CATCH, GuidedMessage.WINTER_GROUNDBAIT, GuidedMessage.STORM_SAFETY -> Evidence.GREEN
        GuidedMessage.WINTER_PATIENCE, GuidedMessage.FORCED_IN_WINTER, GuidedMessage.ZONE_LIMIT_REACHED, GuidedMessage.NO_EQUIVALENT_BAIT,
        GuidedMessage.INFLOW_EDGE, GuidedMessage.INFLOW_SUMMER_STORM -> Evidence.PURPLE
        GuidedMessage.LEGAL_END_SOON, GuidedMessage.NO_ZONE_CHANGES_LEGAL -> Evidence.REGULATION
        GuidedMessage.PREPARE_BAIT, GuidedMessage.REDUCE_GROUNDBAIT, GuidedMessage.WIND_CHANGED,
        GuidedMessage.LIGHT_RAIN_KEEP, GuidedMessage.HEAVY_RAIN_WATCH, GuidedMessage.INFLOW_WINTER_AVOID -> Evidence.YELLOW
    }

/** «Boilie fresa 20 (boilie)» si es del equipo; si no, solo el tipo. */
fun Resources.baitText(type: BaitType?, name: String?): String? {
    val typeText = type?.let { getString(it.titleRes()) }
    return when {
        name != null && typeText != null -> "$name (${typeText.lowercase()})"
        else -> name ?: typeText?.lowercase()
    }
}

/** Qué hacer, en una frase. */
fun Resources.proposalText(p: Proposal, phase: FishingPhase): String {
    val bait = baitText(p.bait, p.baitName).orEmpty()
    return when (p.kind) {
        StepKind.INITIAL -> getString(R.string.guided_step_initial, bait)
        StepKind.PRESENTATION -> getString(R.string.guided_step_presentation, bait)
        StepKind.RIG -> getString(R.string.guided_step_rig, bait)
        StepKind.COLUMN -> getString(R.string.guided_step_column, getString((p.column ?: Column.POPUP).titleRes()))
        StepKind.ANTI_CRAB -> getString(R.string.guided_step_anti_crab, bait)
        StepKind.SELECTIVE -> getString(R.string.guided_step_selective, bait)
        StepKind.INFLOW -> getString(R.string.guided_step_inflow)
        StepKind.DISTANCE -> getString(
            when (phase) {
                FishingPhase.WINTER -> R.string.guided_step_distance_winter
                FishingPhase.SPRING -> R.string.guided_step_distance_spring
                FishingPhase.SUMMER, FishingPhase.HEAT -> R.string.guided_step_distance_summer
                FishingPhase.AUTUMN -> R.string.guided_step_distance_autumn
            },
        )
        StepKind.ZONE -> getString(
            when (phase) {
                FishingPhase.WINTER -> R.string.guided_step_zone_winter
                FishingPhase.SPRING -> R.string.guided_step_zone_spring
                FishingPhase.SUMMER -> R.string.guided_step_zone_summer
                FishingPhase.HEAT -> R.string.guided_step_zone_heat
                FishingPhase.AUTUMN -> R.string.guided_step_zone_autumn
            },
        )
    }
}

/** Por qué se propone (el diagnóstico). */
fun Resources.situationText(p: Proposal, phase: FishingPhase): String =
    if (p.situation == Situation.START) getString(R.string.guided_situation_start, getString(phase.titleRes())) else getString(p.situation.titleRes())

/** Etiqueta corta del paso para el historial. */
fun Resources.stepShort(kind: StepKind): String = getString(
    when (kind) {
        StepKind.INITIAL -> R.string.guided_plan_title
        StepKind.PRESENTATION, StepKind.ANTI_CRAB, StepKind.SELECTIVE -> R.string.guided_change_bait
        StepKind.RIG -> R.string.guided_change_rig
        StepKind.COLUMN -> R.string.guided_change_column
        StepKind.DISTANCE -> R.string.guided_change_distance
        StepKind.ZONE, StepKind.INFLOW -> R.string.guided_change_zone
    },
)

/** Nombre de la caña para mostrar: el que puso el usuario o «Caña N». */
fun Resources.rodLabel(id: Int, name: String): String = name.ifBlank { getString(R.string.guided_rod_default, id) }

/** «fija: » delante de una propuesta cuando hay varias cañas. */
fun Resources.rodPrefix(name: String, show: Boolean): String = if (show && name.isNotBlank()) "$name: " else ""
