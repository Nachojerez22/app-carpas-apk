package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.assistant.AiValidator
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.FieldWeather
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GuidedEngine
import com.nachojerez.carpstrategy.domain.guided.Proposal
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.Spots
import com.nachojerez.carpstrategy.domain.guided.WeatherSnapshot
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Lo que se manda para el plan antes de pescar (sale del estado de Estrategia). */
data class PlanInput(
    val now: Instant,
    val zone: ZoneId,
    val rods: Int,
    val band: String?,
    val limitingLevel: String?,
    val legalStart: Instant?,
    val legalEnd: Instant?,
    val windows: List<Triple<Instant, Instant, String>>,
    /** Consejos de las reglas: (campo, texto, evidencia). */
    val advice: List<Triple<String, String, Evidence>>,
    val waterC: Double?,
    val waterMeasured: Boolean,
    val waterTrend3dC: Double?,
    val season: String?,
    val windFromDeg: Double?,
    val windKmh: Double?,
    val rain24hMm: Double?,
    val gear: List<GearItem>,
    val spots: List<Spot>,
)

/**
 * Instrucciones y estado que se mandan a la IA. **Nunca** van la ubicación GPS, la cuenta ni la
 * clave: solo el estado de la pesca. Las normas de CONOCIMIENTO.md van en las instrucciones y
 * además se comprueban al recibir la respuesta ([AiValidator]).
 */
object AssistantPrompts {
    private const val RULES = """
Eres el asistente de pesca de carpa de la app CarpStrategy en el embalse de Brovales (Badajoz).
Normas obligatorias (si no las cumples, la app descarta tu respuesta y usa sus reglas):
1. No prometas peces ni digas dónde están: habla de condiciones más o menos favorables.
2. Sin probabilidades, porcentajes ni niveles de confianza.
3. Sin gramos ni kilos de cebado: di poco, normal o mucho.
4. Solo horario legal (de 1 h antes de la salida del sol a 1 h después de la puesta). Nunca propongas pescar de noche. A menos de 45 min del fin legal no propongas cambios de zona (ZONE ni INFLOW).
5. Usa solo cebos y montajes de "equipo" y puestos de "mis_puestos", con su nombre exacto. No inventes profundidades, zonas ni estructuras: la batimetría de Brovales no está verificada.
6. Como mucho un cambio por caña (una sola variable cada vez). Tipos permitidos: PRESENTATION, RIG, COLUMN, DISTANCE, ZONE, ANTI_CRAB, SELECTIVE, INFLOW.
7. Presión atmosférica, nubosidad y luna pesan 0: no las uses como motivo.
8. Con tormenta activa: MANTENER y recuerda dejar las cañas y ponerse a cubierto.
9. Tras una captura de carpa, mantener. En invierno, paciencia: cambia menos. Lo que el usuario anota (lo que ve en el puesto) manda sobre la previsión.
10. Cada motivo lleva etiqueta de evidencia en "evidencia": 🟢 demostrado, 🟡 indicio o experiencia, 🔴 sin evidencia, 🟣 hipótesis local, 🔵 variabilidad individual, ⚖ normativa.
11. Responde en español, breve (cada texto como mucho 300 caracteres) y SOLO con el JSON pedido, sin texto alrededor.
"""

    val CHECK_IN_SYSTEM = RULES + """
Tarea: en este aviso de la sesión guiada, decide MANTENER o CAMBIAR. Responde con este JSON:
{"decision":"MANTENER" o "CAMBIAR","cambios":[{"cana":1,"tipo":"COLUMN","accion":"qué hacer","cebo":"nombre exacto o null","montaje":"nombre exacto o null","columna":"BOTTOM|POPUP|ZIG|SURFACE o null","puesto":"nombre exacto o null","motivo":"por qué","evidencia":"🟡"}],"motivos":["…"],"evidencia":"🟡"}
Con MANTENER, "cambios" va vacío. No propongas cambios en cañas con "propuesta_pendiente".
"""

