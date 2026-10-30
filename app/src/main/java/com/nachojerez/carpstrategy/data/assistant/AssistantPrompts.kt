package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.advice.DayPart
import com.nachojerez.carpstrategy.domain.advice.ExperienceSummary
import com.nachojerez.carpstrategy.domain.advice.FishingAdvice
import com.nachojerez.carpstrategy.domain.advice.GroundbaitAmounts
import com.nachojerez.carpstrategy.domain.advice.Lesson
import com.nachojerez.carpstrategy.domain.advice.Stat
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.assistant.AiValidator
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.FieldWeather
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.GuidedEngine
import com.nachojerez.carpstrategy.domain.guided.Proposal
import com.nachojerez.carpstrategy.domain.guided.RodKind
import com.nachojerez.carpstrategy.domain.guided.RodRules
import com.nachojerez.carpstrategy.domain.guided.RodSetup
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.Spots
import com.nachojerez.carpstrategy.domain.guided.WeatherSnapshot
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.reservoir.Reservoir
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirContext
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirSheet
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
    /** Embalse activo con su ficha (especies, si se puede cebar). */
    val reservoir: ReservoirContext? = null,
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
    /** Puesto elegido en Estrategia (fase 7.10): el plan se hace para él. */
    val chosenSpot: Spot? = null,
    /** Nivel de cebado de la app (null = no se ceba) y tu medida en kg por caña (fase 7.13). */
    val groundbait: GroundbaitLevel? = null,
    val groundbaitNormalKg: Double? = null,
    /** Cañas de Mi equipo que lleva hoy, en orden (fase 7.20); vacío = sin tipo. */
    val rodSetups: List<RodSetup> = emptyList(),
)

/**
 * Instrucciones y estado que se mandan a la IA. **Nunca** van la ubicación GPS, la cuenta ni la
 * clave: solo el estado de la pesca. Las normas de CONOCIMIENTO.md van en las instrucciones y
 * además se comprueban al recibir la respuesta ([AiValidator]).
 */
object AssistantPrompts {
    private const val RULES = """
Eres el guía de pesca de carpa de CarpStrategy en el embalse de "embalse" (Extremadura; si no viene, Brovales). Tu objetivo es que el usuario saque el MÁXIMO de carpas: no dejes nada al azar. Sé concreto: qué cebo de su equipo, en qué puesto, a qué distancia, a qué altura (fondo, pop-up, zig), cuánto cebar y a qué hora.
Cómo decidir:
- Parte de "conocimiento_base" (su documento CONOCIMIENTO.md), de "recomendacion_de_la_app" y, por encima de ambos, de "lo_que_te_ha_funcionado" y "lecciones_aceptadas" (son datos reales del usuario, en este embalse si "en_otros_embalses" es false). Si sus datos contradicen al documento, sigue sus datos y dilo.
- Adapta todo a las especies de "embalse.especies" (§5.11): si hay barbos o bogas, cebos selectivos y poca partícula; con cangrejo, cebos duros; con siluro, montaje fuerte. Si "embalse.se_puede_cebar" es false, NO propongas cebar (solo el cebo del anzuelo); si es "sin_confirmar", dilo una vez.
- El apartado §6 del documento es solo de Brovales: en otro embalse no lo apliques; usa "embalse.notas" si las hay.
- Lo que el usuario anota en el puesto manda sobre la previsión.
- Presión atmosférica, nubosidad y luna pesan 0: no las uses como motivo.
Formato y límites (si no los cumples, la app descarta tu respuesta):
1. No prometas peces: habla de opciones mejores o peores.
2. Sin porcentajes ni probabilidades. En los textos, sin gramos ni kilos de cebado: di poco, normal o mucho. Los kilos solo van en "engodo.kg_por_cana" (número) y solo si existe "cebado.tu_normal_kg_por_cana" (la medida del usuario).
3. Usa solo cebos y montajes de "equipo" y puestos de "mis_puestos", con su nombre exacto. No inventes profundidades ni estructuras.
4. La app ya controla el horario: no hables de normativa, permisos ni horario legal. No propongas horas de noche ni fuera de "horario_de_pesca".
5. Cada motivo lleva etiqueta de evidencia en "evidencia": 🟢 demostrado, 🟡 indicio o experiencia, 🔴 sin evidencia, 🟣 hipótesis local o datos propios, 🔵 variabilidad individual.
6. Responde en español, breve (cada texto como mucho 300 caracteres) y SOLO con el JSON pedido, sin texto alrededor.
7. Respeta el tipo de cada caña ("tipo" en "canas" o "canas_de_hoy"). FIJA (sin carrete, de "longitud_m"): a fondo (BOTTOM), a media agua con flotador (ZIG) o en superficie (SURFACE), siempre a una distancia hasta su longitud, con flotador de 3 a 4 g y NUNCA con boilies (ni pop-up ni boilie duro). CARPFISHING: siempre a fondo (BOTTOM o POPUP, nunca ZIG ni SURFACE), de 7 a 100 m, con plomo de 40 a 90 g; vale cualquier cebo, también maíz. En los textos puedes citar el flotador o el plomo en gramos («flotador de 3 g», «plomo de 60 g») dentro de esos rangos.
"""

