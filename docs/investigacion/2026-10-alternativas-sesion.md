# Capa de ALTERNATIVAS para la app de carpfishing en Brovales: tiempos de decisión, escalera de planes B/C, sustitución de cebos y normativa

> Investigación aportada por el usuario el 3/10/2026 (elaborada con Claude). Se conserva tal cual como fuente; solo se han quitado las marcas sueltas de las citas web (wiley, juntaex, boe…). Lo que se usa en la app está integrado en `docs/CONOCIMIENTO.md` §3.2, §5.5, §5.6, §5.9, §6, §7, §9, §10, §12 y §14.

La conclusión principal es que ningún estudio dice cada cuántos minutos hay que cambiar de puesto, cebo o montaje durante una sesión de carpa. Lo que la ciencia sí permite afirmar es que una zona cebada desde cero tarda días, no horas, en atraer carpas en número: unos 3–4 días según la telemetría de Bajer et al. 2010 y 2–3 días según Kasumyan. También sabemos que las carpas aprenden muy deprisa a evitar el anzuelo, y que el frío multiplica el tiempo entre tomas de alimento. Por eso, dentro de una sesión de un solo día como las de Brovales (sin pesca nocturna), la app debería cambiar de sitio y de capa de agua cuando no hay señales, y cambiar de presentación cuando hay señales pero no picadas. Los tiempos concretos (60–240 min según la temperatura del agua) son 🟣 hipótesis que habrá que calibrar con las sesiones del usuario.

## TL;DR

- **Tiempos (🟢 + 🟣):** la evidencia de campo indica que la atracción por cebado se aprende en 2–4 días. Indica también que la capturabilidad de la carpa cae a los pocos días de pesca y que el tránsito digestivo es unas 5 veces más lento a 9 °C que a 26,5 °C. Con estos datos, la escalera propuesta es: sin señales → cambiar de columna/distancia y después de zona; con señales y sin picadas → cambiar de presentación/cebo de anzuelo; en invierno, paciencia. Los umbrales de partida son 60–90 min en verano, 90–120 min en primavera y otoño y 150–240 min en invierno, todos 🟣 a calibrar.
- **Cebos (🟢/🟡/⚖):** en un estudio con caña (Ateşşahin y Dürrani 2023, Fisheries Research 261:106640), el maíz en grano capturó 2,1–3,4 veces más que la masa y el pescado. Las partículas (maíz, cañamón, chufa preparada), los pellets y los boilies son sustitutos funcionales entre sí si se respeta su papel: atracción, retención, selectividad o visibilidad. En Extremadura está prohibido usar como cebo o en el cebado almejas, mejillones, cangrejos (incluido el cangrejo rojo) y peces continentales «en ninguna forma o estado». También está prohibido el clonk, y el cebado en los embalses de abastecimiento. El cebado previo con productos no tóxicos está permitido por la Ley 11/2010 (art. 40.2).
- **Brovales (⚖, por confirmar):** en ninguna de las normas revisadas consta un régimen especial para Brovales. No es coto ni tiene horario libre, y está registrado como embalse de riego. Por tanto, se aplican las reglas generales: 3 cañas en ≤10 m, 3 señuelos por caña, horario de 1 h antes del orto a 1 h después del ocaso y cebado no tóxico permitido. Debe confirmarse por escrito con el Servicio de Pesca que no se le considera «embalse de abastecimiento». El caso de Valuengo es ambiguo (más abajo).

## 1. Key Findings