    val PLAN_SYSTEM = RULES + """
Tarea: plan antes de pescar para hoy con las cañas indicadas. Responde con este JSON:
{"resumen":"…","evidencia":"🟡","canas":[{"cana":1,"puesto":"nombre exacto o null","cebo":"nombre exacto o null","montaje":"nombre exacto o null","columna":"BOTTOM|POPUP|ZIG|SURFACE o null","distancia_m":40 o null,"motivo":"…","evidencia":"🟡"}],"avisos":[{"texto":"…","evidencia":"⚖"}]}
Respeta las ventanas legales y los consejos de las reglas; si no hay datos, dilo.
"""

    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

    private fun minutes(from: Instant, to: Instant?): Long? = to?.let { Duration.between(from, it).toMinutes() }

    /** Estado de la sesión en un aviso. */
    fun checkInState(
        session: Session,
        phase: FishingPhase,
        legalEnd: Instant?,
        now: Instant,
        zone: ZoneId,
        gear: List<GearItem>,
        spots: List<Spot>,
    ): String {
        val record = session.guided
        val report = record?.let { FieldWeather.report(it, now) }
        return buildJsonObject {
            put("momento", "aviso")
            put("hora_local", CLOCK.format(now.atZone(zone)))
            put("minutos_de_sesion", Duration.between(session.start, now).toMinutes())
            minutes(now, legalEnd)?.let { put("minutos_hasta_fin_legal", it) }
            put("fase", phase.name)
            put("zona_permitida_por_horario", legalEnd == null || Duration.between(now, legalEnd) > GuidedEngine.LEGAL_NO_ZONE)
            report?.let { r ->
                putJsonObject("tiempo_prevision") {
                    weather(r.current)
                    putJsonArray("cambios_desde_ultimo_aviso") { r.sinceLast.forEach { add(it.kind.name) } }
                    putJsonArray("cambios_desde_inicio") { r.sinceStart.forEach { add(it.kind.name) } }
                    r.wind?.let { put("viento_en_el_puesto", it.name) }
                }
            }
            putJsonArray("condiciones_anotadas_por_el_usuario") { record?.activeConditions(now)?.forEach { add(it.name) } }
            record?.groundbait?.let { put("cebado_inicial", it.name) }
            record?.spot?.let { s -> putJsonObject("puesto_actual") { spot(s, report?.current) } }
            putJsonArray("mis_puestos") { spots.forEach { s -> addJsonObject { spot(s, report?.current) } } }
            gear(gear)
            putJsonArray("canas") {
                record?.rods?.forEach { rod ->
                    addJsonObject {
                        val seg = rod.log.current
                        put("cana", rod.id)
                        if (rod.name.isNotBlank()) put("nombre", rod.name)
                        putJsonObject("configuracion") {
                            seg.baitName?.let { put("cebo", it) } ?: seg.bait?.let { put("tipo_cebo", it.name) }
                            seg.rigName?.let { put("montaje", it) }
                            seg.column?.let { put("columna", it.name) }
                            put("paso", seg.kind.name)
                            put("minutos_con_esta_configuracion", Duration.between(seg.start, now).toMinutes())
                        }
                        put("cambios_de_zona", rod.log.zoneChanges)
                        put("picadas_carpa", rod.log.segments.sumOf { it.bites })
                        put("carpas", rod.log.segments.sumOf { it.catches })
                        put("otras_especies", rod.log.segments.sumOf { it.bycatch })
                        rod.log.pending?.let { p -> putJsonObject("propuesta_pendiente") { proposal(p.proposal) } }
                        putJsonArray("ultimos_avisos") {
                            rod.log.allCheckIns.takeLast(MAX_CHECK_INS).forEach { c -> addJsonObject { checkIn(c, now) } }
                        }
                    }
                }
            }
        }.toString()
    }