    val CHECK_IN_SYSTEM = RULES + """
Tarea: en este aviso de la sesión guiada, decide MANTENER o CAMBIAR para pescar más. Un cambio por caña (una sola variable), de estos tipos: PRESENTATION, RIG, COLUMN, DISTANCE, ZONE, ANTI_CRAB, SELECTIVE, INFLOW. Tras una carpa, mantener. En invierno, paciencia. Con tormenta, MANTENER y que se ponga a cubierto. Si "zona_permitida" es false, nada de ZONE ni INFLOW. Responde con este JSON:
{"decision":"MANTENER" o "CAMBIAR","cambios":[{"cana":1,"tipo":"COLUMN","accion":"qué hacer, concreto","cebo":"nombre exacto o null","montaje":"nombre exacto o null","columna":"BOTTOM|POPUP|ZIG|SURFACE o null","puesto":"nombre exacto o null","motivo":"por qué","evidencia":"🟡"}],"motivos":["…"],"evidencia":"🟡"}
Con MANTENER, "cambios" va vacío. No propongas cambios en cañas con "propuesta_pendiente".
"""

    val PLAN_SYSTEM = RULES + """
Tarea: estrategia concreta para hoy con las cañas indicadas, para sacar el máximo de carpas. Ayúdale también a decidir dónde pescar: compara todos los puestos de "mis_puestos" (estructura, profundidad, distancia, orientación, fetch, cómo les da el viento con el agua y el aire de hoy, y lo que le ha funcionado en "lo_que_te_ha_funcionado", "lecciones_aceptadas" y "recomendacion_de_la_app") y elige el mejor para hoy en "puesto_recomendado" con un motivo concreto (si no hay puestos, null). Si hay "puesto_elegido", es el que el usuario tiene pensado: el plan por caña es para ese puesto (adaptado a su estructura, profundidad, distancia y viento), y si recomiendas otro, explica en el motivo qué gana con el cambio; si el elegido ya es el mejor, recomiéndalo y di por qué. Sin "puesto_elegido", el plan es para el puesto que recomiendas. Da un plan por caña (puesto, cebo, montaje, columna y distancia en metros si el puesto la tiene) y consejos por apartado: dónde (WHERE), cuándo (WHEN: mejores horas dentro de "horario_de_pesca"), cebado (BAIT: poco, normal o mucho y cada cuánto recebar), presentación (PRESENTATION), qué evitar (AVOID) y notas (NOTES). Como mucho 2 consejos por apartado. Propón también el engodo con lo que lleva hoy ("equipo.cebos": engodos y mixes, harinas, pellet, partícula, líquidos, boilies triturados), teniendo en cuenta su marca, "que_lleva" y "ficha_ia", la fase, el agua y las especies: qué mezcla, en partes (de 1 a 10), cuántos kilos por caña según "cebado" (solo si hay "tu_normal_kg_por_cana"; si no, null) y cómo recebar. Si "embalse.se_puede_cebar" es false o "cebado.nivel" es "sin_cebar", "engodo" es null. Responde con este JSON:
{"resumen":"…","evidencia":"🟡","puesto_recomendado":{"puesto":"nombre exacto","motivo":"…","evidencia":"🟡"} o null,"engodo":{"mezcla":[{"cebo":"nombre exacto","partes":2}],"kg_por_cana":1.5 o null,"recebado":"cómo y cuándo recebar, sin gramos","motivo":"…","evidencia":"🟡"} o null,"canas":[{"cana":1,"puesto":"nombre exacto o null","cebo":"nombre exacto o null","montaje":"nombre exacto o null","columna":"BOTTOM|POPUP|ZIG|SURFACE o null","distancia_m":40 o null,"peso_g":60 o null,"motivo":"…","evidencia":"🟡"}],"consejos":[{"apartado":"WHERE","texto":"…","evidencia":"🟡"}],"avisos":[]}
"""

