package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GuidedEngine
import com.nachojerez.carpstrategy.domain.guided.GuidedMessage
import com.nachojerez.carpstrategy.domain.guided.GuidedSessions
import com.nachojerez.carpstrategy.domain.journal.Session
import java.time.Instant
import java.time.ZoneId

data class GuidedUiState(
    val isLoading: Boolean = true,
    /** Sesión guiada en curso; null si no hay ninguna. */
    val session: Session? = null,
    val phase: FishingPhase? = null,
    val legalEnd: Instant? = null,
    val messages: Set<GuidedMessage> = emptySet(),
    val nextCheckIn: Instant? = null,
    val baits: Int = 0,
    val rigs: Int = 0,
    val now: Instant = Instant.EPOCH,
    val starting: Boolean = false,
    /** Última respuesta anotada desde la pantalla (para confirmarlo). */
    val lastSaved: Instant? = null,
    val canNotify: Boolean = true,
    val exactAlarms: Boolean = true,
)

/** Estado de la pantalla. Función pura: los avisos salen de evaluar el registro ahora. */
fun buildGuidedState(session: Session?, gear: List<GearItem>, now: Instant, nextCheckIn: Instant?, zone: ZoneId): GuidedUiState {
    val base = GuidedUiState(
        isLoading = false,
        baits = gear.count { it.category == GearCategory.BAIT },
        rigs = gear.count { it.category == GearCategory.RIG },
        now = now,
    )
    val log = session?.guided ?: return base
    val ctx = GuidedSessions.context(session, now, gear, zone)
    return base.copy(
        session = session,
        phase = ctx.phase,
        legalEnd = ctx.legalEnd,
        messages = GuidedEngine.evaluate(log, ctx).messages,
        nextCheckIn = nextCheckIn,
    )
}
