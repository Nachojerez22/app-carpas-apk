package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.advice.DayPart
import com.nachojerez.carpstrategy.domain.advice.ExperienceSummary
import com.nachojerez.carpstrategy.domain.advice.FishingAdvice
import com.nachojerez.carpstrategy.domain.advice.Lesson
import com.nachojerez.carpstrategy.domain.advice.Stat
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

/** Lo que la app sabe además del estado: tus datos, tus lecciones y su propia recomendación. */
data class AiExtras(
    val experience: ExperienceSummary = ExperienceSummary.EMPTY,
    val lessons: List<Lesson> = emptyList(),
    val advice: FishingAdvice? = null,
)

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
    val extras: AiExtras = AiExtras(),
)

/**
 * Instrucciones y estado que se mandan a la IA. **Nunca** van la ubicación GPS, la cuenta ni la
 * clave: solo el estado de la pesca. Las normas de CONOCIMIENTO.md van en las instrucciones y
 * además se comprueban al recibir la respuesta ([AiValidator]).
 */
object AssistantPrompts {
    private const val RULES = """
Eres el guía de pesca de carpa de CarpStrategy en el embalse de Brovales (Badajoz). Tu objetivo es que el usuario saque el MÁXIMO de carpas: no dejes nada al azar. Sé concreto: qué cebo de su equipo, en qué puesto, a qué distancia, a qué altura (fondo, pop-up, zig), cuánto cebar y a qué hora.
Cómo decidir:
- Parte de "conocimiento_base" (su documento CONOCIMIENTO.md), de "recomendacion_de_la_app" y, por encima de ambos, de "lo_que_te_ha_funcionado" y "lecciones_aceptadas" (son datos reales del usuario en Brovales). Si sus datos contradicen al documento, sigue sus datos y dilo.
- Lo que el usuario anota en el puesto manda sobre la previsión.
- Presión atmosférica, nubosidad y luna pesan 0: no las uses como motivo.
Formato y límites (si no los cumples, la app descarta tu respuesta):
1. No prometas peces: habla de opciones mejores o peores.
2. Sin porcentajes ni probabilidades. Sin gramos ni kilos de cebado: di poco, normal o mucho.
3. Usa solo cebos y montajes de "equipo" y puestos de "mis_puestos", con su nombre exacto. No inventes profundidades ni estructuras.
4. La app ya controla el horario: no hables de normativa, permisos ni horario legal. No propongas horas de noche ni fuera de "horario_de_pesca".
5. Cada motivo lleva etiqueta de evidencia en "evidencia": 🟢 demostrado, 🟡 indicio o experiencia, 🔴 sin evidencia, 🟣 hipótesis local o datos propios, 🔵 variabilidad individual.
6. Responde en español, breve (cada texto como mucho 300 caracteres) y SOLO con el JSON pedido, sin texto alrededor.
"""

    val CHECK_IN_SYSTEM = RULES + """
Tarea: en este aviso de la sesión guiada, decide MANTENER o CAMBIAR para pescar más. Un cambio por caña (una sola variable), de estos tipos: PRESENTATION, RIG, COLUMN, DISTANCE, ZONE, ANTI_CRAB, SELECTIVE, INFLOW. Tras una carpa, mantener. En invierno, paciencia. Con tormenta, MANTENER y que se ponga a cubierto. Si "zona_permitida" es false, nada de ZONE ni INFLOW. Responde con este JSON:
{"decision":"MANTENER" o "CAMBIAR","cambios":[{"cana":1,"tipo":"COLUMN","accion":"qué hacer, concreto","cebo":"nombre exacto o null","montaje":"nombre exacto o null","columna":"BOTTOM|POPUP|ZIG|SURFACE o null","puesto":"nombre exacto o null","motivo":"por qué","evidencia":"🟡"}],"motivos":["…"],"evidencia":"🟡"}
Con MANTENER, "cambios" va vacío. No propongas cambios en cañas con "propuesta_pendiente".
"""

    val PLAN_SYSTEM = RULES + """
Tarea: estrategia concreta para hoy con las cañas indicadas, para sacar el máximo de carpas. Da un plan por caña (puesto, cebo, montaje, columna y distancia en metros si el puesto la tiene) y consejos por apartado: dónde (WHERE), cuándo (WHEN: mejores horas dentro de "horario_de_pesca"), cebado (BAIT: poco, normal o mucho y cada cuánto recebar), presentación (PRESENTATION), qué evitar (AVOID) y notas (NOTES). Como mucho 2 consejos por apartado. Responde con este JSON:
{"resumen":"…","evidencia":"🟡","canas":[{"cana":1,"puesto":"nombre exacto o null","cebo":"nombre exacto o null","montaje":"nombre exacto o null","columna":"BOTTOM|POPUP|ZIG|SURFACE o null","distancia_m":40 o null,"motivo":"…","evidencia":"🟡"}],"consejos":[{"apartado":"WHERE","texto":"…","evidencia":"🟡"}],"avisos":[]}
"""

