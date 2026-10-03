package com.nachojerez.carpstrategy.data.userdata

import com.nachojerez.carpstrategy.domain.guided.BaitState
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.ChangedVariable
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GuidedLog
import com.nachojerez.carpstrategy.domain.guided.HookActivity
import com.nachojerez.carpstrategy.domain.guided.Proposal
import com.nachojerez.carpstrategy.domain.guided.RejectReason
import com.nachojerez.carpstrategy.domain.guided.RigType
import com.nachojerez.carpstrategy.domain.guided.SignalLevel
import com.nachojerez.carpstrategy.domain.guided.Situation
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuidedJsonTest {
    private val t0 = Instant.parse("2026-07-15T05:00:00Z")

    @Test
    fun `ida y vuelta del registro guiado`() {
        val a = Proposal(StepKind.INITIAL, Situation.START, BaitType.BOILIE, "Boilie fresa", column = Column.BOTTOM, evidence = Evidence.YELLOW, createdAt = t0)
        val b = Proposal(StepKind.COLUMN, Situation.NO_SIGNALS, column = Column.ZIG, rigName = "Zig 1,5 m", forced = true, evidence = Evidence.PURPLE, createdAt = t0.plusSeconds(3600))
        val log = GuidedLog.start(t0, a).accept(t0)
            .withCheckIn(CheckIn(t0.plusSeconds(1800), SignalLevel.INDIRECT, HookActivity.TOUCHES, BaitState.NIBBLED, notWorking = true, userChange = ChangedVariable.BAIT))
            .withProposal(b).reject(t0.plusSeconds(3700), RejectReason.NO_BAIT, " no llevo ")
            .withProposal(b).accept(t0.plusSeconds(3800))
            .withCheckIn(CheckIn(t0.plusSeconds(5400), activity = HookActivity.CATCH))
            .finish(t0.plusSeconds(7200))
        val back = GuidedJson.decode(GuidedJson.encode(log))
        assertEquals(log, back)
        assertTrue(GuidedJson.encode(log).contains("\"tramos\""))
    }

    @Test
    fun `equipo ida y vuelta e ignora entradas corruptas`() {
        val gear = listOf(
            GearItem("1", GearCategory.BAIT, "Maíz", baitType = BaitType.MAIZE),
            GearItem("2", GearCategory.RIG, "Zig", rigType = RigType.ZIG),
        )
        assertEquals(gear, GuidedJson.decodeGear(GuidedJson.encodeGear(gear)))
        val text = """[{"id":"1","categoria":"XX","nombre":"a"},{"id":"2","categoria":"BAIT","nombre":"b","tipo_cebo":"RARO"}]"""
        assertEquals(listOf(GearItem("2", GearCategory.BAIT, "b")), GuidedJson.decodeGear(text))
    }

    @Test
    fun `texto corrupto o vacío no rompe`() {
        assertNull(GuidedJson.decode(null))
        assertNull(GuidedJson.decode("{no es json"))
        assertNull(GuidedJson.decode("""{"tramos":[]}"""))
        assertEquals(emptyList<GearItem>(), GuidedJson.decodeGear("basura"))
    }
}