    val REVIEW_SYSTEM = RULES + """
Tarea: repasa la sesión terminada ("sesion") y saca como mucho 3 lecciones útiles para pescar más la próxima vez en este embalse: qué funcionó, qué no y en qué condiciones. Compáralas con "conocimiento_base": si una lección confirma o corrige un apartado, indícalo ("apartado": "§5.8", "corrige": true o false). No repitas "lecciones_aceptadas". Si la sesión no enseña nada nuevo, devuelve la lista vacía. Usa 🟣 salvo que haya algo más sólido. Responde con este JSON:
{"lecciones":[{"tema":"BAIT|WHERE|DISTANCE|PRESENTATION|WHEN|GROUNDBAIT|AVOID|OTHER","texto":"…","condiciones":{"fase":"WINTER|SPRING|SUMMER|HEAT|AUTUMN o null","agua_min_c":14 o null,"agua_max_c":18 o null,"momento":"DAWN|MORNING|MIDDAY|AFTERNOON|DUSK o null","puesto":"nombre exacto o null"},"evidencia":"🟣","apartado":"§5.8 o null","corrige":false}]}
"""

    /**
     * Ficha de un cebo (fase 7.13): con búsqueda en Google y el enlace de la tienda si el proveedor
     * lo permite. Aquí no van datos de la pesca ni la ubicación: solo la marca, el nombre, lo que
     * escribió el usuario y el enlace.
     */
    val BAIT_SYSTEM = """
Eres un experto en cebos para la pesca de la carpa. Busca el producto de "cebo" (marca y nombre) en internet y, si viene "enlace", lee esa página (tienda o fabricante). Describe qué es y cómo sirve para la carpa.
Normas (si no las cumples, la app descarta la respuesta):
1. No inventes. Si no encuentras el producto, di lo que sepas por el tipo de cebo con evidencia 🔴 o 🟣 y deja vacío lo que no sepas.
2. Respeta lo que el usuario escribió en "que_lleva": complétalo, no lo contradigas sin decirlo.
3. No prometas capturas.
4. Evidencia: 🟢 ficha oficial del fabricante, 🟡 tiendas o reseñas coinciden, 🟣 deducido o sin fuente, 🔴 no encontrado.
5. Responde en español y SOLO con este JSON, sin texto alrededor:
{"tipo":"BOILIE|BOILIE_HARD|PELLET|MAIZE|HEMP|TIGERNUT|PASTE|BREAD|POPUP|PVA|WORM|GROUNDBAIT_MIX|FLOUR|LIQUID|OTHER o null","composicion":"ingredientes, sabor, tamaño o formato (como mucho 500 caracteres)","perfil":"dulce, frutal, pescado, especiado, lácteo, neutro…","funciones":["ATTRACTION","RETENTION","SELECTIVITY","VISIBILITY"],"solubilidad":"HIGH|MEDIUM|LOW o null","fases":["WINTER","SPRING","SUMMER","HEAT","AUTUMN"],"notas":"cómo usarlo para la carpa (como mucho 300 caracteres)","fuentes":["https://…"],"evidencia":"🟣"}
"""