| # | Hallazgo | Etiqueta | Fuente | Implicación para la app |
|---|---|---|---|---|
| 1 | Las carpas tardaron 3–4 días en llegar en gran número a un punto cebado nuevo, lo que sugiere una respuesta aprendida. Bajer et al. 2010 siguieron a 34 carpas radiomarcadas durante 10 días de cebado: cada noche acudían más, y la décima noche visitaron el punto 21 de las 34 (Hundt et al. 2022 lo resumen como «aproximadamente el 70 %» en un lago de 35 ha). | 🟢 | Bajer et al. 2010 (Environ. Biol. Fishes 88:295–300), citado en Ghosal et al. 2018 y Hundt et al. 2022 | En una sesión de horas no se «construye» un comedero desde cero. Hay que buscar peces que ya pasen por allí o mantener un comedero varios días (legal en Brovales, por confirmar). |
| 2 | Con engodo (torta de girasol, maíz, trigo y arcilla roja, 1:1:1:5), el número de carpas y la captura diaria subieron bruscamente a los 2–3 días y se estabilizaron en una semana. El experimento duró 79 días (19 de junio–5 de septiembre). | 🟢 | Kasumyan, Kuzishchin y Gruzdeva 2024, J. Ichthyol. 64:689–704 (experimento de 2018 en el río Ajtuba, polígono de 80×30 m; cebos: harina de girasol y boilies «fresa», «ciruela», «tutti-frutti», «maíz dulce», «mejillón»; al principio la mayoría de las carpas se capturaron con el boilie de sabor «mejillón») | Pre-cebar 2–3 días antes tiene base. Recebar de forma constante sostiene la presencia. |
| 3 | Una vez empezado el cebado, las carpas visitaron el punto y comieron sobre todo de noche. La densidad se duplicó y el ADN ambiental se multiplicó por 500. | 🟢 | Ghosal et al. 2018, Ecol. Evol. (lago de 67 ha, maíz partido) | En Brovales la noche no es pescable. Los márgenes legales del alba y el anochecer son la ventana más valiosa (🟣). |
| 4 | En 14 grandes competiciones de carpa de 4 países europeos, las capturas fueron más frecuentes de noche. | 🟢 | Žák 2021, Fisheries Research 243:106086 | Hay que avisar de que Brovales tiene horario diurno: no se debe prometer rendimiento nocturno. |
| 5 | En un lago experimental, las carpas se capturaban con facilidad los primeros días y luego su capturabilidad cayó al nivel de la tenca. Encontrar el cebo es necesario pero no suficiente. | 🟢 | Monk y Arlinghaus 2017, PLoS ONE (Kleiner Döllnsee) | «Hay señales y no pican» = problema de presentación o de desconfianza, no de ubicación. |
| 6 | La evitación del anzuelo se adquiere por experiencia propia y social (peces que solo ven capturar a otros). En Klefoth, Pieterek y Arlinghaus 2013, un grupo entero de carpas desarrolló evitación del anzuelo a los pocos días de pesca. En los estanques de Beukema 1970, la capturabilidad un año después de la captura fue unas 3 veces menor. Otro estudio no encontró evitación que durase más de 7 meses (Monk y Arlinghaus). | 🟢 | Lovén Wallerius et al. 2020, TAFS 149:498–511; Klefoth et al. 2013; Beukema 1970; Monk y Arlinghaus | Con mucha presión de pesca (fines de semana), priorizar cambios de presentación y cebo de anzuelo. |
| 7 | La evacuación intestinal de la carpa fue del 6,0 %/h a 9 °C y del 32,7 %/h a 26,5 °C. El consumo diario fue del 0,39 % del peso a 14 °C (abril) y del 4,08 % a 26,5 °C (agosto). | 🟢 | Garcia y Adelman 1985, J. Fish Biol. | En frío come unas 10 veces menos. Se justifican menos cebo y esperas más largas. |
| 8 | Con mucho calor la ingesta baja: se redujo de forma significativa por encima de 34 °C. | 🟢 (acuicultura) | Revisión citada en estudios de ritmo alimentario de carpa | En Brovales no se espera >34 °C en el agua de fondo, pero >28 °C sí reduce el margen (🟣). |
| 9 | En verano, en el embalse de Flix (Ebro), las carpas hacían migración vertical diaria: profundas y poco activas de noche, someras de día, con uso de aguas hipóxicas (<1,1 mg/L) por la noche. Con más caudal eran menos activas y usaban aguas más someras. | 🟢 | Benito et al. 2015, TAFS 144:491–501 | Un día de verano puede tener carpas en capas someras o a media agua: probar zig o pop-up alto como alternativa. Ese embalse tiene un tiempo de residencia muy bajo, muy distinto de Brovales (🟣 transferibilidad). |
| 10 | Las carpas adultas se agregan lejos de la orilla a finales de otoño e invierno y en zonas someras con vegetación antes y durante el desove. Se agregan cuando el agua baja de 10 °C y forman agregaciones densas por debajo de 5 °C. Esas agregaciones se desplazan con frecuencia. | 🟢 | Penne y Pierce 2008 (Clear Lake, Iowa); Bajer et al. 2011, Fish. Manag. Ecol. 18:497–505 | En invierno: pocos puntos «con peces», hay que localizarlos y luego tener paciencia. |
| 11 | Profundidad media de las carpas en un lago alemán: 5,07 ± 1,09 m en invierno, 1,68 m en primavera, 2,04 m en verano y 2,16 m en otoño. | 🟢 | «Network analysis of intra- and interspecific freshwater fish interactions using year-around tracking», J. R. Soc. Interface 18(183):20210445 (2021), Kleiner Döllnsee | Patrón estacional: más hondo en invierno. En Brovales la batimetría no está verificada. |
| 12 | El maíz en grano capturó 2,1–3,4 veces más que la masa y el pescado. El tipo de cebo no cambió la talla capturada; el tamaño del anzuelo sí. | 🟢 | Ateşşahin y Dürrani 2023, Fisheries Research 261:106640 | El maíz es el «comodín» de sustitución. Que un cebo sea más selectivo por talla es 🟡, no 🟢. |
| 13 | Del total de aminoácidos libres probados, alrededor del 29 % estimuló el gusto de la carpa. La L-cisteína fue muy estimulante. Ningún aminoácido esencial resultó palatable. El olor de fondo cambia la motivación, pero no el consumo de partículas según su sabor. | 🟢 (laboratorio) | Kasumyan y Morsi 1996; Kasumyan y Døving 2003; Kasumyan et al. 2009 | El olor atrae y el gusto decide. Es un argumento para cambiar el cebo de anzuelo (sabor y textura) cuando hay toques sin clavada. |
| 14 | A 17 °C las carpas eligieron más grasa y a 25 °C más proteína. La digestibilidad de la proteína no cambió (95,9 % frente a 96,3 %); la de la grasa fue menor a 17 °C. | 🟢 (acuicultura) | Yamamoto et al., Aquaculture | 🟣 En frío: cebos más digeribles y en poca cantidad. No conviene trasladar la «preferencia por grasa» al anzuelo sin pruebas. |

## 2. Tiempos y decisión durante la sesión

### 2.1 Qué dice la evidencia (y qué no)

**Llegada a un cebado nuevo (🟢).** Tres líneas de evidencia de campo coinciden en que las carpas tardan 2–4 días en concentrarse en un punto cebado nuevo:

- telemetría de Bajer et al. 2010, con 3–4 días;
- experimento de Kasumyan, con 2–3 días y estabilización a la semana;
- Bullers et al. 2026 (Management of Biological Invasions), que observan agregaciones que se forman «en apenas tres días».

Las carpas aprenden la ubicación y vuelven de noche desde zonas «de residencia» a unos 500 m. Ese es el dato duro. No existe un estudio equivalente que mida el tiempo hasta la primera visita durante una sesión de pesca de horas con cebado al momento.

**Ritmo de movimiento (🟢, contexto).** En lagos de Dakota del Sur, las carpas seguidas 24 h se movieron a una media de 100–166 m/h, con máximos (no significativos) en junio, octubre y al anochecer (Hennen et al. 2014). 🟣 Inferencia: una carpa que pasa cerca puede encontrar un cebado en minutos u horas. La duración de la espera depende, por tanto, de si hay peces en la zona, no del tiempo que tarde el olor en difundirse.

**Encuentro ≠ picada (🟢).** Monk y Arlinghaus 2017 demostraron con telemetría de alta resolución que las carpas visitaban los puntos de pesca y, aun así, la capturabilidad se hundió tras los primeros días. En otras palabras: tener peces en el cebadero no garantiza picadas.

**Temperatura (🟢 fisiología → 🟣 umbrales).** El tránsito intestinal es unas 5,5 veces más rápido a 26,5 °C que a 9 °C, y la ración diaria unas 10 veces mayor en agosto que en abril (Garcia y Adelman 1985). 🟣 Inferencia: en invierno hay menos episodios de alimentación al día, así que la probabilidad de picada por cada 30 minutos es baja aunque la ubicación sea correcta.

