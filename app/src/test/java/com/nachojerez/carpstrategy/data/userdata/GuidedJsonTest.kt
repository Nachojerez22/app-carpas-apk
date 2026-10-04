package com.nachojerez.carpstrategy.data.userdata

import com.nachojerez.carpstrategy.domain.assistant.AiExchange
import com.nachojerez.carpstrategy.domain.assistant.AiIssue
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.guided.ActivityPlace
import com.nachojerez.carpstrategy.domain.guided.BaitState
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.ChangedVariable
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.FieldCondition
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.GuidedLog
import com.nachojerez.carpstrategy.domain.guided.GuidedRecord
import com.nachojerez.carpstrategy.domain.guided.HookActivity
import com.nachojerez.carpstrategy.domain.guided.JumpCount
import com.nachojerez.carpstrategy.domain.guided.Proposal
import com.nachojerez.carpstrategy.domain.guided.ProposalSource
import com.nachojerez.carpstrategy.domain.guided.RejectReason
import com.nachojerez.carpstrategy.domain.guided.RigType
import com.nachojerez.carpstrategy.domain.guided.RodTrack
import com.nachojerez.carpstrategy.domain.guided.SignalLevel
import com.nachojerez.carpstrategy.domain.guided.Situation
import com.nachojerez.carpstrategy.domain.guided.Species
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.SpotStructure
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.guided.WeatherQuestion
import com.nachojerez.carpstrategy.domain.guided.WeatherSnapshot
import com.nachojerez.carpstrategy.domain.journal.FishingZone
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
            .withCheckIn(CheckIn(t0.plusSeconds(5500), activity = HookActivity.CATCH, species = Species.BLACK_BASS, rebait = GroundbaitLevel.LOW))
        val record = GuidedRecord(
            rods = listOf(RodTrack(1, "fija", log), RodTrack(2, "carrete", GuidedLog.start(t0, a))),
            alarms = listOf(t0.plusSeconds(3600)),
            windChanges = listOf(t0.plusSeconds(4000)),
            groundbait = GroundbaitLevel.HIGH,
        ).withCondition(t0.plusSeconds(4100), FieldCondition.STORM, true)
            .withCondition(t0.plusSeconds(4200), FieldCondition.HEAVY_RAIN, true)
            .withCondition(t0.plusSeconds(6000), FieldCondition.STORM, false)
            .finish(t0.plusSeconds(7200))
        val text = GuidedJson.encode(record)
        assertEquals(record, GuidedJson.decode(text))
        assertTrue(text.contains("\"canas\""))
        assertTrue(text.contains("\"BLACK_BASS\""))
    }

    @Test
    fun `puesto y tiempo de cada aviso van con la sesion`() {
        val a = Proposal(StepKind.INITIAL, Situation.START, BaitType.MAIZE, evidence = Evidence.YELLOW, createdAt = t0)
        val spot = Spot(3, "Punta del cauce", FishingZone.WEST, SpotStructure.OLD_CHANNEL, 4.5, 60.0, 270, "fondo duro")
        val weather = WeatherSnapshot(
            time = t0, airC = 21.5, windKmh = 12.0, windFromDeg = 250.0, gustKmh = 30.0, cloudPct = 80.0, precipitationMm = 0.4,
            weatherCode = 95, pressureHpa = 1012.0, waterC = 22.1, waterMeasured = true,
            sunset = t0.plusSeconds(50_000), legalEnd = t0.plusSeconds(53_600),
        )
        val record = GuidedRecord(rods = listOf(RodTrack(1, "", GuidedLog.start(t0, a))), spot = spot)
            .withWeather(weather)
            .withWeatherAnswer(WeatherQuestion.STORM, false)
            .withWeather(WeatherSnapshot(time = t0.plusSeconds(1800)))
        val text = GuidedJson.encode(record)
        assertEquals(record, GuidedJson.decode(text))
        assertTrue(text.contains("\"puesto\""))
        assertTrue(text.contains("\"codigo_tiempo\":95"))
        assertTrue(text.contains("\"tormenta_usuario\":false"))
    }

    @Test
    fun `consultas a la IA, propuestas de la IA y nuevas preguntas del aviso`() {
        val a = Proposal(StepKind.INITIAL, Situation.START, BaitType.MAIZE, evidence = Evidence.YELLOW, createdAt = t0)
        val ai = Proposal(
            StepKind.ZONE, Situation.ASSISTANT, evidence = Evidence.PURPLE, createdAt = t0.plusSeconds(1800),
            source = ProposalSource.AI, note = "Cambia a la punta — viento de cara", spotName = "Punta",
        )
        val log = GuidedLog.start(t0, a).accept(t0)
            .withCheckIn(CheckIn(t0.plusSeconds(1700), SignalLevel.DIRECT, seenAt = ActivityPlace.SURFACE, jumps = JumpCount.FEW))
            .withProposal(ai)
        val record = GuidedRecord(
            rods = listOf(RodTrack(1, "", log)),
            ai = listOf(
                AiExchange(t0.plusSeconds(1800), AiKind.CHECK_IN, "GEMINI", "gemini-x", "{…}", true, emptyList(), null, "CAMBIAR 1:ZONE", "viento de cara"),
                AiExchange(t0.plusSeconds(3600), AiKind.CHECK_IN, "GEMINI", "gemini-x", "{…}", false, listOf(AiIssue.GRAMS, AiIssue.NIGHT)),
                AiExchange(t0.plusSeconds(5400), AiKind.CHECK_IN, "GEMINI", "gemini-x", error = "Quota"),
            ),
        )
        val text = GuidedJson.encode(record)
        assertEquals(record, GuidedJson.decode(text))
        assertTrue(text.contains("\"origen\":\"AI\""))
        assertTrue(text.contains("\"saltos\":\"FEW\""))
        assertTrue(text.contains("\"problemas\":[\"GRAMS\",\"NIGHT\"]"))
        assertEquals(record.ai.first(), GuidedJson.decodeAi(GuidedJson.encodeAi(record.ai.first())))
    }

    @Test
    fun `mis puestos ida y vuelta y los invalidos se descartan`() {
        val spots = listOf(
            Spot(1, "Recula norte", FishingZone.NORTH, SpotStructure.INLET_BAY, 1.5, 30.0, 180, ""),
            Spot(2, "Llano", structure = SpotStructure.FLAT),
        )
        assertEquals(spots, GuidedJson.decodeSpots(GuidedJson.encodeSpots(spots)))
        val text = """[{"id":1,"nombre":"  "},{"id":2,"nombre":"Punta","estructura":"DESCONOCIDA","orientacion_grados":-90}]"""
        assertEquals(listOf(Spot(2, "Punta", facingDeg = 270)), GuidedJson.decodeSpots(text))
        assertEquals(emptyList<Spot>(), GuidedJson.decodeSpots("no es json"))
        assertEquals(emptyList<Spot>(), GuidedJson.decodeSpots(null))
    }

    @Test
    fun `formato anterior de una sola cana se sigue leyendo`() {
        val old = """{"tramos":[{"inicio":"2026-07-15T05:00:00Z","paso":"INITIAL","cebo":"MAIZE"}],"propuestas":[]}"""
        val record = GuidedJson.decode(old)!!
        assertEquals(1, record.rods.size)
        assertEquals(BaitType.MAIZE, record.rods.single().log.current.bait)
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
