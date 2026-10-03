package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.GuidedEnv
import com.nachojerez.carpstrategy.domain.guided.GuidedMessage
import com.nachojerez.carpstrategy.domain.guided.GuidedSessions
import com.nachojerez.carpstrategy.domain.guided.HookActivity
import com.nachojerez.carpstrategy.domain.guided.RigType
import com.nachojerez.carpstrategy.domain.guided.Species
import com.nachojerez.carpstrategy.domain.journal.FeatureSnapshot
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.journal.SessionContext
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuidedUiStateTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val t0 = Instant.parse("2026-01-14T09:00:00Z")
    private val gear = listOf(
        GearItem("1", GearCategory.BAIT, "Maíz", baitType = BaitType.MAIZE),
        GearItem("2", GearCategory.BAIT, "Chufa", baitType = BaitType.TIGERNUT),
        GearItem("3", GearCategory.RIG, "Pelo", rigType = RigType.HAIR_BOTTOM),
    )

    @Test
    fun `sin sesion guiada solo cuenta el equipo`() {
        val state = buildGuidedState(null, gear, t0, null, madrid)
        assertFalse(state.isLoading)
        assertNull(state.session)
        assertEquals(2, state.baits)
        assertEquals(1, state.rigs)
    }

    @Test
    fun `con sesion guiada en invierno calcula fase y avisos`() {
        val winter = Session(
            start = t0,
            location = GeoPoint(38.2, -6.75),
            createdAt = t0,
            context = SessionContext(features = FeatureSnapshot(numbers = mapOf("temp_agua_c" to 8.0), texts = mapOf("estacion" to "invierno"))),
        )
        val started = GuidedSessions.start(winter, listOf("fija", "carrete"), GroundbaitLevel.HIGH, GuidedEnv(t0, gear, madrid)).session
        val state = buildGuidedState(started, gear, t0.plusSeconds(60), t0.plusSeconds(2700), madrid)
        assertEquals(FishingPhase.WINTER, state.phase)
        assertEquals(t0.plusSeconds(2700), state.nextCheckIn)
        assertEquals(listOf("fija", "carrete"), state.rods.map { it.name })
        // Mucho cebado en invierno: aviso 🟢 de cebado mínimo.
        assertTrue(GuidedMessage.WINTER_GROUNDBAIT in state.messages)
        assertFalse(GuidedMessage.LEGAL_END_SOON in state.messages)
        assertTrue(state.legalEnd!!.isAfter(t0))
    }

    @Test
    fun `borrador del equipo valida y sustituye por id`() {
        assertFalse(GearDraft(category = GearCategory.BAIT, name = "Maíz").isValid)
        val draft = GearDraft(category = GearCategory.BAIT, name = "  Maíz dulce ", baitType = BaitType.MAIZE, rigType = RigType.ZIG)
        val item = draft.toItem { "nuevo" }
        assertEquals(GearItem("nuevo", GearCategory.BAIT, "Maíz dulce", baitType = BaitType.MAIZE), item)
        val edited = GearDraft.of(gear[0]).copy(name = "Maíz duro").toItem { error("no debe crear id") }
        assertEquals(listOf("Maíz duro", "Chufa", "Pelo"), gear.upsert(edited).map { it.name })
        assertEquals(4, gear.upsert(item).size)
    }

    @Test
    fun `borrador de aviso - la especie solo cuenta en capturas y los nombres de cana`() {
        val carp = CheckInDraft(activity = HookActivity.CATCH, species = Species.CARP).toCheckIn(t0)
        assertTrue(carp.isCarpCatch)
        assertNull(carp.species)
        val barbel = CheckInDraft(activity = HookActivity.CATCH, species = Species.BARBEL).toCheckIn(t0)
        assertTrue(barbel.isBycatch)
        assertNull(CheckInDraft(activity = HookActivity.TOUCHES, species = Species.BARBEL).toCheckIn(t0).species)
        assertEquals(listOf("fija", ""), rodNamesFor(2, listOf(" fija ", "", "sobra")))
        assertEquals(listOf(""), rodNamesFor(0, emptyList()))
    }
}