**Presión de pesca (🟢).** La evitación del anzuelo es rápida y también se aprende socialmente (Lovén Wallerius et al. 2020; Klefoth et al. 2013). 🟣 Inferencia: en fines de semana con mucha gente, el cuello de botella será la presentación, no la ubicación.

**Ritmo diario (🟢 para la noche; 🟡 para el alba y el anochecer).** La alimentación y las capturas se concentran de noche (Ghosal et al. 2018; Žák 2021). En Brovales solo es legal pescar desde 1 h antes del orto hasta 1 h después del ocaso. 🟣 Hipótesis local: las primeras y últimas horas legales del día serán las de más picadas. Hay que validarlo con las sesiones.

**Rotación de puestos y montajes (🔴).** No se ha encontrado ningún estudio que compare la rotación de puestos o montajes durante una misma sesión con mantener el puesto. Toda regla sobre rotación es 🟡 (práctica habitual) o 🟣.

### 2.2 Variables que la app puede usar en cada check-in

| Parámetro nuevo | Tipo | Cómo se obtiene |
|---|---|---|
| minutos_desde_inicio | entero | automático |
| minutos_desde_ultimo_cambio | entero | automático; se reinicia al registrar un cambio |
| minutos_sin_picada (o horas_sin_picadas) | entero | automático desde la última picada, toque o captura |
| senal_visual | enum: ninguna / indirecta (burbujas, barro, toques en la línea) / directa (saltos, carpas vistas) | check-in |
| actividad_anzuelo | enum: nada / toques / picada_fallada / captura | check-in |
| estado_cebo | enum: intacto / mordisqueado (peces pequeños) / desaparecido_o_roto (posible cangrejo o tortuga) / no_revisado | check-in |
| cambio_condiciones | multi: viento_gira, viento_sube, nubes, sol, lluvia, ninguno | check-in + Open-Meteo |
| temp_agua_c | decimal opcional | termómetro del usuario; si falta, se estima por la estación (🟣) |
| fase_estacional | invierno (<10 °C) / primavera (10→20 °C) / verano (>22 °C) / otono (20→10 °C) / calor_extremo (>28 °C) | derivado |
| nivel_tendencia | baja / estable / sube | boletín semanal + entrada manual |
| presion_pesca | baja / alta (fin de semana, festivo o ≥N pescadores a la vista) | calendario + check-in opcional |
| cebo_disponible | lista de cebos que lleva el usuario | se rellena al inicio de la sesión |
| minutos_hasta_fin_legal | entero | cálculo de orto/ocaso ±1 h |

### 2.3 Lógica de decisión (núcleo)

Hay cuatro situaciones básicas, que la app diagnostica cada 30 minutos:

| Situación | Diagnóstico probable | Acción prioritaria | Etiqueta |
|---|---|---|---|
| A. Sin señales y sin actividad en el anzuelo | No hay peces en la zona o en esa capa | Cambiar de columna o distancia y, si sigue igual, de zona por función | 🟣 (lógica basada en el hallazgo 1 y en Benito 2015) |
| B. Con señales y sin actividad | Hay peces, pero no comen en el punto o desconfían | Ajustar la presentación al punto exacto de la actividad, reducir el tamaño del cebo de anzuelo, cambiar a un cebo de anzuelo de alta atracción (maíz o pop-up) y no cambiar de zona | 🟢 (Monk y Arlinghaus 2017) + 🟣 |
| C. Toques o picadas falladas | Hay peces que inspeccionan el cebo | Cambiar el montaje o la presentación (pelo más corto, anzuelo más pequeño o más grande según la talla, cebo de anzuelo más pequeño o flotante) y mantener el cebado | 🟡 |
| D. Captura | La zona funciona | No cambiar nada: recebar poco y repetir. Avisar de que la capturabilidad puede caer tras capturas repetidas | 🟢 (hallazgos 5–6) |

Modificadores:

- Cebo desaparecido_o_roto con anzuelo limpio → probable cangrejo, tortuga o peces pequeños. Pasar a un cebo de anzuelo duro (boilie endurecido, chufa, imitación) o a un pop-up separado del fondo. 🟡
- mordisqueado → peces pequeños (bogas, barbos jóvenes). Aumentar el tamaño del cebo de anzuelo o usar partículas selectivas (chufa, boilie de ≥20 mm). 🟡
- viento_gira o viento_sube hacia la orilla de enfrente → valorar la orilla que recibe el viento. Esto es 🟡: la idea de que las carpas siguen el viento es experiencia de pescadores; no se ha encontrado un estudio con telemetría que lo confirme.
- Lluvia o escorrentía → probar la boca de los arroyos Brovales y Rubiales (entrada de agua). 🟡/🟣. Benito 2015 observó menos actividad con más caudal en un embalse de río; en un embalse de cola larga como Brovales, el efecto es desconocido.
- nivel_tendencia = baja (vaciado para riego de junio a septiembre) → las zonas someras de ayer pueden quedar fuera del agua o calentarse. Mover las cañas a la primera caída. 🟣. Taylor et al. 2012 documentan respuestas de la carpa al nivel del agua en el lago Crescent, pero no hay datos de Brovales.

## 3. Escalera de alternativas por estación y condición

Regla general de orden (🟣): cada vez que se suba un peldaño, cambiar una sola variable por caña. El orden es:

1. presentación o cebo de anzuelo;
2. columna (fondo → pop-up → zig);
3. distancia sobre la misma estructura;
4. zona por función;
5. cantidad de recebado.

Excepción: sin señales en verano, se empieza por columna o distancia, porque el problema más probable es la ubicación.

### 3.1 Tiempos de partida (todos 🟣, a calibrar)