    /** Lo mismo sin búsqueda (la clave no la permite): solo lo que sabe la IA, sin fuentes. */
    val BAIT_SYSTEM_NO_SEARCH = BAIT_SYSTEM
        .replace("Busca el producto de \"cebo\" (marca y nombre) en internet y, si viene \"enlace\", lee esa página (tienda o fabricante).", "No puedes buscar en internet: usa lo que sepas del producto de \"cebo\" (marca y nombre) y lo que escribió el usuario.")
        .replace("\"fuentes\":[\"https://…\"],", "\"fuentes\":[],")

    /** Con enlace (fase 7.15): solo se lee la página, sin buscar en Google (no gasta búsquedas). */
    val BAIT_SYSTEM_URL_ONLY = BAIT_SYSTEM
        .replace("Busca el producto de \"cebo\" (marca y nombre) en internet y, si viene \"enlace\", lee esa página (tienda o fabricante).", "No busques en internet: lee la página de \"enlace\" (tienda o fabricante) y usa lo que sepas del producto de \"cebo\" (marca y nombre).")

    /** Estado para la ficha de un cebo: solo el producto (nunca la ubicación ni la pesca). */
    fun baitState(item: GearItem): String = buildJsonObject {
        putJsonObject("cebo") {
            if (item.brand.isNotBlank()) put("marca", item.brand.trim())
            put("nombre", item.name.trim())
            item.baitType?.let { put("tipo_actual", it.name) }
            if (item.description.isNotBlank()) put("que_lleva", item.description.trim().take(AiValidator.MAX_COMPOSITION))
            if (item.url.isNotBlank()) put("enlace", item.url.trim())
        }
    }.toString()

    val CHAT_SYSTEM = RULES + """
Tarea: eres un experto en la pesca de la carpa y estás conversando con el usuario, que está pescando ahora. Ya sabes dónde está ("embalse" y "puesto_actual"), qué estrategia lleva ("estrategia_del_dia", "recomendacion_de_la_app" y la configuración de cada caña en "canas") y lo que ha pasado en la sesión: no le pidas datos que ya tienes. Contesta a "mensaje_del_usuario" teniendo en cuenta "conversacion". Responde como un pescador experto que está a su lado: claro, concreto y breve (como mucho 1000 caracteres). Si lo que cuenta pide cambiar algo, propónlo en "cambios" (un cambio por caña, tipos PRESENTATION, RIG, COLUMN, DISTANCE, ZONE, ANTI_CRAB, SELECTIVE, INFLOW; nada de ZONE ni INFLOW si "zona_permitida" es false; no en cañas con "propuesta_pendiente"); si no, deja "cambios" vacío. Si te pregunta algo que no sabes, dilo. Responde con este JSON:
{"respuesta":"…","cambios":[{"cana":1,"tipo":"COLUMN","accion":"qué hacer, concreto","cebo":"nombre exacto o null","montaje":"nombre exacto o null","columna":"BOTTOM|POPUP|ZIG|SURFACE o null","puesto":"nombre exacto o null","motivo":"por qué","evidencia":"🟡"}],"evidencia":"🟡"}
"""

    /**
     * Ficha de un embalse. Aquí no hay datos del usuario: solo el nombre, el municipio y las
     * coordenadas públicas del embalse del catálogo (nunca las del GPS del usuario).
     */
    val SHEET_SYSTEM = """
Eres el guía de pesca de carpa de CarpStrategy. Prepara la ficha del embalse de "embalse" (Extremadura) aplicando "conocimiento_base" (CONOCIMIENTO.md): qué especies hay y qué cambia eso en la pesca de la carpa.
Normas (si no las cumples, la app descarta la respuesta):
1. No inventes. Si no sabes si una especie está, NO la pongas en la lista. "presente": false solo si sabes que no está.
2. Especies solo de esta lista: CARP, BARBEL, NASE, TENCH, CRUCIAN, BULLHEAD, CATFISH, BLACK_BASS, PIKE, PIKEPERCH, SUNFISH, BLEAK, CRAYFISH (cangrejo rojo americano).
3. "abastecimiento": true si sabes que abastece de agua a una población, false si sabes que no, null si no lo sabes.
4. "resumen" y cada nota: qué hacer para pescar más carpas en este embalse según sus especies, estructura y uso conocidos (como mucho 300 caracteres). No prometas peces, sin porcentajes, sin gramos ni kilos, sin noche, sin normativa ni horarios.
5. Cada nota con evidencia: 🟢 demostrado, 🟡 indicio o experiencia, 🔴 sin evidencia, 🟣 hipótesis o dato sin verificar (lo normal aquí).
6. Responde en español y SOLO con este JSON:
{"especies":[{"especie":"CARP","presente":true}],"abastecimiento":null,"resumen":"…","notas":[{"texto":"…","evidencia":"🟣"}]}
"""