    /** Estado para el plan antes de pescar. */
    fun planState(input: PlanInput): String = buildJsonObject {
        put("momento", "plan_antes_de_pescar")
        put("hora_local", CLOCK.format(input.now.atZone(input.zone)))
        put("numero_de_canas", input.rods)
        input.band?.let { put("valoracion", it) }
        input.limitingLevel?.let { put("nivel_limitante", it) }
        input.legalStart?.let { put("inicio_legal", CLOCK.format(it.atZone(input.zone))) }
        input.legalEnd?.let { put("fin_legal", CLOCK.format(it.atZone(input.zone))) }
        putJsonArray("ventanas_sugeridas") {
            input.windows.forEach { (start, end, kind) ->
                addJsonObject {
                    put("desde", CLOCK.format(start.atZone(input.zone)))
                    put("hasta", CLOCK.format(end.atZone(input.zone)))
                    put("tipo", kind)
                }
            }
        }
        putJsonArray("consejos_de_las_reglas") {
            input.advice.forEach { (field, text, evidence) ->
                addJsonObject {
                    put("campo", field)
                    put("texto", text)
                    put("evidencia", evidence.name)
                }
            }
        }
        putJsonObject("condiciones") {
            input.waterC?.let { put("agua_c", it) }
            put("agua_medida", input.waterMeasured)
            input.waterTrend3dC?.let { put("tendencia_agua_3d_c", it) }
            input.season?.let { put("estacion", it) }
            input.windFromDeg?.let { put("viento_24h_de_grados", it) }
            input.windKmh?.let { put("viento_24h_kmh", it) }
            input.rain24hMm?.let { put("lluvia_24h_mm", it) }
        }
        val wind = WeatherSnapshot(time = input.now, windKmh = input.windKmh, windFromDeg = input.windFromDeg)
        putJsonArray("mis_puestos") { input.spots.forEach { s -> addJsonObject { spot(s, wind) } } }
        gear(input.gear)
    }.toString()

    private fun JsonObjectBuilder.weather(w: WeatherSnapshot) {
        w.airC?.let { put("aire_c", it) }
        w.windKmh?.let { put("viento_kmh", it) }
        w.windFromDeg?.let { put("viento_de_grados", it) }
        w.gustKmh?.let { put("rachas_kmh", it) }
        w.precipitationMm?.let { put("lluvia_mm_h", it) }
        put("tormenta_prevista", w.forecastStorm)
        w.waterC?.let { put("agua_c", it) }
        put("agua_medida", w.waterMeasured)
    }

    private fun JsonObjectBuilder.spot(s: Spot, wind: WeatherSnapshot?) {
        put("nombre", s.name)
        put("estructura", s.structure.name)
        s.zone?.let { put("zona", it.name) }
        s.depthM?.let { put("profundidad_m_medida", it) }
        s.distanceM?.let { put("distancia_m", it) }
        s.facingDeg?.let { put("orilla_mira_a_grados", it) }
        Spots.windRelation(s.facingDeg, wind?.windFromDeg, wind?.windKmh)?.let { put("viento", it.name) }
        if (s.notes.isNotBlank()) put("notas", s.notes.take(AiValidator.MAX_TEXT))
    }

    private fun JsonObjectBuilder.gear(gear: List<GearItem>) {
        putJsonObject("equipo") {
            putJsonArray("cebos") {
                gear.filter { it.category == GearCategory.BAIT }.forEach { g ->
                    addJsonObject {
                        put("nombre", g.name)
                        g.baitType?.let { put("tipo", it.name) }
                    }
                }
            }
            putJsonArray("montajes") {
                gear.filter { it.category == GearCategory.RIG }.forEach { g ->
                    addJsonObject {
                        put("nombre", g.name)
                        g.rigType?.let { put("tipo", it.name) }
                    }
                }
            }
        }
    }

    private fun JsonObjectBuilder.proposal(p: Proposal) {
        put("tipo", p.kind.name)
        put("origen", p.source.name)
        p.baitName?.let { put("cebo", it) }
        p.rigName?.let { put("montaje", it) }
        p.column?.let { put("columna", it.name) }
    }

    private fun JsonObjectBuilder.checkIn(c: CheckIn, now: Instant) {
        put("hace_min", Duration.between(c.time, now).toMinutes())
        put("senales", c.signals.name)
        put("actividad", c.activity.name)
        c.species?.let { put("especie", it.name) }
        put("estado_cebo", c.baitState.name)
        c.seenAt?.let { put("actividad_vista_en", it.name) }
        c.jumps?.let { put("saltos", it.name) }
        if (c.notWorking) put("no_funciona", true)
        c.userChange?.let { put("cambio_del_usuario", it.name) }
        c.rebait?.let { put("recebado", it.name) }
    }

    /** Avisos recientes por caña que se mandan (el resto queda en el diario). */
    private const val MAX_CHECK_INS = 8
}