| Fase | Peldaño 1 sin señales | Peldaño 2 sin señales | Con señales y sin picadas: cambio de presentación | Cambio de zona como máximo | Cuándo NO cambiar |
|---|---|---|---|---|---|
| Invierno (<10 °C) | 150 min → distancia o columna (más hondo) | 240 min → zona (refugio profundo o zona soleada) | 120 min | 1 vez por sesión | Si ha habido una sola señal o picada en las últimas 2 h; en las horas centrales soleadas |
| Primavera (10→20 °C, subiendo) | 90 min → más somero o zona de calentamiento | 150 min → zona (colas, ensenadas orientadas al sur) | 60–90 min | 2 veces | En días de desove visible (no molestar; los peces no comen) 🟡 |
| Verano (>22 °C) | 60 min → columna (pop-up alto o zig) o distancia | 90–120 min → zona (sombra, viento o primera caída) | 60 min | 3 veces | En la primera hora legal y al anochecer, si el puesto ya tuvo actividad |
| Calor extremo (>28 °C) | 60 min → zona más fresca u oxigenada (viento, entrada de agua, profundidad media) | 90 min | 60 min | 2 | En las horas centrales: no gastar cebo, esperar al anochecer legal |
| Otoño (20→10 °C, bajando) | 90 min → distancia o caída (transición a profundo) | 150 min → zona de paso o refugio | 90 min | 2 | Si hay señales en el puesto |

Por qué el invierno requiere paciencia (🟢 → 🟣): el tránsito digestivo es unas 5 veces más lento y las carpas se agregan en pocos puntos (Bajer et al. 2011; Garcia y Adelman 1985). Si se cambia de zona cada hora, se sale del único punto con peces antes de que coman.

### 3.2 Modificadores por condición (🟣 salvo indicación)

| Condición | Ajuste en la escalera |
|---|---|
| Lluvia o escorrentía tras un periodo seco | Añadir un peldaño de «boca de recula o arroyo» antes del cambio general de zona |
| Viento sostenido | Si no hay señales tras 60 min, priorizar la orilla que recibe el viento (🟡) |
| Nivel bajando (vaciado de riego) | Distancia → primera caída; desactivar las zonas someras de las que no haya datos recientes |
| Nivel subiendo (otoño-invierno) | Probar las zonas recién inundadas (vegetación terrestre sumergida). 🟡: la carpa explora el litoral nuevo. Hay evidencia de uso de zonas someras con vegetación en primavera (Penne y Pierce 2008), pero no de la respuesta a la subida de nivel en embalses mediterráneos |
| Alta presión de pesca | Peldaño de presentación más temprano (−30 min); cebos de anzuelo poco habituales; recebado mínimo (🟢 aprendizaje + 🟣) |
| Fin del horario legal a menos de 45 min | No proponer cambios de zona; solo de presentación (⚖ + 🟣) |

## 4. Alternativas de zona, distancia y columna

### 4.1 Evidencia de telemetría

| Estudio | Sistema | Resultado útil | Transferencia a Brovales |
|---|---|---|---|
| Benito et al. 2015 | Embalse de Flix (Ebro), 19 meses, ultrasonidos | Poca estacionalidad en actividad y profundidad. En la estación cálida, profundas de noche y someras de día. Usan agua hipóxica de noche. Con más caudal, menos actividad y aguas más someras. Mucha variabilidad entre individuos | 🟣: Flix tiene un tiempo de residencia muy bajo; Brovales (E-T04, monomíctico) se parecerá más a un lago |
| Penne y Pierce 2008 | Clear Lake (Iowa) | Agregación lejos de la orilla en otoño e invierno; zonas someras con vegetación antes y durante el desove | 🟢 patrón general / 🟣 local |
| Bajer et al. 2011 | 3 lagos de Minnesota | Agregación por debajo de 10 °C, densa por debajo de 5 °C. La ubicación no se explicaba por temperatura ni oxígeno, y las agregaciones se movían con frecuencia | En Brovales el agua rara vez bajará de 5 °C (🟣). Las agregaciones serán menos marcadas |
| Taylor et al. 2012 | Lago Crescent (Tasmania) | Movimientos y hábitat según temperatura y nivel del agua | Apoya el modificador por nivel (🟣 local) |
| Kleiner Döllnsee (J. R. Soc. Interface 18(183):20210445, 2021) | Lago alemán, todo el año | Profundidad media de 5,07 ± 1,09 m en invierno frente a ~2 m el resto del año | Patrón estacional de profundidad |
| Hennen et al. 2014 | Lagos de Dakota del Sur | Agregación somera junto a la orilla en mayo, junio y agosto; dispersión a zonas profundas en julio; 100–166 m/h | Hay variación dentro del verano: no hay que fijar una única zona estival |

### 4.2 Alternativas por estructura (orden propuesto, 🟣)

| Primera opción que falla | Alternativa 1 | Alternativa 2 | Alternativa 3 |
|---|---|---|---|
| Borde somero (≤2 m) | Primera caída (talud) | Pie del talud (fondo del cambio de pendiente) | Zona contigua con viento o sombra |
| Primera caída | Antiguo cauce (si está localizado con sonda o mapa) | Borde somero por la mañana o al anochecer | Boca de recula |
| Antiguo cauce | Primera caída del lado soleado (invierno) | Media agua sobre el cauce (zig) en verano | Llano intermedio |
| Boca de recula o arroyo | Interior de la recula (si sube el nivel) | Punta o cabo exterior (zona de paso) | Primera caída cercana |

### 4.3 Columna (🟡/🟣)

| De | A | Cuándo |
|---|---|---|
| Fondo | Pop-up de 5–15 cm | Fondo sucio, cangrejo o algas; o señales sin picadas |
| Pop-up | Zig a media agua (½–¾ de la profundidad) | Verano o calor, día soleado y carpas vistas en superficie o a media agua (en línea con Benito 2015) |
| Zig | Superficie (pan o flotante) | Carpas comiendo en superficie; viento flojo. ⚖ En Extremadura el pan es un cebo vegetal (no prohibido) |

Advertencia obligatoria: la batimetría de Brovales no está verificada. Términos como «primera caída» o «antiguo cauce» son categorías funcionales que el usuario debe confirmar con sonda (en aguas no trucheras no hay prohibición de sonda en la Orden de Vedas 2022; la prohibición se limita a los tramos trucheros) o con plomo marcador.

## 5. Sustitución de cebos y montajes

### 5.1 Funciones de un cebo

- **Atracción olfativa:** sustancias solubles que atraen a distancia.
- **Retención:** mantener a los peces comiendo en el punto (partículas pequeñas y numerosas).
- **Selectividad:** filtrar peces pequeños, cangrejos y fauna no deseada.
- **Visibilidad:** contraste o flotabilidad.