    /** Estado para la ficha: datos públicos del catálogo y lo que ya confirmó el usuario. */
    fun sheetState(reservoir: Reservoir, previous: ReservoirSheet?): String = buildJsonObject {
        put("momento", "ficha_de_embalse")
        put("embalse", reservoir.name)
        reservoir.municipality?.let { put("municipio", it) }
        reservoir.coto?.let { put("coto", it) }
        if (!reservoir.custom) {
            put("lat", Math.round(reservoir.point.latitude * 1000) / 1000.0)
            put("lon", Math.round(reservoir.point.longitude * 1000) / 1000.0)
        }
        if (reservoir.waterSupplyByName) put("nombre_oficial_dice_abastecimiento", true)
        val confirmed = previous?.species.orEmpty().filterValues { it.confirmedByUser }
        if (confirmed.isNotEmpty()) {
            putJsonArray("especies_confirmadas_por_el_usuario") {
                confirmed.forEach { (sp, st) -> addJsonObject { put("especie", sp.name); put("presente", st.present) } }
            }
        }
    }.toString()

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
        return buildJsonObject {
            put("momento", "aviso")
            sessionState(session, phase, legalEnd, now, zone, gear, spots, legalStart, extras)
        }.toString()
    }

    /**
     * Estado para el chat de la sesión (fase 7.7): todo lo de un aviso (embalse, puesto, cañas,
     * tiempo, tus datos), la estrategia del día, la conversación hasta ahora y el mensaje nuevo.
     */
    fun chatState(
        session: Session,
        phase: FishingPhase,
        legalEnd: Instant?,
        now: Instant,
        zone: ZoneId,
        gear: List<GearItem>,
        spots: List<Spot>,
        message: String,
        legalStart: Instant? = null,
        extras: AiExtras = AiExtras(),
    ): String = buildJsonObject {
        put("momento", "conversacion_en_el_puesto")
        put("mensaje_del_usuario", message.trim().take(MAX_USER_MESSAGE))
        sessionState(session, phase, legalEnd, now, zone, gear, spots, legalStart, extras)
        session.guided?.ai?.lastOrNull { it.kind == AiKind.PLAN && it.valid }?.response?.let(AssistantJson::parsePlan)?.let { plan ->
            putJsonObject("estrategia_del_dia") {
                put("resumen", plan.summary)
                putJsonArray("canas") {
                    plan.rods.forEach { r ->
                        addJsonObject {
                            put("cana", r.rod)
                            r.spotName?.let { put("puesto", it) }
                            r.baitName?.let { put("cebo", it) }
                            r.rigName?.let { put("montaje", it) }
                            r.column?.let { put("columna", it.name) }
                            r.distanceM?.let { put("distancia_m", it) }
                            if (r.reason.isNotBlank()) put("motivo", r.reason)
                        }
                    }
                }
                putJsonArray("consejos") { plan.advice.forEach { a -> add(a.text) } }
            }
        }
        putJsonArray("conversacion") {
            session.guided?.chat?.takeLast(MAX_CHAT_HISTORY)?.forEach { m ->
                if (m.rejected) return@forEach
                addJsonObject {
                    put("hora", CLOCK.format(m.time.atZone(zone)))
                    put("de", if (m.fromUser) "usuario" else "ia")
                    put("texto", m.text)
                }
            }
        }
    }.toString()

    private fun JsonObjectBuilder.sessionState(
        session: Session,
        phase: FishingPhase,
        legalEnd: Instant?,
        now: Instant,
        zone: ZoneId,
        gear: List<GearItem>,
        spots: List<Spot>,
        legalStart: Instant?,
        extras: AiExtras,
    ) {
        val record = session.guided
        val report = record?.let { FieldWeather.report(it, now) }
        run {
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
                        rodKind(rod.setup)
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
        }
    }

    /** Estado para el plan antes de pescar. */
    fun planState(input: PlanInput): String = buildJsonObject {
        put("momento", "plan_antes_de_pescar")
        put("hora_local", CLOCK.format(input.now.atZone(input.zone)))
        put("numero_de_canas", input.rods)
        if (input.rodSetups.isNotEmpty()) {
            putJsonArray("canas_de_hoy") {
                input.rodSetups.forEachIndexed { i, r ->
                    addJsonObject {
                        put("cana", i + 1)
                        if (r.name.isNotBlank()) put("nombre", r.name)
                        rodKind(r)
                    }
                }
            }
        }
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
        putJsonObject("cebado") {
            put("nivel", input.groundbait?.name ?: "sin_cebar")
            input.groundbaitNormalKg?.let { normal ->
                put("tu_normal_kg_por_cana", normal)
                input.groundbait?.let { put("la_app_calcula_kg_por_cana", round1(GroundbaitAmounts.factor(it) * normal)) }
            }
        }
        input.chosenSpot?.let { s -> putJsonObject("puesto_elegido") { spot(s, wind) } }
        putJsonArray("mis_puestos") { input.spots.forEach { s -> addJsonObject { spot(s, wind) } } }
        gear(input.gear)
    }.toString()

    /** Horas en las que se puede pescar hoy (la IA no debe proponer otras). */
    private fun JsonObjectBuilder.schedule(start: Instant?, end: Instant?, zone: ZoneId) {
        if (start != null && end != null) put("horario_de_pesca", "${CLOCK.format(start.atZone(zone))}–${CLOCK.format(end.atZone(zone))}")
    }

    /** Tus datos (en condiciones parecidas), tus lecciones aceptadas y la recomendación de la app. */
    private fun JsonObjectBuilder.extras(extras: AiExtras, zone: ZoneId) {
        extras.reservoir?.let { reservoir(it) }
        val exp = extras.experience
        if (exp.sessions > 0) {
            putJsonObject("lo_que_te_ha_funcionado") {
                put("sesiones_parecidas", exp.sessions)
                put("en_otros_embalses", exp.otherReservoirs)
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
                put("cebado", a.groundbait?.name ?: "NO_SE_PUEDE_CEBAR")
                if (a.groundbaitDoubt) put("cebado_por_confirmar", true)
                putJsonArray("puestos_ordenados") { a.spots.take(MAX_STATS).forEach { add(it.spot.name) } }
            }
        }
    }

    private fun JsonObjectBuilder.reservoir(ctx: ReservoirContext) {
        putJsonObject("embalse") {
            put("nombre", ctx.reservoir.name)
            ctx.reservoir.municipality?.let { put("municipio", it) }
            ctx.reservoir.coto?.let { put("coto", it) }
            val sheet = ctx.sheet
            putJsonArray("especies") { ctx.species.sortedBy { it.ordinal }.forEach { add(it.name) } }
            sheet?.species?.filterValues { !it.present }?.keys?.takeIf { it.isNotEmpty() }?.let { absent ->
                putJsonArray("especies_que_no_hay") { absent.sortedBy { it.ordinal }.forEach { add(it.name) } }
            }
            if (sheet?.hasUnconfirmed == true) put("especies_sin_confirmar", true)
            when (ctx.groundbaitAllowed) {
                true -> put("se_puede_cebar", true)
                false -> put("se_puede_cebar", false)
                null -> put("se_puede_cebar", "sin_confirmar")
            }
            sheet?.summary?.let { put("resumen", it) }
            if (sheet != null && sheet.notes.isNotEmpty()) putJsonArray("notas") { sheet.notes.forEach { add(it.text) } }
        }
    }

    private fun round1(v: Double): Double = Math.round(v * 10) / 10.0

    /** Cuántos elementos de cada lista se mandan (lo demás queda en el diario). */
    private const val MAX_STATS = 5
    private const val MAX_LESSONS = 20

    /** Mensajes del chat que se mandan (los últimos) y longitud máxima de un mensaje del usuario. */
    private const val MAX_CHAT_HISTORY = 20
    private const val MAX_USER_MESSAGE = 1_000

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
            session.context?.observed?.let { o ->
                putJsonObject("viento_observado_en_la_orilla") {
                    o.relation?.let { put("viento_en_puesto", it.name) }
                    o.waves?.let { put("oleaje", it.name) }
                    o.foamLine?.let { put("linea_espuma", it) }
                    o.airShoreC?.let { put("aire_orilla_c", it) }
                    if (o.issues.isNotEmpty()) putJsonArray("problemas") { o.issues.forEach { add(it.name) } }
                }
            }
            put("picadas", session.bites)
            if (session.bait.isNotBlank()) put("cebo", session.bait)
            if (session.notes.isNotBlank()) put("notas_del_usuario", session.notes.take(AiValidator.MAX_TEXT))
            session.guided?.let { record ->
                record.spot?.let { s -> putJsonObject("puesto") { spot(s, record.weather.firstOrNull()) } }
                record.groundbait?.let { put("cebado_inicial", it.name) }
                putJsonArray("tiempo") { record.weather.forEach { w -> addJsonObject { put("hora", CLOCK.format(w.time.atZone(zone))); weather(w) } } }
                putJsonArray("conversacion_con_la_ia") {
                    record.chat.filterNot { it.rejected }.takeLast(MAX_CHAT_HISTORY).forEach { m ->
                        addJsonObject { put("hora", CLOCK.format(m.time.atZone(zone))); put("de", if (m.fromUser) "usuario" else "ia"); put("texto", m.text) }
                    }
                }
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
        s.fetchM?.let { put("fetch_m", it) }
        Spots.windRelation(s.facingDeg, wind?.windFromDeg, wind?.windKmh)?.let { put("viento", it.name) }
        if (s.notes.isNotBlank()) put("notas", s.notes.take(AiValidator.MAX_TEXT))
    }

    /** Tipo de caña y lo que permite (fase 7.20): la IA lo tiene que respetar ([AiValidator] lo comprueba). */
    private fun JsonObjectBuilder.rodKind(r: RodSetup) {
        val kind = r.kind ?: return
        put("tipo", if (kind == RodKind.FIXED) "FIJA" else "CARPFISHING")
        r.lengthM?.let { put("longitud_m", it) }
        putJsonArray("columnas_permitidas") { RodRules.columns(kind).sortedBy { it.ordinal }.forEach { add(it.name) } }
        RodRules.distanceRange(kind, r.lengthM)?.let { d ->
            put("distancia_min_m", d.start)
            put("distancia_max_m", d.endInclusive)
        }
        RodRules.weightRange(kind)?.let { w ->
            put(if (kind == RodKind.FIXED) "flotador_g" else "plomo_g", "${w.first}-${w.last}")
        }
        put("boilies", RodRules.baitAllowed(kind, BaitType.BOILIE))
    }

    private fun JsonObjectBuilder.gear(gear: List<GearItem>) {
        putJsonObject("equipo") {
            putJsonArray("cebos") {
                gear.filter { it.category == GearCategory.BAIT }.forEach { g ->
                    addJsonObject {
                        put("nombre", g.name)
                        g.baitType?.let { put("tipo", it.name) }
                        g.brand.takeIf { it.isNotBlank() }?.let { put("marca", it.trim()) }
                        g.description.takeIf { it.isNotBlank() }?.let { put("que_lleva", it.trim().take(AiValidator.MAX_TEXT)) }
                        g.sheet?.let { sh ->
                            putJsonObject("ficha_ia") {
                                put("composicion", sh.composition.take(AiValidator.MAX_TEXT))
                                if (sh.profile.isNotBlank()) put("perfil", sh.profile)
                                sh.solubility?.let { put("solubilidad", it.name) }
                                if (sh.notes.isNotBlank()) put("notas", sh.notes.take(AiValidator.MAX_TEXT))
                            }
                        }
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