    val REVIEW_SYSTEM = RULES + """
Tarea: repasa la sesión terminada ("sesion") y saca como mucho 3 lecciones útiles para pescar más la próxima vez en Brovales: qué funcionó, qué no y en qué condiciones. Compáralas con "conocimiento_base": si una lección confirma o corrige un apartado, indícalo ("apartado": "§5.8", "corrige": true o false). No repitas "lecciones_aceptadas". Si la sesión no enseña nada nuevo, devuelve la lista vacía. Usa 🟣 salvo que haya algo más sólido. Responde con este JSON:
{"lecciones":[{"tema":"BAIT|WHERE|DISTANCE|PRESENTATION|WHEN|GROUNDBAIT|AVOID|OTHER","texto":"…","condiciones":{"fase":"WINTER|SPRING|SUMMER|HEAT|AUTUMN o null","agua_min_c":14 o null,"agua_max_c":18 o null,"momento":"DAWN|MORNING|MIDDAY|AFTERNOON|DUSK o null","puesto":"nombre exacto o null"},"evidencia":"🟣","apartado":"§5.8 o null","corrige":false}]}
"""

    /** Instrucciones + la parte del documento que sirve para decidir (si se tiene). */
    fun withKnowledge(system: String, knowledge: String?): String =
        if (knowledge.isNullOrBlank()) system else system + "\nconocimiento_base (extracto de CONOCIMIENTO.md):\n" + knowledge

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
        legalStart: Instant? = null,
        extras: AiExtras = AiExtras(),
    ): String {
        val record = session.guided
        val report = record?.let { FieldWeather.report(it, now) }
        return buildJsonObject {
            put("momento", "aviso")
            put("hora_local", CLOCK.format(now.atZone(zone)))
            put("minutos_de_sesion", Duration.between(session.start, now).toMinutes())
            minutes(now, legalEnd)?.let { put("minutos_hasta_recoger", it) }
            schedule(legalStart, legalEnd, zone)
            put("fase", phase.name)
            put("zona_permitida", legalEnd == null || Duration.between(now, legalEnd) > GuidedEngine.LEGAL_NO_ZONE)
            extras(extras, zone)
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
        schedule(input.legalStart, input.legalEnd, input.zone)
        extras(input.extras, input.zone)
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

    /** Horas en las que se puede pescar hoy (la IA no debe proponer otras). */
    private fun JsonObjectBuilder.schedule(start: Instant?, end: Instant?, zone: ZoneId) {
        if (start != null && end != null) put("horario_de_pesca", "${CLOCK.format(start.atZone(zone))}–${CLOCK.format(end.atZone(zone))}")
    }

    /** Tus datos (en condiciones parecidas), tus lecciones aceptadas y la recomendación de la app. */
    private fun JsonObjectBuilder.extras(extras: AiExtras, zone: ZoneId) {
        val exp = extras.experience
        if (exp.sessions > 0) {
            putJsonObject("lo_que_te_ha_funcionado") {
                put("sesiones_parecidas", exp.sessions)
                put("horas_cana", round1(exp.rodHours))
                put("carpas", exp.catches)
                fun <T : Any> list(name: String, stats: List<Stat<T>>, label: (T) -> String) = putJsonArray(name) {
                    stats.take(MAX_STATS).forEach { st ->
                        addJsonObject {
                            put("valor", label(st.value))
                            put("carpas", st.catches)
                            put("horas_cana", round1(st.rodHours))
                            put("sesiones", st.sessions)
                            put("le_ha_funcionado", exp.proven(st))
                        }
                    }
                }
                list("cebos", exp.baits) { it }
                list("puestos", exp.spots) { it }
                list("columnas", exp.columns) { it.name }
                list("momentos_del_dia", exp.dayParts.filter { it.value != DayPart.NIGHT }) { it.name }
            }
        }
        if (extras.lessons.isNotEmpty()) {
            putJsonArray("lecciones_aceptadas") {
                extras.lessons.take(MAX_LESSONS).forEach { l ->
                    addJsonObject {
                        put("tema", l.topic.name)
                        put("texto", l.text)
                        l.conditions.phase?.let { put("fase", it.name) }
                        l.conditions.dayPart?.let { put("momento", it.name) }
                        l.conditions.spotName?.let { put("puesto", it) }
                        l.conditions.waterMinC?.let { put("agua_min_c", it) }
                        l.conditions.waterMaxC?.let { put("agua_max_c", it) }
                    }
                }
            }
        }
        extras.advice?.let { a ->
            putJsonObject("recomendacion_de_la_app") {
                put("para_las", CLOCK.format(a.forTime.atZone(zone)))
                put("momento", a.dayPart.name)
                putJsonArray("cebos") { a.baits.forEach { b -> add(b.gearName ?: b.type?.name ?: "?") } }
                put("columna", a.column.name)
                a.altColumn?.let { put("columna_si_ves_carpas_arriba", it.name) }
                put("distancia", a.distance.name)
                put("profundidad", a.depth.name)
                put("cebado", a.groundbait.name)
                putJsonArray("puestos_ordenados") { a.spots.take(MAX_STATS).forEach { add(it.spot.name) } }
            }
        }
    }

    private fun round1(v: Double): Double = Math.round(v * 10) / 10.0

    /** Cuántos elementos de cada lista se mandan (lo demás queda en el diario). */
    private const val MAX_STATS = 5
    private const val MAX_LESSONS = 20

    /** Estado para repasar una sesión terminada y sacar lecciones. */
    fun reviewState(session: Session, phase: FishingPhase, zone: ZoneId, gear: List<GearItem>, spots: List<Spot>, extras: AiExtras): String = buildJsonObject {
        put("momento", "repaso_de_sesion")
        putJsonObject("sesion") {
            put("inicio", CLOCK.format(session.start.atZone(zone)))
            session.end?.let { put("fin", CLOCK.format(it.atZone(zone))) }
            put("fecha", session.start.atZone(zone).toLocalDate().toString())
            put("fase", phase.name)
            session.context?.waterTempC?.let { put("agua_c", it) }
            put("canas", session.rods)
            put("carpas", session.catches.size)
            put("picadas", session.bites)
            if (session.bait.isNotBlank()) put("cebo", session.bait)
            if (session.notes.isNotBlank()) put("notas_del_usuario", session.notes.take(AiValidator.MAX_TEXT))
            session.guided?.let { record ->
                record.spot?.let { s -> putJsonObject("puesto") { spot(s, record.weather.firstOrNull()) } }
                record.groundbait?.let { put("cebado_inicial", it.name) }
                putJsonArray("tiempo") { record.weather.forEach { w -> addJsonObject { put("hora", CLOCK.format(w.time.atZone(zone))); weather(w) } } }
                putJsonArray("condiciones_anotadas") {
                    record.conditions.forEach { c -> addJsonObject { put("hora", CLOCK.format(c.time.atZone(zone))); put("tipo", c.condition.name); put("activa", c.active) } }
                }
                putJsonArray("canas_detalle") {
                    record.rods.forEach { rod ->
                        addJsonObject {
                            put("cana", rod.id)
                            putJsonArray("tramos") {
                                rod.log.segments.forEach { seg ->
                                    addJsonObject {
                                        put("desde", CLOCK.format(seg.start.atZone(zone)))
                                        seg.end?.let { put("hasta", CLOCK.format(it.atZone(zone))) }
                                        put("paso", seg.kind.name)
                                        seg.baitName?.let { put("cebo", it) } ?: seg.bait?.let { put("tipo_cebo", it.name) }
                                        seg.rigName?.let { put("montaje", it) }
                                        seg.column?.let { put("columna", it.name) }
                                        put("picadas", seg.bites)
                                        put("carpas", seg.catches)
                                        put("otras_especies", seg.bycatch)
                                        putJsonArray("avisos") { seg.checkIns.forEach { c -> addJsonObject { put("hora", CLOCK.format(c.time.atZone(zone))); checkIn(c, session.end ?: c.time) } } }
                                    }
                                }
                            }
                            putJsonArray("propuestas") {
                                rod.log.proposals.forEach { p ->
                                    addJsonObject {
                                        proposal(p.proposal)
                                        put("decision", p.decision.name)
                                        p.reason?.let { put("motivo_rechazo", it.name) }
                                        if (p.comment.isNotBlank()) put("comentario", p.comment.take(AiValidator.MAX_TEXT))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        putJsonArray("mis_puestos") { spots.forEach { s -> addJsonObject { spot(s, null) } } }
        gear(gear)
        extras(extras, zone)
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