El olor atrae, pero la aceptación la decide el gusto, que es específico de cada especie (Kasumyan y Døving 2003; Kasumyan et al. 2009) 🟢. Por eso un sustituto debe cumplir la misma función, no tener el mismo aspecto.

### 5.2 Tabla de equivalencias

| Cebo propuesto | Función principal | Sustituto 1 | Sustituto 2 | Casero o barato | Temperatura razonable | Evidencia | Pros / contras | ⚖ Extremadura |
|---|---|---|---|---|---|---|---|---|
| Boilie de 15–20 mm (anzuelo) | Selectividad media + atracción | Chufa preparada (2–3 unidades) | Maíz duro cocido (3–4 granos) o 2 granos de maíz dulce con maíz artificial | Masilla dura casera (harinas + huevo) | Todo el año; más pequeño en frío | 🟡 (no hay ensayos de boilie frente a partícula en España) | Resiste peces pequeños; el cangrejo lo destroza en ~2 h (🟡 Eurocarp) | Permitido (vegetal o masa) |
| Boilie de ≥24 mm o endurecido | Selectividad alta, anti-cangrejo | Chufa grande ×3 | Imitación de plástico o corcho | Boilie secado al aire varios días | Verano y otoño | 🟡 | Menos picadas, más talla (no probado: según Ateşşahin y Dürrani 2023 el tipo de cebo no cambia la talla) | Permitido |
| Pellet (cebado) | Atracción olfativa rápida | Harinas de engodo (girasol, maíz) | Boilie triturado | Pienso de carpa o de ganado no medicado 🟣; harina de maíz + pan rallado | Primavera–otoño; poco en invierno | 🟢 (el engodo de girasol atrajo carpas en 2–3 días, Kasumyan) | Se deshace rápido; atrae a todos los ciprínidos | Permitido si no es tóxico (Ley art. 40.2) |
| Maíz dulce o en grano | Atracción visual y gustativa + retención | Maíz duro cocido | Garbanzo cocido 🟡 / trigo cocido | Maíz partido cocido (cebo selectivo de carpa en Norteamérica, Bajer et al. 2010) | Todo el año | 🟢 (2,1–3,4 veces más capturas que masa o pescado, Ateşşahin y Dürrani 2023) | Muy eficaz; poco selectivo (barbos, bogas, tortugas 🟡) | Permitido |
| Cañamón | Retención (lecho fino aceitoso) | Trigo o cebada cocidos | Mijo o alpiste cocidos 🟡 | Trigo cocido (muy barato) | Primavera–otoño (🟡 menos útil en frío) | 🟡 | Mantiene a los peces escarbando; atrae peces pequeños | Permitido |
| Chufa (tigernut) | Selectividad + resistencia | Boilie endurecido | Cacahuete (🟡; muchos cotos del Reino Unido lo prohíben por bienestar) → no recomendado | — | Verano y otoño (🟡) | 🟡 | Muy resistente al cangrejo y a peces pequeños. Debe estar preparada: remojo ≥24 h + hervor ≥30 min; cruda se hincha y es un riesgo para el pez (🟡 consenso de fabricantes) | Permitido; avisar del riesgo si no está preparada |
| Pasta o masilla | Atracción rápida, cebo de anzuelo blando | Pan (miga) | Boilie roto | Harina + huevo + maíz molido | Frío y primavera (se disuelve lento en frío) 🟡 | 🟢 (en Ateşşahin y Dürrani 2023 la masa rindió menos que el maíz) | Barata; poco selectiva; el cangrejo la roba | Permitido |
| Pan | Visibilidad y superficie | Flotante (corcho + maíz) | Pop-up | Corteza de pan | Verano, superficie | 🟡 | Muy visible; atrae aves y peces pequeños | Permitido |
| Bolsa de PVA | Concentrar el cebo junto al anzuelo | Pellets/engodo comprimidos en el plomo (method) | «Stick» de engodo | Bola de engodo apretada | Todo el año; ideal en frío (poca cantidad) | 🟡 | Precisión; el PVA no funciona con cebo húmedo | Permitido (PVA soluble) |
| Pop-up | Visibilidad + fondo sucio + anti-cangrejo | Maíz artificial | Corcho + cebo natural | Boilie con inserto de corcho | Todo el año | 🟡 | Separa el cebo del cangrejo | Permitido |
| Lombriz | Atracción animal | Asticot (si está disponible) | — | — | Frío y primavera | 🟡 | Atrae a todo, incluido el cangrejo | Cebo natural permitido (no es pez, almeja, mejillón ni cangrejo) |

### 5.3 Prohibidos o problemáticos en Extremadura (⚖)

| Cebo o práctica | Estado | Texto |
|---|---|---|
| Almejas, mejillones, cangrejos (incluido el rojo) y peces continentales, como cebo o en el cebado | Prohibido «en ninguna forma o estado» | OGV de 7/11/2022, art. 8.3 («cuando sean de río»). ⚠️ La fórmula «cuando sean de río» deja dudas sobre el mejillón de mar o el pescado marino. La Orden de 2016 permitía peces de mar muertos. Confirmar con el Servicio; mientras tanto, la app debe tratarlos como 🔴/no recomendados |
| Pez vivo | Solo tenca de acuicultura acreditada, en embalses | OGV art. 8.3; Ley art. 40.1. Irrelevante para la carpa |
| Clonk | Prohibido (específico para especies invasoras) | OGV art. 8.6 |
| Cebado en embalses de abastecimiento | Prohibido | OGV art. 8.3 |
| Sustancias venenosas, desoxigenantes, paralizantes o repelentes | Prohibido (falta muy grave) | Ley 11/2010, arts. 39 d) y 59 |
| Fuentes de luz proyectadas al agua | Prohibido | Ley art. 39 f) |
| Transportar cangrejo rojo vivo o devolverlo al agua | Prohibido (especie invasora) | OGV art. 5; DA 3.ª: captura para autoconsumo sin transporte en vivo, con un máximo de 10 reteles |
| Cebado previo | Permitido con productos no tóxicos | Ley art. 40.2, salvo aguas trucheras, régimen especial cuyo plan lo prohíba o lo que diga la OGV (art. 40.3–4) |
| Grandes cantidades de partículas fermentadas en verano | No está prohibido de forma expresa | 🟣 Precaución: el oxígeno de Brovales es desconocido y la Ley prohíbe sustancias «desoxigenadoras». Limitar las cantidades |

Sanciones de referencia (Ley 11/2010):

- cebar en lugares o con sustancias no autorizados: leve (20–100 €) o menos grave (101–500 €);
- usar cebos no permitidos: menos grave;
- usar sustancias tóxicas: grave (501–5.000 €).

### 5.4 Selección automática según lo disponible (cebo_disponible)

Pseudoalgoritmo (🟣):

1. Identificar la función del cebo propuesto (atracción, retención, selectividad o visibilidad).
2. Filtrar cebo_disponible por esa función y por la fase_estacional.
3. Si estado_cebo muestra cangrejo o peces pequeños → priorizar los cebos con selectividad alta.
4. Si no queda ninguno con la misma función → avisar: «Sin equivalente: pesca con [X], que cumple [función secundaria]; espera menos selectividad».
5. Bloquear siempre los cebos ⚖ prohibidos y avisar de la preparación de la chufa y las legumbres.

### 5.5 Cantidad de cebado según la temperatura (🟢 fisiología → 🟣 regla)

La ración diaria de la carpa en el medio natural fue unas 10 veces mayor a 26,5 °C que a 14 °C (Garcia y Adelman 1985). Regla de partida: cebado inicial en invierno ≈ 1/5–1/10 del de verano; recebado solo tras una captura o una señal. Las cifras en kg dependen de la densidad de carpas de Brovales, que es desconocida (🔴): el usuario debe calibrarlas.

## 6. Contexto de Brovales y normativa: lo verificado y lo pendiente

| Dato | Estado | Fuente / nota |
|---|---|---|
| Presa de contrafuertes de 1960; 6,98 hm³; 158,7 ha; cuenca de 203 km²; tipología E-T04; arroyos Brovales y Rubiales | Verificado (documento del usuario) | — |
| Uso: riego, vaciado de unos 30 puntos entre junio y septiembre | Verificado (usuario). Registrado como «Riego» en SEPREM y MITECO, según el subagente | infoembalse: 6,75 hm³ en junio de 2025 frente a una media de 6,24 hm³ en junio de los últimos 10 años |
| Carpa pescable sin talla, cupo ni temporada | ⚖ Verificado | Pescayrios: «Carpa» pescable «en aguas embalsadas artificialmente antes de 2007» sin limitación. Brovales es de 1960 |
| 3 cañas en ≤10 m, 3 señuelos por caña | ⚖ Verificado | Ley art. 38.1; OGV art. 8.1 |
| Horario de 1 h antes del orto a 1 h después del ocaso; sin horario libre | ⚖ Verificado | Ley art. 36.1; OGV art. 9.1. El horario libre solo existe en Orellana, Alange, Alcántara y Puerto Peña |
| Brovales en algún régimen especial | ⚖ No consta (es agua libre) | No es coto (listado oficial de cotos de 23/03/2026), no figura en el Anexo II de 2022 ni en la Resolución de 14/01/2025. No se ha leído el anexo completo de la Resolución de 18/03/2026 |
| Cebado permitido en Brovales | ⚖ Probable, por confirmar | Ley art. 40.2 + no es de abastecimiento según los registros. Pedir confirmación por escrito |
| Valuengo | ⚖ Ambiguo | Es coto y su Plan Técnico de Gestión dice «Engodos: Sí; Masillas: Sí; Cebo de origen animal: Sí». El Ayuntamiento dice que abastece de agua potable a Jerez, Fregenal e Higuera la Real. La OGV prohíbe el cebado en los embalses de abastecimiento, pero no publica ninguna lista. El art. 10.1 permite que el Plan del coto difiera de la Orden. Interpretación (no oficial): el Plan prevalece. Además, la cola del embalse (ZEPA) es incompatible con la pesca todo el año |
| Estado trófico, estratificación, oxígeno, cangrejo rojo en Brovales | 🔴 Sin datos | El cangrejo rojo está muy extendido en Extremadura: Pérez-Bote, Pula y Cascos 2000 (Graellsia 56:71–78) lo detectaron en el 69,77 % de las 407 cuadrículas UTM de 10×10 km prospectadas, y no apareció por encima de 750 m de altitud. No hay confirmación específica para Brovales |
| Talla de las carpas en Brovales | 🟡 (fuente débil) | Un blog habla de carpas de 600–800 g y de que la mejor época es abril–junio y septiembre–diciembre. No contrastado |

## 7. Diseño para la app

### 7.1 (a) Check-in de 30 minutos: máximo 5 preguntas de un toque

| # | Pregunta | Botones |
|---|---|---|
| 1 | ¿Señales de carpa? | Ninguna · Indirectas (burbujas o barro) · Vistas o saltos |
| 2 | ¿Actividad en las cañas? | Nada · Toques · Picada fallada · Captura (abre una ficha rápida) |
| 3 | ¿Estado del cebo? (solo si se ha recogido) | Intacto · Mordisqueado · Desaparecido o roto · No revisado |
| 4 | ¿Ha cambiado algo del tiempo? | No · Viento · Nubes o sol · Lluvia (pre-rellenado con Open-Meteo; el usuario confirma) |
| 5 | ¿Has cambiado algo tú? | No · Cebo · Montaje · Columna · Distancia · Zona |

Opcional, en una línea plegada: temperatura del agua y número de pescadores a la vista.

### 7.2 (b) Plantilla de reglas SI→ENTONCES

```
REGLA: ALT_SIN_SENALES_VERANO
SI: fase_estacional = verano Y senal_visual = ninguna Y actividad_anzuelo = nada
    Y minutos_desde_ultimo_cambio >= 60
ENTONCES:
  columna: pasar 1 caña (B) a pop-up alto o zig ½ profundidad
  distancia: si ya se probó columna → mover caña B a primera caída
  cebado: no añadir
  presentacion: sin cambios en caña A (control)
  evitar: cambiar las 3 cañas a la vez
EVIDENCIA: 🟣 (apoyo indirecto 🟢 Benito et al. 2015; Hennen et al. 2014)
FUENTE/MOTIVO: problema probable = ubicación/capa
CAMBIA_VALORACION: no (solo propone alternativa)

REGLA: ALT_SENALES_SIN_PICADA
SI: senal_visual ∈ {indirecta, directa} Y actividad_anzuelo = nada
    Y minutos_desde_ultimo_cambio >= umbral_presentacion[fase]
ENTONCES:
  presentacion: cebo de anzuelo más pequeño o de contraste (maíz/pop-up); pelo más corto
  distancia: recolocar sobre el punto exacto de las señales
  cebado: mínimo (un puñado/PVA)
  evitar: cambiar de zona
EVIDENCIA: 🟢 (encuentro ≠ captura: Monk & Arlinghaus 2017) + 🟣 umbral
CAMBIA_VALORACION: no

REGLA: ALT_TOQUES
SI: actividad_anzuelo ∈ {toques, picada_fallada} en ≥2 check-ins consecutivos
ENTONCES:
  presentacion: cambiar montaje (anzuelo/longitud de bajo/pelo); probar cebo de anzuelo distinto en sabor/textura
  cebado: mantener
  evitar: aumentar el cebado
EVIDENCIA: 🟡 (+🟢 gusto decide aceptación: Kasumyan 2003/2009)
CAMBIA_VALORACION: no

REGLA: MANTENER_TRAS_CAPTURA
SI: actividad_anzuelo = captura
ENTONCES:
  todo: repetir posición y cebo; recebar poco
  aviso: "La capturabilidad puede bajar tras capturas repetidas (aprendizaje)"
  reiniciar minutos_desde_ultimo_cambio
EVIDENCIA: 🟢 (Monk & Arlinghaus 2017; Lovén Wallerius et al. 2020)
CAMBIA_VALORACION: sí (sube la valoración de la zona/cebo en el historial)

REGLA: PACIENCIA_INVIERNO
SI: fase_estacional = invierno Y minutos_desde_ultimo_cambio < 150
    Y (senal_visual ≠ ninguna O actividad en últimas 2 h)
ENTONCES:
  todo: no cambiar; mensaje "En frío las carpas comen poco y en pocos puntos: espera"
EVIDENCIA: 🟢 fisiología (Garcia & Adelman 1985) + agregación (Bajer et al. 2011) → 🟣 umbral
CAMBIA_VALORACION: no

REGLA: CANGREJO_O_PEQUENOS
SI: estado_cebo = desaparecido_o_roto en ≥2 revisiones
ENTONCES:
  presentacion: boilie endurecido/chufa preparada/imitación; o pop-up 10–20 cm
  cebado: partículas duras, menos pellet blando
  evitar: cebos de pescado/carne; lombriz
EVIDENCIA: 🟡 (experiencia Eurocarp; sin estudio)
CAMBIA_VALORACION: no

REGLA: NIVEL_BAJANDO
SI: nivel_tendencia = baja Y fase_estacional ∈ {verano, calor_extremo}
ENTONCES:
  distancia: priorizar primera caída sobre borde somero
  evitar: zonas someras sin datos recientes
EVIDENCIA: 🟣 (apoyo indirecto: Taylor et al. 2012)
CAMBIA_VALORACION: sí (reduce peso de zonas someras)

REGLA: FIN_HORARIO_LEGAL
SI: minutos_hasta_fin_legal <= 45
ENTONCES:
  evitar: cambios de zona; proponer solo presentación
  aviso a 15 min: "Fin del horario legal en Brovales (1 h tras ocaso)"
EVIDENCIA: ⚖ Ley 11/2010 art. 36.1; OGV art. 9.1
CAMBIA_VALORACION: no

REGLA: PRESION_ALTA
SI: presion_pesca = alta
ENTONCES:
  umbral_presentacion[fase] -= 30 min
  presentacion: cebos de anzuelo menos habituales, tamaño reducido
  cebado: mínimo
EVIDENCIA: 🟢 aprendizaje social/privado (Lovén Wallerius et al. 2020) + 🟣 ajuste
CAMBIA_VALORACION: no
```

### 7.3 (c) Escalera resumida (🟣, de lectura en el banco)

1. 0–X min (X según la fase): mantener la caña A = recomendación.
2. Sin señales: caña B → otra columna; luego otra distancia.
3. Sigue sin señales: caña C (exploración) → otra zona por función. Si C da señales, mover A y B allí.
4. Señales sin picada: cambiar la presentación en A y B; no mover.
5. Toques: cambiar el montaje.
6. Captura: congelar la configuración.

### 7.4 (d) Tabla de sustitución en la app

La tabla de §5.2, convertida en JSON con estos campos: cebo, funciones[], fases_validas[], selectividad (1–3), resistencia_cangrejo (1–3), preparacion_obligatoria, legal_extremadura, evidencia.

### 7.5 (e) Registro y aprendizaje con 20–40 sesiones

- Unidad de medida: capturas por hora-caña (CPUE). Registrar también las picadas por hora-caña, porque las capturas serán escasas.
- Protocolo de tres cañas:
  - A: recomendación de la app;
  - B: alternativa de un solo peldaño;
  - C: exploración (otra zona, columna o cebo).
- Hay que rotar cuál ocupa cada posición física para que el puesto no confunda el efecto. ⚖ Las tres deben estar en ≤10 m de orilla, lo que limita C a «otra distancia o columna», no «otra zona». La exploración de otra zona exige mover las tres cañas.
- Registro de cada cambio: hora, caña, variable cambiada (solo una), valor anterior y nuevo, y estado de las variables de check-in.
- Análisis:
  - Con 20–40 sesiones y pocas capturas, la potencia estadística es baja (🟣). Conviene usar medias con contracción (shrinkage) bayesiana hacia el prior de la estrategia, y no cambiar una regla mientras haya menos de unas 10 sesiones con esa condición.
  - Mostrar intervalos, no rankings.
  - Hay que tener en cuenta el aprendizaje de los peces: un cebo que funciona «al principio» puede dejar de hacerlo (🟢).

### 7.6 (f) Límites y avisos obligatorios

- «Los tiempos de cambio son hipótesis 🟣: no hay estudios que los fijen.»
- «La batimetría de Brovales no está verificada.»
- «Sin datos de oxígeno, estratificación ni cangrejo rojo en Brovales.»
- «Normativa revisada hasta marzo de 2026; confirma con el Servicio de Pesca y con los carteles del embalse.»
- «La app no garantiza capturas; las carpas aprenden a evitar los montajes.»
- Bloqueo ⚖ de cebos prohibidos y aviso de preparación de la chufa y las legumbres.

### 7.7 (g) Avisos cada 30 minutos sin molestar

- Solo vibración, sin sonido, con una notificación expandible para responder sin abrir la app (y Wear OS si está disponible).
- Intervalo adaptativo: 45–60 min en invierno o si las últimas 3 respuestas fueron «nada/ninguna» sin cambios. Pausa automática de 20 min tras pulsar «Captura».
- Saltar el check-in si el usuario ha registrado algo en los últimos 15 min.
- Una sola notificación agrupada, sin cadenas de avisos. Las propuestas de cambio solo aparecen cuando una regla se activa, no en cada check-in.
- Modo «no molestar» con un toque y cierre automático al acabar el horario legal.

## 8. Recommendations

1. Implementar ya las reglas B, C, D y FIN_HORARIO_LEGAL (base 🟢/⚖) y PACIENCIA_INVIERNO.
2. Activar como 🟣 experimentales los umbrales de tiempo y la regla de nivel bajando, con un registro A/B/C obligatorio.
3. Pre-cebado de 2–3 días como opción de estrategia (🟢 Kasumyan; Bajer). Requiere confirmar que el cebado es legal en Brovales.
4. Pedir por escrito al Servicio de Pesca:
   - si Brovales o Valuengo se consideran «embalse de abastecimiento»;
   - si se permiten pescado o mejillón de mar como cebo;
   - el anexo completo de la Resolución de 18/03/2026.
5. Levantar una batimetría propia con plomo marcador o sonda y registrar la temperatura del agua en cada sesión.

## 9. Caveats

- Casi toda la telemetría procede de lagos de Norteamérica o del centro de Europa, o del Ebro (un embalse fluvial). Su transferencia a un embalse monomíctico del suroeste es 🟣.
- La fisiología (ingesta, digestibilidad, gusto) procede de la acuicultura o del laboratorio, con peces jóvenes.
- La eficacia comparada de cebos se basa en un solo estudio con caña (maíz frente a masa y pescado); no hay ensayos de boilie frente a partícula.
- Las fuentes de pescadores (Eurocarp, fabricantes, blogs) son 🟡 y algunas tienen interés comercial.

## 10. Huecos de conocimiento y puntos a confirmar

| Hueco | Prioridad | Cómo cerrarlo |
|---|---|---|
| Lista oficial de «embalses de abastecimiento» (Brovales y Valuengo) | Alta ⚖ | Consulta escrita al Servicio de Pesca y Acuicultura |
| Alcance de «cuando sean de río» (pescado o mejillón de mar) | Media ⚖ | Ídem |
| Anexo completo de la Resolución de 18/03/2026 | Media ⚖ | DOE núm. 59, de 26/03/2026 |
| Batimetría de Brovales | Alta | Sonda o plomo; Confederación Hidrográfica del Guadiana |
| Oxígeno y estratificación en verano | Alta | Programa de seguimiento de la CHG o medición propia |
| Presencia de cangrejo rojo y tortugas | Media | Registro de estado_cebo |
| Tiempo hasta la primera picada con cebado al momento | Alta 🔴 | Se calibra con las sesiones (no existe estudio) |
| Efecto del viento sobre la ubicación de la carpa | Media 🔴 | Registro en la app |

## 11. Fuentes consultadas

- Bajer, Lim, Travaline, Miller y Sorensen 2010, Environ. Biol. Fishes 88:295–300 (citado a través de Ghosal et al. 2018 y Hundt et al. 2022).
- Ghosal, Eichmiller, Witthuhn y Sorensen 2018, Ecology and Evolution.
- Hundt et al. 2022, Ecology and Evolution.
- Bullers, Bajcz, Mensinger y Bajer 2026, Management of Biological Invasions 17(1):133–154.
- Kasumyan, Kuzishchin y Gruzdeva 2024, Journal of Ichthyology 64:689–704 (atrayentes químicos para carpa silvestre, río Ajtuba).
- Kasumyan y Morsi 1996; Kasumyan y Døving 2003, Fish and Fisheries 4:289–347; Kasumyan et al. 2009.
- Monk y Arlinghaus 2017, PLoS ONE (Kleiner Döllnsee).
- Lovén Wallerius et al. 2020, TAFS 149:498–511.
- Klefoth, Pieterek y Arlinghaus 2013, Fish. Manag. Ecol.
- Beukema 1970 (capturabilidad unas 3 veces menor un año después de la captura).
- Žák 2021, Fisheries Research 243:106086.
- Benito, Benejam, Zamora y García-Berthou 2015, TAFS 144:491–501.
- Penne y Pierce 2008, TAFS 137:1050–1062.
- Bajer, Chizinski y Sorensen 2011, Fish. Manag. Ecol. 18:497–505.
- Taylor, Tracey, Hartmann y Patil 2012, Mar. Freshw. Res. 63:587–597.
- Hennen et al. 2014, NAJFM.
- «Network analysis of intra- and interspecific freshwater fish interactions using year-around tracking», J. R. Soc. Interface 18(183):20210445 (2021), doi:10.1098/rsif.2021.0445.
- Garcia y Adelman 1985, J. Fish Biol.
- Yamamoto et al., Aquaculture (autoselección de macronutrientes).
- Ateşşahin y Dürrani 2023, «Effects of hook size and bait type on the size selectivity and short-time post-release mortality of Cyprinus carpio», Fisheries Research 261:106640.
- Ley 11/2010 de Pesca y Acuicultura de Extremadura (BOE-A-2010-19048, texto consolidado).
- Orden General de Vedas de 7/11/2022 (DOE de 16/11/2022).
- Resoluciones de 14/01/2025 y 18/03/2026.
- Portal pescayrios.juntaextremadura.es: especies pescables, Plan Técnico de Gestión del coto de Valuengo y listado de cotos.
- Nota de la Junta de 30/03/2026.
- Ayuntamiento de Jerez de los Caballeros (hidrografía).
- infoembalse y embalses.net.
- Pérez-Bote, Pula y Cascos 2000, «Distribución del cangrejo rojo Procambarus clarkii en Extremadura», Graellsia 56:71–78.
- Eurocarp (experiencia con cangrejo).
- Guías de preparación de partículas (OMC Tackle, CC Moore, BankSide).
