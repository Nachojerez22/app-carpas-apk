# CLAUDE.md — App Android de apoyo a la pesca de carpa (Brovales, Extremadura)

> Este documento es la única fuente de verdad del proyecto. Procede de dos conversaciones (ChatGPT y Gemini) que fueron **revisadas y corregidas** contra fuentes primarias. Si algo aquí contradice lo que "sabes" de carpfishing, **manda este documento**. No inventes datos de Brovales ni reglas sin evidencia.

---

## 0. Reglas de trabajo para Claude Code (leer primero)

1. **La app es un diario de sesiones + estimador de condiciones. NO es un oráculo de "dónde están las carpas".** Nunca prometas localizar peces. Usa lenguaje tipo "condiciones más o menos favorables según tus datos".
2. **Ninguna variable sin evidencia entra con peso > 0.** Luna, presión barométrica, seiches y "viento nuevo" se *registran* pero pesan 0 hasta que los datos del usuario justifiquen subirlas.
3. **No sumes puntos.** El modelo es una **cadena de filtros y multiplicadores** (ver §4). Si un nivel inferior es bajo, los superiores no lo compensan.
4. **Etiqueta de evidencia obligatoria** en cada regla y en cada texto que la app muestre al usuario: 🟢 fuerte · 🟡 moderada/hipótesis operativa · 🔴 mito/insuficiente · 🟣 hipótesis local pendiente de validar · 🔵 variabilidad individual.
5. **No muestres datos de Brovales que no estén en §6.1 (verificados).** Lo estimado (§6.2) se muestra marcado como "estimado". Lo no verificable (§6.3) no se muestra.
6. **La legalidad es un filtro duro (§7).** La app no debe recomendar sesiones fuera del horario legal.
7. **Privacidad:** GPS en local por defecto; si se comparte algo, redondear a zonas. Pedir permiso de ubicación solo durante el uso.
8. **Todo dato de terceros puede fallar o llegar con retraso.** Diseña para degradar con elegancia (sin red, API caída, dato semanal viejo).
9. Antes de cada temporada hay que revisar la normativa (cambia por resolución). Mostrar "última revisión: <fecha>" en pantalla de normativa.

Stack sugerido (decidible por el usuario): Kotlin + Jetpack Compose + Room (SQLite local) + WorkManager + Retrofit/Ktor. Todo funcionando offline salvo la descarga de meteorología/nivel.

---

## 1. Resumen ejecutivo del conocimiento

- La **temperatura del agua** es la variable maestra, y la única cuantificada en carpa salvaje: la evacuación del tubo digestivo pasó de **6,0 %/h a 9 °C a 32,7 %/h a 26,5 °C**, y la temperatura explicó el 72–91 % de la variación (Garcia y Adelman 1985, J. Fish Biol.). El consumo diario subió del 0,39 % del peso corporal (abril, 14 °C) al 4,08 % (agosto, 26,5 °C).
- La carpa **tolera muy bien la hipoxia**: en el embalse de Flix (Ebro) usó extensamente aguas con <1,1 mg/L de O₂ (Benito et al. 2015, TAFS 144:491–501); la FAO indica supervivencia a 0,3–0,5 mg/L. "Poco oxígeno" ≠ "sin carpas".
- El patrón **día/noche depende del sistema**: no hay ley universal. En Flix, de noche en el fondo y de día en aguas someras (<3 m) en verano; en el río Perla, al revés (más de noche y en aguas someras cerca de orillas). En 14 competiciones europeas las capturas fueron más frecuentes de noche (Žák 2021).
- La **evitación del anzuelo** es real (aprendizaje propio y social), pero **no hay evidencia de que dure más de 7 meses** (Czapla et al. 2023).
- Para **luna, presión barométrica, seiches y "viento nuevo" no hay evidencia publicada en carpa.** Son hipótesis.
- Sobre **Brovales** casi no hay datos limnológicos accesibles (estado trófico, estratificación, oxígeno). Todo lo que se diga sobre su cola en verano o sobre "lo que saben los locales" **no es verificable**.
- **Legal en Brovales:** solo se puede pescar desde 1 h antes del orto hasta 1 h después del ocaso. **Nada de pesca nocturna.**

---

## 2. Etiquetas de evidencia

| Etiqueta | Significado | Peso inicial en el modelo |
|---|---|---|
| 🟢 | Resultado repetido en telemetría/experimentos/estudios ecológicos | Alto/medio |
| 🟡 | Mecanismo razonable + alguna evidencia, sin demostrar en carpa/embalse concreto | Bajo/medio, validar con datos |
| 🔴 | Mito o evidencia insuficiente | **0** (solo registrar) |
| 🟣 | Hipótesis local de Brovales/Extremadura pendiente de validar | Bajo, validar con datos del usuario |
| 🔵 | Variabilidad individual entre ejemplares | No determinista: no prometer |

---

## 3. Tabla de verificación (36 afirmaciones)

Veredictos: CONFIRMADA · PARCIAL · PLAUSIBLE (no demostrada) · CONTRADICHA · SIN DATOS.

| # | Afirmación (origen) | Veredicto | Evidencia | Base |
|---|---|---|---|---|
| 1 | La carpa no puede estar en el hipolimnion anóxico, "moriría asfixiada" (Gemini) | **CONTRADICHA** (forma absoluta) | 🟢 contra | Benito et al. 2015: Flix, uso extenso de aguas <1,1 mg/L. FAO: sobrevive a 0,3–0,5 mg/L. Matiz: anoxia total prolongada sí es letal; en hipoxia invernal en estanques dejó de alimentarse y se desplazó a zonas someras junto a la entrada de agua (Bauer y Schlott 2006) |
| 2 | Corrección de ChatGPT citando Benito et al. 2015 | **CONFIRMADA** | 🟢 | Ojo: Flix es embalse fluvial con poco tiempo de residencia y siluros; la estancia nocturna en el fondo se atribuye a evitar depredadores. No extrapolable a Brovales |
| 3 | Brovales estratificado de forma estable y eutrófico | **SIN DATOS** | 🟣 | E-T04 es categoría administrativa, no medición. Profundidad máx. ~15–17 m a embalse lleno (cauce 288 m, coronación 305 m): estratificación posible junto a la presa, improbable en colas someras. Sin clorofila/Secchi/perfiles de O₂ accesibles |
| 4 | A <5–8 °C la comida "se pudre en el intestino" (Gemini) | **CONTRADICHA / mito** | 🔴 | Digestión se ralentiza pero continúa (6 %/h a 9 °C). Carpa agástrica: hablar de "tránsito del tubo digestivo", no "evacuación gástrica" |
| 5 | A 8 °C la tasa es "5–8 %/h" (Gemini) | **PARCIAL** | 🟡 | Orden de magnitud coherente con 6,0 %/h a 9 °C; a 8 °C es extrapolación |
| 6 | En frío "no puede procesar grandes cebaderos" | **PLAUSIBLE (mal formulada)** | 🟡 | La razón es demanda baja (~0,4 % peso/día a 14 °C), no incapacidad digestiva |
| 7 | 1 hPa ≈ 1,02 cm de columna de agua | **CONFIRMADA** | física | −20 hPa ≈ 20 cm de profundidad |
| 8 | La carpa es fisóstoma | **CONFIRMADA** | 🟢 | Conducto neumático: ajusta la vejiga natatoria tragando/expulsando aire. Resta peso al argumento barométrico |
| 9 | Presión baja "reduce la tensión superficial" y sube el zooplancton (Gemini) | **CONTRADICHA** | física | La tensión superficial depende de temperatura y surfactantes, no de la presión atmosférica |
| 10 | "Factor x2" a orilla somera con presión baja (Gemini) | **SIN DATOS / inventado** | 🔴 | Ningún estudio lo respalda |
| 11 | Reglas lunares: llena → cauce; nueva → meseta somera (Gemini) | **SIN DATOS** | 🔴 | Sin estudios en carpa. En otras especies efectos pequeños frente a hora, presión de pesca y temperatura |
| 12 | "Viento nuevo", fetch, seiche, upwelling como "autopista trófica predecible" 🟢 (Gemini) | **PLAUSIBLE (no demostrada)** | 🟡 | Física establecida en lagos estratificados; sin estudios en carpa. En un vaso de ~1,5 km² somero el fetch es corto |
| 13 | Viento cálido acumula agua cálida a sotavento; viento frío espanta | **PARCIAL** | 🟡 | Arrastre superficial a sotavento: físico. "El frío espanta": experiencia de pescadores |
| 14 | Entradas frías se hunden por el cauce; cálidas flotan; heladas se hunden | **CONFIRMADA (física)** | 🟢 física | Hidráulica de embalses (plunging/interflow). "Rompe la termoclina local" es exagerado |
| 15 | Atracción a entradas de agua / "reotaxia positiva" | **PARCIAL** | 🟡 | En Flix, con caudales altos menos activas y más someras; prefieren corriente lenta. En hipoxia invernal se desplazaron a la entrada de agua |
| 16 | La carpa "odia el agua 100 % chocolate" y busca el borde de la pluma | **PLAUSIBLE** | 🟡 | Experiencia de pescadores. Contradice la afirmación #34 |
| 17 | Vuelco otoñal = "días muy duros" (Gemini) vs oct–nov buenos (ChatGPT) | Gemini: **SIN DATOS** · ChatGPT: **PARCIAL** | 🟡 | El vuelco puede dar incertidumbre puntual; "buena época" es experiencia de pescadores |
| 18 | Caída superficial >4 °C en 3 días = vuelco en curso | **SIN DATOS** | 🟣 | Un frente frío produce lo mismo sin vuelco; hace falta perfil vertical |
| 19 | Hipoxia pre-amanecer en reculas con macrófitos densos | **PLAUSIBLE** | 🟡 | Limnología básica (ciclo diario de O₂). Que las carpas abandonen la zona no está medido |
| 20 | Dieta: cangrejo rojo, Corbicula, bentos; carpas grandes patrullan fondos duros en otoño | Dieta omnívora bentónica: **CONFIRMADA** · Cangrejo/Corbicula en Brovales: **SIN DATOS** · Patrullas otoñales: **PLAUSIBLE** | 🟢/🟣 | FAO: omnívora con tendencia a alimento animal |
| 21 | Carpas grandes: más cautelosas, metabolismo relativo menor → menos cebadero masivo | **PARCIAL** | 🟡 | Alometría general sí; la cautela puede deberse a aprendizaje/selección; la regla de cebado es hipótesis |
| 22 | Wallerius et al. 2020: evitación por aprendizaje privado y social | **CONFIRMADA** | 🟢 | TAFS 149(4):498–511. Reducción de vulnerabilidad ~57–60 % tras captura previa; evitación social ≥7 días. Las cifras "57–74 %" de blogs no coinciden con el original |
| 23 | Duración de la evitación | **CORREGIDA** | 🟢 | Czapla et al. 2023 (Fish. Res. 259:106573): sin evitación más allá de 7 meses. El "un año" viene de Beukema 1970 (estanques) |
| 24 | Carpas "filmadas comiendo alrededor del cebo con anzuelo", fluorescentes, abanicar con pectorales | **PLAUSIBLE (no verificada)** | 🟡 | Wallerius 2020 sí describe discriminación visual/táctil del montaje |
| 25 | Más capturas de noche (14 competiciones europeas) | **CONFIRMADA** | 🟡 | Žák 2021, Fish. Res. 243:106086. Datos de competición con cebadero intenso |
| 26 | Picos de alimentación 08–11 h y 19–23 h | **SIN DATOS verificados** | 🔴 | Fuente primaria no localizada. **No usar** |
| 27 | De día profundas, de noche en orilla somera (Gemini) | **CONTRADICHA como regla general** | 🟢 contra | Opuesto en Flix. Varía según el sistema |
| 28 | Desove a ~17–21 °C | **CONFIRMADA** | 🟢 | FAO: inicio 17–18 °C; óptimo 18–22 °C |
| 29 | Crecimiento óptimo 23–30 °C | **CONFIRMADA** | 🟢 | FAO |
| 30 | 28 °C = máximo de alimentación en laboratorio | **SIN DATOS verificados** | 🔴 | **No usar como umbral duro** |
| 31 | Agregaciones invernales en zonas más profundas | **CONFIRMADA** (otros sistemas) | 🟡 | Telemetría de un año en lago; Flix |
| 32 | Con bajada de nivel, se refugian en el antiguo cauce | **PLAUSIBLE** | 🟣 | Trivial que dejen la recula seca; que vayan al cauce y no a otras zonas de 2–4 m no está medido |
| 33 | Subida de nivel con pasto inundado atrae a las carpas | **PLAUSIBLE** | 🟡 | Coherente con ecología de llanura de inundación; como predictor de alimentación es experiencia |
| 34 | Con agua turbia tras lluvia, "adora" la turbidez y se alimenta de día | **PLAUSIBLE** | 🟡 | Experiencia de pescadores; choca con #16 |
| 35 | Un choque térmico rompe la termoclina | **PARCIAL** | física | Puede profundizar la capa de mezcla; romper toda la termoclina requiere evento intenso. Sin perfiles es especulación |
| 36 | Modelo de 5 niveles (vive → motivos → presente → se alimenta → interceptable) | **BUENA ARQUITECTURA** | diseño | Adoptar, ver §4 |

### 3.2 Hallazgos añadidos (alternativas durante la sesión, oct. 2026)

Fuente completa: [investigación de alternativas, oct. 2026](investigacion/2026-10-alternativas-sesion.md). Numeración continua con la tabla anterior.

| # | Afirmación | Veredicto | Etiqueta | Fuente y matiz |
|---|---|---|---|---|
| 37 | Las carpas tardan 2–4 días en concentrarse en un punto cebado nuevo (respuesta aprendida) | **CONFIRMADA** | 🟢 | Bajer et al. 2010 (3–4 días; 21 de 34 carpas la décima noche); Kasumyan et al. 2024 (2–3 días, estable a la semana); Bullers et al. 2026 (~3 días). En una sesión de horas no se «construye» un comedero desde cero |
| 38 | Tras empezar el cebado, las carpas visitan el punto y comen sobre todo de noche | **CONFIRMADA** | 🟢 | Ghosal et al. 2018 (densidad ×2, ADN ambiental ×500). En Brovales la noche no es pescable |
| 39 | Encontrar el cebo no garantiza picadas: la capturabilidad cae tras los primeros días | **CONFIRMADA** | 🟢 | Monk y Arlinghaus 2017 (telemetría). «Señales sin picadas» = presentación o desconfianza, no ubicación |
| 40 | La evitación del anzuelo se adquiere en pocos días, también por aprendizaje social | **CONFIRMADA** | 🟢 | Klefoth, Pieterek y Arlinghaus 2013; Beukema 1970 (capturabilidad ~3 veces menor un año después). Compatible con #22 y con el límite de ≤7 meses de Czapla et al. 2023 |
| 41 | Con calor extremo la ingesta baja (significativo >34 °C) | **CONFIRMADA (acuicultura)** | 🟢 | Revisión de ritmo alimentario. En Brovales no se espera >34 °C en el fondo; >28 °C reduce el margen (🟣) |
| 42 | Las carpas se agregan lejos de la orilla en otoño-invierno (<10 °C; densas <5 °C) y las agregaciones se mueven | **CONFIRMADA** | 🟢 | Penne y Pierce 2008; Bajer et al. 2011. En Brovales el agua rara vez bajará de 5 °C (🟣) |
| 43 | Profundidad media estacional: ~5 m en invierno frente a ~2 m el resto del año (lago alemán) | **CONFIRMADA (otro sistema)** | 🟢 | J. R. Soc. Interface 18(183):20210445 (2021). Patrón general; batimetría de Brovales sin verificar |
| 44 | Velocidad de movimiento 100–166 m/h; agregación somera en mayo, junio y agosto y dispersión en julio | **CONFIRMADA (otro sistema)** | 🟢 | Hennen et al. 2014 (Dakota del Sur). La espera depende de si hay peces cerca, no de la difusión del olor (🟣) |
| 45 | El maíz en grano captura 2,1–3,4 veces más que la masa y el pescado; el tipo de cebo no cambia la talla, el anzuelo sí | **CONFIRMADA (un estudio)** | 🟢 | Ateşşahin y Dürrani 2023, Fish. Res. 261:106640. «Cebo más selectivo por talla» es 🟡 |
| 46 | El olor atrae y el gusto decide la aceptación (~29 % de aminoácidos libres estimulantes; L-cisteína muy estimulante) | **CONFIRMADA (laboratorio)** | 🟢 | Kasumyan y Morsi 1996; Kasumyan y Døving 2003; Kasumyan et al. 2009. Argumento para cambiar el cebo de anzuelo con toques sin clavada |
| 47 | A 17 °C eligen más grasa y a 25 °C más proteína; la grasa se digiere peor a 17 °C | **CONFIRMADA (acuicultura)** | 🟢 | Yamamoto et al. 🟣 En frío: cebos digeribles y poca cantidad; no trasladar la «preferencia por grasa» al anzuelo |
| 48 | Existe una regla probada de cada cuánto cambiar de puesto, cebo o montaje durante una sesión | **SIN DATOS** | 🔴 | Ningún estudio compara rotar con mantener. Los tiempos de §5.9 son 🟣 a calibrar |

### 3.3 Viento (afirmaciones añadidas, oct. 2026)

Fuente completa: [informe de viento, oct. 2026](investigacion/2026-10-viento.md). Numeración continua con la tabla anterior.

| # | Afirmación | Veredicto | Etiqueta | Fuente y matiz |
|---|---|---|---|---|
| 49 | El viento empuja el agua superficial hacia la orilla que lo recibe y engrosa allí la capa de arriba (termoclina inclinada) | CONFIRMADA | 🟢 | Física de lagos (HESS 2020; número de Wedderburn, Imberger y Patterson). [copernicus](https://hess.copernicus.org/articles/24/3399/2020/) En Brovales la magnitud es pequeña por el fetch corto y no está medida. |
| 50 | El agua cálida de superficie se acumula a sotavento | CONFIRMADA en lagos estratificados; magnitud en Brovales SIN DATOS | 🟢 física / 🟣 local | Tesis de la Univ. de Toronto sobre el lago Opeongo: «zooplankton and warm water both accumulate downwind, but… the time scales… are different». [scholaris](https://utoronto.scholaris.ca/server/api/core/bitstreams/ad77494b-6253-46e5-9693-4e2665fef357/content) Requiere días de sol con capa cálida arriba. Con mezcla completa no hay nada que acumular. |
| 51 | El alimento (plancton) se acumula en la orilla que recibe el viento | PARCIAL | 🟡 | Zooplancton pequeño +8–34 % a sotavento (Opeongo, 22,1 km²). El grande no depende del viento. [harvard](https://ui.adsabs.harvard.edu/abs/2022FrBio..67..991C/abstract) [researchgate](https://www.researchgate.net/publication/228524241_Towards_understanding_the_relationship_between_wind_conditions_and_plankton_patchiness) En el fitoplancton el efecto se debe sobre todo a la dilución en barlovento (Cyr 2017). [sciencedirect](https://www.sciencedirect.com/science/article/abs/pii/S004313541730430X) Medido en lagos más de 10 veces mayores que Brovales. |
| 52 | En la orilla de donde viene el viento aflora agua profunda (upwelling) | CONFIRMADA si hay estratificación | 🟢 física / 🟣 local | Upwelling a barlovento y downwelling a sotavento. [copernicus](https://hess.copernicus.org/articles/24/3399/2020/) En verano, si el fondo de Brovales tuviera poco oxígeno, el afloramiento llevaría agua fría y pobre en O₂ a esa orilla. Es una hipótesis: no hay perfiles de O₂. |
| 53 | El oleaje en orillas someras y blandas enturbia y destapa alimento | CONFIRMADA (física) / PLAUSIBLE (atracción de carpa) | 🟢 / 🟡 | Umbrales de resuspensión: 3,7–4,0 m/s con fetch largo (Taihu; Zheng et al. 2015) y >2 m/s en un lago de 1,5 m y 1,6 km (Thomsons Lake; Bailey y Hamilton 1997). En alevines de ciprínido (dace) más oleaje = más contenido intestinal (Stoll et al. 2010). [wiley](https://onlinelibrary.wiley.com/doi/10.1111/j.1095-8649.2010.02611.x) No hay datos de carpa adulta. |
| 54 | El viento oxigena y mezcla | CONFIRMADA la mezcla; efecto sobre la pesca SIN DATOS | 🟢 / 🔴 peso 0 para «oxígeno → capturas» | Con menos viento hay más estratificación diurna (Martinsen et al. 2019). [researchgate](https://www.researchgate.net/publication/327100188_Water_temperature_dynamics_and_the_prevalence_of_daytime_stratification_in_small_temperate_shallow_lakes) Que eso suba las capturas de carpa no está medido. |
| 55 | Las seiches crean momentos de pesca («cuando el agua vuelve») | CONTRADICHA para Brovales | 🔴 | Seiche superficial de ≈7–8 min y cm de amplitud (Merian). La interna depende de una estratificación no verificada. |
| 56 | «La carpa sigue al viento» (regla general) | SIN DATOS científicos | 🔴 como regla / 🟡 como experiencia | Ningún estudio de telemetría ni de capturas de carpa lo prueba. Los propios autores de carpfishing lo matizan: «Carp that inhabit smaller lakes are less influenced by the strength and direction of the wind» (Haiths 🟡). [haiths](https://haiths.com/blogs/fishing/wind-and-wuthering-haiths) En un lago de 10 acres todas las carpas siguieron toda la semana en la misma zona aunque el viento cambió varias veces (Angling Lines 🟡). [anglinglines](https://www.anglinglines.com/blog/set-in-stone-i-dont-think-so-part-1/) |
| 57 | Un «viento nuevo» (cambio de dirección) mueve y activa a las carpas | SIN DATOS | 🔴 peso 0 / 🟡 experiencia | «they will almost always move from their current position when a new wind arrives» (Tom Dove, Korda, fabricante 🟡). [kordatackle](https://kordatackle.com/knowledge/location-is-key-tom-dove) No hay estudios. |
| 58 | El viento frío aleja a las carpas de la orilla que lo recibe y se van a la zona resguardada y soleada | PLAUSIBLE | 🟡 | Mecanismo físico coherente: enfría la orilla batida. Lo apoya la experiencia (Carpology, CARPdiem 🟡). [carpology](https://www.carpology.net/article/features/how-to-use-the-weather-as-an-advantage/) [carpdiem](https://carpdiem.es/estrategias-tecnicas-para-escenarios-masificados/) Sin estudios. |
| 59 | «Viento cálido de cara» funciona igual en el verano de Extremadura | SIN DATOS; probablemente depende de la temperatura del agua | 🟣 | La regla viene de aguas británicas frescas. Con el agua ya caliente, amontonar más calor puede no ayudar. El solano (E) es caliente y seco y aumenta la evaporación. Validar con datos propios. |
| 60 | «Cuanto más viento, más capturas» | CONTRADICHA como regla general | 🔴 | En Escanaba Lake (119 ha, 4,3 m), la CPUE de muskellunge bajó de 0,15 a 0,09 peces/h al pasar de ≤1 a ≥9 m/s (Shaw et al. 2021). [nih](https://pmc.ncbi.nlm.nih.gov/articles/PMC8483380/) Las cifras de black bass («más del doble con >15 mph») vienen de un libro divulgativo, no de un estudio revisado. [bassforecast](https://bassforecast.com/high-percentage-fishing-wind) |
| 61 | La duración importa: un viento de 24–48 h vale más que una racha | PLAUSIBLE | 🟡 | La acumulación de plancton a escala pequeña depende de la velocidad y la persistencia (Blukacz et al. 2010, Limnology & Oceanography, 150 transectos en el lago Opeongo). El umbral exacto de horas no tiene fuente: es una convención del documento. |
| 62 | Con fetch corto (cientos de m a 1,5 km) los efectos del viento son pequeños | CONFIRMADA en lo cualitativo | 🟢 | La termoclina depende sobre todo de la superficie (Gorham y Boyce 1989). [sciencedirect](https://sciencedirect.com/science/article/abs/pii/S0380133089714799?via=ihub%3D) La ola necesita fetch para remover el fondo. Las orillas de Brovales con más fetch serán las únicas con oleaje apreciable. |
| 63 | De cara se lanza más corto y con menos precisión; de espaldas, más lejos | CONFIRMADA (física y experiencia) | 🟡 | «El viento a tu espalda ayuda; el viento de cara hace que la precisión sea casi imposible» (FishRadar 🟡). [fishradar](https://fishradar.ai/es/learn/how-wind-affects-fishing/) Sin estudio formal, pero el mecanismo es obvio. |
| 64 | El viento lateral y el oleaje forman comba, arrastran el plomo y dan avisos falsos | CONFIRMADA (experiencia) | 🟡 | Con viento fuerte o lateral se recomiendan bobbins más pesados (Korda 🟡). [kordatackle](https://kordatackle.com/knowledge/the-complete-guide-to-bite-indication-for-carp-fishing) Los backleads ayudan con resaca («undertow») (Total Fishing 🟡), [total-fishing](https://www.total-fishing.com/bite-indication/) pero restan sensibilidad (Rob Hughes, Angling Times 🟡). [anglingtimes](https://www.anglingtimes.co.uk/advice/tips/carp-fishing-tips-or-slack-line-or-backlead-for-carp-rob-hughes/) |
| 65 | Con tormenta, la caña de carbono es un peligro y hay que dejar de pescar | CONFIRMADA | 🟢 | NOAA/NWS: la pesca es la primera actividad acuática recreativa en muertes por rayo (43 % de 76 casos, 2006–2016); esperar 30 min tras el último trueno. |
| 66 | La dirección y la velocidad del viento influyen en las capturas de pesca recreativa | PARCIAL | 🟡 | Sí en walleye y muskellunge (Escanaba), con efectos distintos según la especie. [doaj](https://doaj.org/article/fed3b791cae846e0b6f5b3d4532e11de) [plos](https://journals.plos.org/plosone/article?id=10.1371%2Fjournal.pone.0257882) No se puede extrapolar a la carpa. |

### 3.1 Correcciones a las fuentes originales (para no reintroducir errores)

**Errores de Gemini:** convertir mecanismos físicos reales (plunging, seiches, vuelco) en reglas 🟢 de localización sin estudios en peces; asfixia "segura" en el hipolimnion; putrefacción intestinal; tensión superficial y "Factor x2"; reglas lunares tras haberlas descartado; contradicciones sobre turbidez; presentar como hechos conocimientos locales de Brovales inexistentes; la "Demasía Hidrográfica del Guadiana" **no existe** (el organismo es la **Confederación Hidrográfica del Guadiana, CHG**).

**Matices a ChatGPT:** Benito et al. 2015 es correcto pero Flix no es modelo directo de Brovales; "oct–nov buenos" es experiencia de pescadores, no ciencia; la persistencia de evitación del anzuelo es ≤7 meses (no un año).

**Datos de partida del usuario:**
- Capacidad oficial **6,98 hm³** (nivel máximo normal). El 7,42 hm³ no se pudo confirmar. La web municipal da 8 hm³ (redondeo/error).
- Superficie oficial del embalse **158,7–159 ha**; **1,45 km²** es la masa de agua del Plan Hidrológico (otro criterio). Ambas valen en su contexto.
- Profundidad media calculada ~**4,4–4,8 m**: es un cálculo, no un dato publicado.
- Titularidad: "Junta de Extremadura" (ficha de presa) vs "Comunidad de Usuarios" (ficha de embalse): discrepancia entre fuentes.

---

## 4. Arquitectura del modelo (cadena de filtros, NO suma de puntos)

```
Nivel 0  LEGALIDAD            filtro duro (horario, cañas, cebado)
Nivel 1  HÁBITAT POSIBLE      profundidad disponible a la cota del día, zonas emergidas
Nivel 2  TEMPERATURA          temp. agua + tendencia 3–5 días  (variable principal)
Nivel 3  MODIFICADORES FÍSICOS viento (dir/vel), entradas tras lluvia, tendencia del nivel
Nivel 4  CAPTURABILIDAD       hora (amanecer/anochecer legales), presión de pesca, capturas recientes
Exploratorias (peso 0)         luna, presión barométrica y tendencia, nubosidad
```

Reglas de diseño:
- Si el nivel 0 falla → no recomendar sesión. Si nivel 1 o 2 son bajos, el resultado es bajo aunque el nivel 3–4 sea alto.
- Preferir **lógica difusa** (pertenencia suave de "agua fría/templada/cálida") a umbrales duros: los umbrales publicados son aproximados.
- La interacción importa: 22 °C en abril ≠ 22 °C en octubre; la misma recula al 90 % y al 40 % de nivel son dos hábitats distintos.
- Separar tres preguntas: **¿puede estar? / ¿come? / ¿es interceptable?** (presencia, actividad, capturabilidad).
- La salida siempre incluye **el porqué** (reglas activadas, con su etiqueta de evidencia).

### 4.1 Reglas iniciales (YAML)

```yaml
- id: horario_legal
  nivel: 0
  tipo: filtro_duro
  descripcion: Solo desde 1 h antes del orto hasta 1 h después del ocaso (Brovales no tiene horario libre)
- id: max_canas
  nivel: 0
  tipo: filtro_duro
  descripcion: Máx. 3 cañas en 10 m de orilla; 3 señuelos por caña
- id: cebado_embalse_abastecimiento
  nivel: 0
  tipo: aviso_duro
  descripcion: Prohibido cebar en embalses de abastecimiento (probable en Valuengo, CONFIRMAR)
- id: temp_agua_demanda
  nivel: 2
  evidencia: verde        # Garcia y Adelman 1985
  descripcion: Demanda alimentaria sube con la temperatura (6 %/h a 9 °C -> 32,7 %/h a 26,5 °C; 0,4 % -> 4,1 % peso/día)
  peso_inicial: alto
- id: temp_tendencia
  nivel: 2
  evidencia: amarillo
  descripcion: Tendencia térmica de 3-5 días modula la lectura de la temperatura actual
  peso_inicial: medio
- id: desove_17_21
  nivel: 2
  evidencia: verde        # FAO
  descripcion: Entre 17 y 21 °C posible concentración en someros con vegetación y menor alimentación (variable)
  peso_inicial: medio
- id: calor_extremo_oxigeno
  nivel: 2
  evidencia: amarillo
  descripcion: >28 °C con calma: bajar confianza y priorizar amanecer; NO descartar el fondo por "anóxico"
  peso_inicial: bajo
- id: nivel_tendencia
  nivel: 3
  evidencia: morado
  descripcion: Bajada rápida (~-1 hm3/semana en verano) reduce reculas someras; subida otoñal puede abrir litoral nuevo
  peso_inicial: medio
- id: entrada_agua_lluvia
  nivel: 3
  evidencia: amarillo
  descripcion: Lluvia relevante (escorrentía real) puede cambiar temperatura/oxigeno/alimento en colas; 20 mm sin escorrentia no cuentan
  peso_inicial: bajo
# Viento (corregido oct. 2026, §5.4): sustituye a «viento_sotavento». Cálido o frío respecto al agua.
- id: viento_calido_orilla_receptora
  nivel: 3
  evidencia: amarillo
  descripcion: Viento más cálido que el agua (aire − agua ≥ 2 °C, 🟣), ≥10 km/h, sostenido ≥12 h y agua por debajo del óptimo → orilla que lo recibe (física 🟢 de arrastre y plancton; sin estudios en carpa). Fetch corto en Brovales
  peso_inicial: bajo (desempate)
- id: viento_frio_resguardo
  nivel: 3
  evidencia: amarillo
  descripcion: Viento más frío que el agua (aire − agua ≤ −2 °C) → orilla resguardada y soleada, con el viento a la espalda
  peso_inicial: bajo
- id: viento_calido_agua_caliente
  nivel: 3
  evidencia: morado
  descripcion: Con el agua ya en el óptimo o por encima (verano), la orilla que recibe el viento cálido NO suma por temperatura; valorar sombra, profundidad, entradas y oleaje (oxígeno)
  peso_inicial: 0 (hasta tener datos)
- id: oleaje_orilla_somera_blanda
  nivel: 3
  evidencia: amarillo
  descripcion: De cara ≥15 km/h, fondo blando, orilla ≤2 m y fetch ≥500 m → el oleaje remueve el fondo (🟢 física; 🟡 alimento en ciprínidos jóvenes, Stoll et al. 2010; sin datos en carpa adulta)
  peso_inicial: muy bajo
- id: viento_nuevo
  nivel: 3
  evidencia: rojo
  descripcion: Cambio de dirección del viento. Sin estudios: registrar, no mover el puesto solo por eso
  peso_inicial: 0
- id: seiche
  nivel: 3
  evidencia: rojo
  descripcion: En Brovales la seiche superficial dura ≈7–8 min y mide centímetros (Merian); la interna exige estratificación no verificada
  peso_inicial: 0
- id: seguridad_tormenta
  nivel: 0
  evidencia: verde
  descripcion: Trueno audible o aviso de tormenta eléctrica → dejar de pescar (caña de carbono = conductor); volver 30 min después del último trueno (NOAA/NWS). Filtro de seguridad, no puntúa
  peso_inicial: filtro
- id: avisos_practicos_viento
  nivel: 4
  evidencia: amarillo
  descripcion: Lance, comba, indicación de picada y cebado según el viento (§5.9.12). Ayudan a pescar bien; no cambian dónde está el pez
  peso_inicial: 0
- id: hora_amanecer_anochecer
  nivel: 4
  evidencia: amarillo
  descripcion: Ventanas legales de amanecer y anochecer (el pico nocturno de competiciones NO es aplicable a Brovales)
  peso_inicial: medio
- id: presion_pesca_puesto
  nivel: 4
  evidencia: verde        # Wallerius 2020, Czapla 2023
  descripcion: Capturas repetidas en un puesto bajan capturabilidad semanas-meses (sin evidencia >7 meses); sugerir rotar
  peso_inicial: medio
- id: luna
  nivel: exploratoria
  evidencia: rojo
  peso_inicial: 0
- id: presion_barometrica
  nivel: exploratoria
  evidencia: rojo
  peso_inicial: 0         # solo como indicador de cambio meteorologico, nunca "X hPa = bueno"
- id: nubosidad
  nivel: exploratoria
  evidencia: rojo
  peso_inicial: 0
```

---

## 5. Estrategias (qué está respaldado y qué no)

> Las conversaciones originales sí daban estrategias, pero mezclaban fisiología demostrada, experiencia de pescadores y mecanismos físicos convertidos en reglas. Aquí van separadas. **La app debe mostrar siempre la etiqueta.**

### 5.1 Por temperatura del agua (lo mejor respaldado)

| Rango | Qué dice la ciencia (🟢) | Experiencia / hipótesis (🟡/🟣) |
|---|---|---|
| **<10 °C** | Ingesta muy baja; tránsito ~6 %/h a 9 °C | Poco cebo, cebos pequeños y muy atractivos, puesto fijo largo. Zonas algo más profundas/estables; días soleados tras varios estables; agua que primero se calienta por la tarde. Agregación invernal en cauce = hipótesis |
| **10–17 °C** (primavera/otoño) | La ingesta crece rápido con la temperatura | Orillas someras que se calientan antes son prioritarias en primavera. Cebado progresivo creciente |
| **17–22 °C** | Ventana de desove (17–18 °C inicio, óptimo 18–22 °C) | Concentración en someros con vegetación; posible menor alimentación durante el desove. La app avisa como "variable" |
| **22–28 °C** | Máxima demanda (hasta ~4 % peso/día a 26,5 °C) | Mayor sentido del cebado abundante; actividad en horas frescas |
| **>28 °C, O₂ bajo** | La carpa tolera hipoxia; no se puede descartar el fondo por "anóxico" | Bajar confianza en cualquier predicción; priorizar amanecer |

### 5.2 Por hora del día
- Hay picos nocturnos de captura en competiciones (Žák 2021) pero el uso de profundidad varía según el embalse (Flix vs río Perla).
- **En Brovales solo es legal pescar desde 1 h antes del orto hasta 1 h después del ocaso.** Las ventanas útiles son amanecer y anochecer *legales*. La app las calcula con SunCalc y **no ofrece horas nocturnas**.
- No usar "08–11 h y 19–23 h" (fuente no verificada).

### 5.3 Por nivel del embalse
- Con vaciado activo (~−1 hm³/semana en verano) las reculas someras pierden agua y el litoral cambia cada semana. 🟣 Plausible: desplazar cañas a primeras caídas o cambios de pendiente cercanos.
- En subidas otoñales con pasto inundado: 🟡 experiencia de pescadores, buscar el nuevo litoral.
- Registrar cota o % en cada sesión.

### 5.4 Por viento

> *(Ampliado oct. 2026 con el [informe de viento](investigacion/2026-10-viento.md); tabla #49–#66 en §3.3.)* Lo que importa no es si el aire te da de cara o en la espalda, sino **hacia qué orilla empuja el agua de arriba, si ese viento está más caliente o más frío que el agua y cuántas horas lleva soplando**.

**Modificador físico de segundo orden (desempate), no regla principal.**

- **Qué está demostrado (🟢, física de lagos):**
  1. El viento empuja la capa superficial hacia la orilla que lo recibe. Allí se engrosa la capa templada y, si hay estratificación, aflora agua profunda y fría en la orilla de donde viene el viento.
  2. El zooplancton pequeño y los flotantes tienden a acumularse en la orilla que recibe el viento (medido en lagos de más de 20 km², con matices).
  3. El oleaje remueve el fondo en orillas someras a partir de vientos bajos (≈2–4 m/s, según profundidad y fetch).
  4. El viento mezcla la columna: con calma, los lagos someros se estratifican de día.
- **Qué NO está demostrado:** que la carpa adulta «siga al viento» (🔴 como regla, 🟡 como experiencia), el «viento nuevo» (🔴, peso 0) y las seiches como momento de pesca (🔴, peso 0; en Brovales la seiche superficial dura ≈7–8 min y mide centímetros).
- **Escala local (🟣):** Brovales (~1,5 km², fetch ≤1–1,5 km, estratificación no verificada) tendrá efectos de orilla pequeños y que se disipan rápido. Antes que la dirección, mira la temperatura del agua y el hábitat.
- **Viento cálido o frío se define respecto al agua:** cálido si el aire está más caliente que el agua superficial; frío si está más frío (umbral inicial 2 °C, 🟣). Las etiquetas por dirección (solano E, cálido y seco en verano; ábrego SO, templado y húmedo; norte) son solo orientativas. Un viento muy seco también enfría por evaporación.
- **Según la estación (🟡 experiencia + física, no demostrada en carpa):**
  - Agua por debajo del óptimo (primavera, otoño, invierno templado) con viento cálido sostenido (≥10 km/h, 12–24 h) → la orilla que lo recibe. En España: «a ser posible nos situaremos con viento cálido de cara o frío de espalda» (CARPdiem 🟡).
  - Viento frío o entrada de frente frío → orilla resguardada y soleada (🟡).
  - Agua ya muy caliente (verano extremeño) → la orilla que recibe el viento cálido **no suma** (peso 0, 🟣 pendiente de validar). Valora en su lugar el oleaje (oxígeno y alimento removido) y la sombra.
  - Viento fuerte con oleaje → la orilla batida de fondo blando puede ser buena para los peces pero mala para ti (lance, comba, seguridad).
- **Prioridad práctica:** si el viento de cara impide lanzar, mantener el montaje o ver las picadas, la orilla resguardada gana aunque el viento «favorezca» la batida.
- **Vocabulario:** usar siempre «orilla que recibe el viento» y «orilla resguardada». «Sotavento» también se usa para «al abrigo» y lleva a confusión.
- **Qué te hace a ti (no cambia dónde está el pez):** con viento de cara lanzas más corto y con menos precisión, el hilo hace más comba, las alarmas dan más avisos falsos y te mojas. Con viento de espaldas lanzas más lejos y cebas mejor. Con viento lateral se forma un seno en el hilo que arrastra el montaje. Avisos concretos en §5.9.12.

### 5.5 Cebado y cebos (con normativa)
- Ley 11/2010 (art. 40) autoriza cebado previo con productos no tóxicos.
- Orden de 2022: **prohibido cebar en embalses de abastecimiento** (probablemente Valuengo; confirmar con el Servicio de Pesca). A Brovales (uso de riego), en principio no.
- Prohibidos como cebo en aguas de río: almejas, mejillones, cangrejos y peces continentales. El único pez vivo permitido en aguas embalsadas es la tenca de acuicultura acreditada. Prohibido el clonk.
- **Cangrejo rojo: invasor; prohibido poseerlo o transportarlo vivo.** Captura con reteles solo para autoconsumo.
- Cantidad de cebo: ajustar a temperatura (§5.1). La regla "menos cebo en frío" es correcta en el fondo (demanda baja), aunque la explicación de "no digiere" no lo sea.
- *(Añadido oct. 2026, [investigación de alternativas, oct. 2026](investigacion/2026-10-alternativas-sesion.md).)* La prohibición de la Orden de 2022 (art. 8.3) dice «en ninguna forma o estado» e incluye el **cebado**, no solo el cebo de anzuelo. La fórmula «cuando sean de río» deja dudas sobre el pescado o el mejillón de mar (la Orden de 2016 permitía peces de mar muertos): **confirmar**; mientras tanto, la app los trata como no recomendados.
- 🟢 El maíz en grano capturó 2,1–3,4 veces más que la masa y el pescado (Ateşşahin y Dürrani 2023): es el «comodín» de sustitución.
- 🟢 → 🟣 Proporción de cebado: la ración diaria es ~10 veces mayor a 26,5 °C que a 14 °C (Garcia y Adelman 1985). Regla de partida: cebado inicial en invierno ≈ 1/5–1/10 del de verano y recebado solo tras captura o señal. Sigue sin haber cifras en kg: dependen de la densidad de carpas de Brovales (🔴), que calibra el usuario.
- 🟢 Pre-cebado de 2–3 días como opción de estrategia (Kasumyan et al. 2024; Bajer et al. 2010). ⚖ Requiere confirmar que el cebado es legal en Brovales.
- 🟣 Grandes cantidades de partículas fermentadas en verano: no están prohibidas de forma expresa, pero el oxígeno de Brovales es desconocido y la Ley prohíbe sustancias «desoxigenadoras». Limitar cantidades.
- 🟡 Chufa y legumbres: siempre preparadas (remojo ≥24 h + hervor ≥30 min); crudas se hinchan y son un riesgo para el pez. Cacahuete: no recomendado.
- Funciones de un cebo y tabla de sustitutos: §5.9.5.

### 5.6 Montajes
- Pop-up, Zig Rig y bolsas de PVA son técnicas de pescadores con lógica pero **sin estudios comparativos en carpa** (🟡).
- Wallerius 2020 respalda tres ideas generales: rotar presentaciones en aguas presionadas, usar bajos menos visibles, no repetir el mismo montaje en el mismo puesto con peces ya capturados.
- *(Añadido oct. 2026.)* Cambios de columna (🟡/🟣):

| De | A | Cuándo |
|---|---|---|
| Fondo | Pop-up de 5–15 cm | Fondo sucio, cangrejo o algas; o señales sin picadas |
| Pop-up | Zig a media agua (½–¾ de la profundidad) | Verano o calor, día soleado y carpas vistas en superficie o a media agua (en línea con Benito 2015) |
| Zig | Superficie (pan o flotante) | Carpas comiendo en superficie; viento flojo (⚖ el pan es cebo vegetal permitido) |

### 5.7 Presión de pesca
- Las capturas repetidas en un puesto reducen la capturabilidad durante semanas o meses. La app registra capturas por zona y sugiere rotar.

### 5.8 Playbook por estación y por condiciones (SI → ENTONCES)

> Punto de partida operativo. **Casi todo es experiencia de pescadores o hipótesis local**: la app lo muestra con su etiqueta (Respaldado 🟢 · Experiencia/moderada 🟡 · Hipótesis local 🟣 · Sin evidencia, peso 0 🔴) y lo sustituye por los datos del propio usuario cuando haya 20–40 sesiones. Invierno y verano se deciden por la **temperatura del agua** (<10 °C y >22 °C). Entre 10 y 22 °C manda la época del año: primavera de enero a junio y otoño de julio a diciembre. *(Corregido oct. 2026: una racha templada de unos días en octubre no es primavera; la tendencia de 3 días es el modificador «temperatura subiendo/bajando» de la tabla de condiciones, no la estación.)* Todo respeta el filtro legal (§7): solo horas legales, máx. 3 cañas.

#### Por estación (disparador: temperatura del agua y época del año)

| | **Invierno** (agua <10 °C) | **Primavera** (10→20 °C, subiendo) | **Verano** (>22 °C) | **Otoño** (20→10 °C, bajando) |
|---|---|---|---|---|
| **Contexto Brovales** | Nivel semanal; puede recuperarse con lluvias tras el riego | Nivel alto (≈86 % a finales de junio): litoral disponible antes del vaciado | Vaciado por riego (≈ −30 puntos de junio a septiembre); arroyos con estiaje | Primeras lluvias pueden frenar el vaciado y subir nivel; vigilar escorrentía |
| **Dónde** 🟡 | Zonas estables algo más profundas junto a caída/antiguo cauce, o someras protegidas del viento frío y con sol a mediodía | Someras con vegetación/pasto inundado y reculas que se calientan antes; boca de recula como paso (🟣) | 🟣 Seguir el nivel: primera caída o cambio de pendiente donde antes había litoral; sombra, vegetación, entradas si llevan agua | 🟣 Litoral recién inundado, reculas y entradas con escorrentía real; fondos duros si hay cangrejo (sin datos en Brovales) |
| **Cuándo** | 🟣 Franja central del día legal, tras 2–3 días estables y horas de sol | 🟡 Tarde y anochecer legal; amanecer si la temperatura asciende | 🟡 Amanecer y anochecer legales; evitar mediodía con calor y calma | 🟡 Horas centrales templadas y atardecer legal; tras frente frío esperar 1–2 días |
| **Cebado** 🟢 | Mínimo: puñado o PVA, cebos pequeños y muy atractivos (6 %/h a 9 °C) | Progresivo al subir la temperatura; 17–21 °C pueden comer menos por el desove: no sobrecebar | Demanda máxima (~4 % del peso/día): abundante pero repartido, repuesto en ventanas de actividad | Bajar a medida que baja la temperatura; si cae de golpe (21→17 °C) reducir más |
| **Presentación** | 🟡 1–2 cañas fijas en la misma zona, paciencia | 🟣 Cañas repartidas entre orilla somera y boca de recula; mover la que no dé señales tras varias horas | 🟡 Con carpas en superficie/media agua: flotantes o zig rig; si no, fondo junto a la caída; anotar profundidad | 🟣 Una caña en litoral nuevo y otra en la caída próxima; registrar cuál recibe picadas |
| **Evita** | Cebaderos grandes, cambiar de puesto cada hora, entradas de agua fría tras lluvia fuerte | Sobrecebar tras bajada brusca de temperatura; zonas que se enfrían con viento frío | 🟢 Descartar el fondo solo por "poco oxígeno" (la carpa lo tolera); cebar zonas someras que se secan | Dar por hecho que el otoño siempre es buena época (experiencia, no ciencia); ignorar la tendencia térmica |

#### Por condición (disparadores del motor de reglas)

| SI (parámetros) | ENTONCES | Evidencia |
|---|---|---|
| **Lluvia** ligera, suelo seco, sin entrada visible | No cambiar el plan (20 mm sobre suelo seco no alteran el agua); registrar lluvia y humedad del suelo | 🟡 |
| **Lluvia** fuerte con escorrentía real y cola turbia | Probar cola y borde de la pluma turbia (no el centro del barro); cebado pequeño: el agua ya trae comida | 🟣 |
| Verano + tormenta con entrada más fresca | Entrada fría y oxigenada se hunde por el cauce: una caña en la caída frente a la desembocadura | 🟣 (física 🟢, efecto en carpa sin demostrar) |
| Invierno + lluvia fría fuerte | Evitar entrada y cola (agua helada); zonas estables y protegidas, cebado mínimo | 🟡 |
| Primavera/otoño + lluvia templada + nivel subiendo | Litoral recién inundado: cañas someras junto a nueva orilla y entrada, cebado pequeño | 🟡 |
| **Viento** cálido (aire > agua) sostenido ≥12–24 h, ≥10 km/h, agua por debajo del óptimo | Probar primero la orilla que lo recibe, sobre todo zonas someras que se calientan antes; mantener una caña de control en la zona habitual. En Brovales el fetch es corto: desempate *(corregido oct. 2026: antes 24–48 h y sin comparar con el agua)* | 🟢 física + 🟡 experiencia · peso bajo |
| Frente frío o viento frío súbito (aire < agua) | Bajar cebado, orilla resguardada y soleada, cebos de alta atracción y poco volumen, esperar (la demanda cae con el agua más fría) | 🟡 · peso bajo |
| Verano con agua muy caliente + viento cálido y seco (solano) | No priorizar la orilla batida por temperatura; valorar sombra, profundidad, entradas de agua y orilla con oleaje (oxígeno) | 🟣 · peso 0 hasta tener datos |
| Viento lateral fuerte | No cambia la ubicación del pez; ajustar equipo: hilo hundido, bobbins pesados, puntas bajas, cebado compensado | 🟡 · práctico |
| Cambio de dirección del viento («viento nuevo») | Registrar; no mover el puesto solo por eso | 🔴 · peso 0 |
| Trueno audible o previsión de tormenta eléctrica | Recoger, tumbar las cañas, salir de la orilla; volver 30 min después del último trueno | 🟢 · filtro de seguridad (no puntúa) |
| Verano + calma + sol + calor | Amanecer y anochecer, sombra y vegetación, cebos a media agua si hay actividad; no insistir a mediodía | 🟡 |
| Nublado o presión bajando | No usar la presión como regla; registrar nubosidad y presión con **peso 0** | 🔴 |
| Viento fuerte (≥15 km/h) con oleaje sobre orilla somera de fondo blando | Probar la orilla batida si se puede pescar con seguridad (agua turbia = alimento removido); si no, recula protegida | 🟢 física / 🟡 carpa · peso bajo *(antes 🟣)* |
| **Temperatura** subiendo 3 días (15→19 °C) | Subir cebado progresivamente, buscar someras que se calientan; 17–21 °C vigilar desove | 🟢 |
| Temperatura bajando 3 días (21→17 °C) | Reducir cebado, volver a zonas estables, dejar que el agua se estabilice | 🟡 |
| Agua >28 °C y calma | Bajar confianza de cualquier predicción; sesión corta en el amanecer; medir temperatura y registrar profundidad | 🟡 |
| **Nivel** bajando rápido (≈ −1 hm³/semana) | Mover cañas a la primera caída o cambio de pendiente; no cebar zona somera que se queda sin agua | 🟣 |
| Nivel subiendo e inundando pasto | Cañas someras junto a la nueva orilla, cebado pequeño (litoral nuevo con comida terrestre) | 🟡 |
| **Campo**: burbujas, saltos, nubes de sedimento, carpas en movimiento | Cañas hacia esa zona: mejor señal del día, por encima de cualquier predicción; anotarla | 🟡 |
| Mucha presión (fin de semana, otros pescadores, ruido, barcas) | Evitar el puesto evidente; zonas menos obvias, cebado discreto, presentaciones distintas | 🟢 |
| Ya capturaste en el puesto hace pocos días | Rotar puesto y montaje (evitación del anzuelo: semanas o meses, ≤7 meses) | 🟢 |
| Horas sin señales ni picadas | Cambiar de zona por función (calentamiento, paso, entrada); registrar el bolo con sus horas-caña | 🟡 |
| Historial propio en esas condiciones | Repetir lo que funcionó; con 20–40 sesiones los datos propios pasan por delante de este playbook | 🟣 |

**Notas de implementación:**
- Las condiciones se evalúan en este orden: legalidad → temperatura/tendencia → nivel → entradas/lluvia con escorrentía → viento → señales de campo → presión de pesca. Varias pueden activarse a la vez; mostrar al usuario **todas las reglas activas con su etiqueta** y la que domina (la de nivel inferior en la cadena de §4).
- "Lluvia con escorrentía real" exige lluvia acumulada, humedad del suelo y tiempo desde la última lluvia (no basta un valor de precipitación).
- Las cantidades de cebo se expresan como **demanda esperada (muy baja → muy alta)**, nunca como gramos fijos: no hay datos para fijar gramos por temperatura.
- Zig rig, flotantes, PVA y similares son técnicas de pescadores sin estudios comparativos en carpa (🟡).

```yaml
# Ejemplo de regla del playbook (formato sugerido)
- id: pb_nivel_bajando_rapido
  cuando:
    nivel_delta_7d_hm3: "<= -0.8"      # umbral inicial a calibrar
    estacion_por_temp: "verano"
  entonces:
    recomendar: "Mover cañas a primera caída / cambio de pendiente cercano al litoral anterior"
    evitar: "Cebar zona somera que se queda sin agua"
  evidencia: morado        # hipótesis local
  peso_inicial: bajo
- id: pb_lluvia_escorrentia_invierno
  cuando:
    escorrentia_real: true
    temp_agua_c: "< 10"
  entonces:
    recomendar: "Zonas estables y protegidas, cebado mínimo"
    evitar: "Cola y entrada de agua fría"
  evidencia: amarillo
  peso_inicial: bajo
```

---

### 5.9 Alternativas durante la sesión (sesión guiada)

> Añadido en oct. 2026. Fuente completa y tablas originales: [investigación de alternativas, oct. 2026](investigacion/2026-10-alternativas-sesion.md). **Ningún estudio dice cada cuántos minutos hay que cambiar de puesto, cebo o montaje (🔴, #48).** Lo respaldado: un cebadero nuevo tarda días en atraer carpas (#37), encontrar el cebo no garantiza picadas (#39), aprenden a evitar el anzuelo (#40) y en frío comen mucho menos (§5.1). Por eso: **sin señales → cambiar de capa o distancia y luego de zona; con señales y sin picadas → cambiar la presentación; en invierno, paciencia.** Todos los tiempos son 🟣 a calibrar con las sesiones del usuario.

#### 5.9.1 Diagnóstico en cada aviso (cada 30 min)

| Situación | Diagnóstico probable | Acción prioritaria | Etiqueta |
|---|---|---|---|
| A. Sin señales y sin actividad en el anzuelo | No hay peces en la zona o en esa capa | Cambiar de columna o distancia y, si sigue igual, de zona por función | 🟣 |
| B. Con señales y sin actividad | Hay peces que no comen en el punto o desconfían | Ajustar la presentación al punto exacto de la actividad, cebo de anzuelo más pequeño o de alta atracción (maíz o pop-up); **no cambiar de zona** | 🟢 (#39) + 🟣 |
| C. Toques o picadas falladas | Peces que inspeccionan el cebo | Cambiar montaje o presentación (pelo más corto, otro anzuelo, cebo más pequeño o flotante, otro sabor o textura); mantener el cebado | 🟡 (+🟢 #46) |
| D. Captura | La zona funciona | No cambiar nada; recebar poco y repetir. Avisar de que la capturabilidad puede caer tras capturas repetidas | 🟢 (#39, #40) |

Modificadores (🟡 salvo indicación): cebo **desaparecido o roto** con anzuelo limpio → probable cangrejo, tortuga o peces pequeños: cebo duro (boilie endurecido, chufa, imitación) o pop-up separado del fondo · **mordisqueado** → peces pequeños: cebo de anzuelo más grande o partículas selectivas · **viento que gira o sube** → valorar la orilla que recibe el viento (🟡, sin telemetría que lo confirme) · **lluvia con escorrentía** → probar la boca de los arroyos Brovales y Rubiales (🟡/🟣) · **nivel bajando** → primera caída (🟣, Taylor et al. 2012).

#### 5.9.2 Escalera de alternativas

Regla general (🟣): en cada peldaño se cambia **una sola variable por caña**, en este orden: 1) presentación o cebo de anzuelo · 2) columna (fondo → pop-up → zig) · 3) distancia sobre la misma estructura · 4) zona por función · 5) cantidad de recebado. Excepción: sin señales en verano se empieza por columna o distancia (el problema más probable es la ubicación).

Tiempos de partida (todos 🟣, a calibrar):

| Fase | Peldaño 1 sin señales | Peldaño 2 sin señales | Señales sin picadas → presentación | Cambios de zona como máximo | Cuándo NO cambiar |
|---|---|---|---|---|---|
| Invierno (<10 °C) | 150 min → distancia o columna (más hondo) | 240 min → zona (refugio profundo o zona soleada) | 120 min | 1 por sesión | Si hubo una sola señal o picada en las últimas 2 h; en las horas centrales soleadas |
| Primavera (10→20 °C, subiendo) | 90 min → más somero o zona de calentamiento | 150 min → zona (colas, ensenadas al sur) | 60–90 min | 2 | Con desove visible (no molestar; no comen) 🟡 |
| Verano (>22 °C) | 60 min → columna (pop-up alto o zig) o distancia | 90–120 min → zona (sombra, viento o primera caída) | 60 min | 3 | En la primera hora legal y al anochecer, si el puesto ya tuvo actividad |
| Calor extremo (>28 °C) | 60 min → zona más fresca u oxigenada (viento, entrada de agua, profundidad media) | 90 min | 60 min | 2 | En las horas centrales: no gastar cebo, esperar al anochecer legal |
| Otoño (20→10 °C, bajando) | 90 min → distancia o caída | 150 min → zona de paso o refugio | 90 min | 2 | Si hay señales en el puesto |

Por qué el invierno requiere paciencia (🟢 → 🟣): tránsito digestivo ~5 veces más lento y agregación en pocos puntos (#42). Cambiar de zona cada hora saca al pescador del único punto con peces antes de que coman.

Modificadores por condición (🟣 salvo indicación): lluvia o escorrentía tras periodo seco → peldaño extra de «boca de recula o arroyo» antes del cambio de zona · viento sostenido → sin señales tras 60 min, orilla que recibe el viento (🟡) · nivel bajando → distancia a la primera caída; descartar someras sin datos recientes · nivel subiendo → zonas recién inundadas (🟡) · alta presión de pesca → peldaño de presentación 30 min antes, cebos de anzuelo poco habituales, recebado mínimo (🟢 #40 + 🟣) · **fin del horario legal en menos de 45 min → no proponer cambios de zona, solo de presentación** (⚖ + 🟣).

#### 5.9.3 Decisión del usuario (prevalece sobre los tiempos)

- El usuario puede **aceptar o rechazar** cada propuesta con un motivo («no tengo ese cebo», «no me convence», «ya lo probé», «las condiciones son otras»). Si falta el cebo, la app ofrece el sustituto de §5.9.5.
- **Si el usuario marca «no funciona» dos veces en 30 minutos, la app propone el siguiente peldaño en ese momento**, sin esperar al tiempo de la tabla (decisión del usuario, oct. 2026). En invierno se muestra el aviso 🟣 de paciencia, pero no se bloquea. El cambio se registra como **forzado por el usuario** para poder compararlo después con los cambios por tiempo.
- Las propuestas solo aparecen cuando una regla se activa o el usuario lo pide, no en cada aviso.

#### 5.9.4 Alternativas por estructura (🟣)

| Primera opción que falla | Alternativa 1 | Alternativa 2 | Alternativa 3 |
|---|---|---|---|
| Borde somero (≤2 m) | Primera caída (talud) | Pie del talud | Zona contigua con viento o sombra |
| Primera caída | Antiguo cauce (si está localizado con sonda o mapa) | Borde somero por la mañana o al anochecer | Boca de recula |
| Antiguo cauce | Primera caída del lado soleado (invierno) | Media agua sobre el cauce (zig) en verano | Llano intermedio |
| Boca de recula o arroyo | Interior de la recula (si sube el nivel) | Punta o cabo exterior (zona de paso) | Primera caída cercana |

**Aviso obligatorio:** la batimetría de Brovales no está verificada (§6.3). «Primera caída» y «antiguo cauce» son categorías funcionales que el usuario confirma con sonda o plomo marcador (⚖ la sonda no está prohibida en aguas no trucheras).

#### 5.9.5 Sustitución de cebos

Funciones de un cebo: **atracción** (olfativa, a distancia) · **retención** (mantener a los peces comiendo: partículas pequeñas y numerosas) · **selectividad** (filtrar peces pequeños, cangrejo y fauna no deseada) · **visibilidad** (contraste o flotabilidad). 🟢 El olor atrae y el gusto decide (#46): un sustituto debe cumplir **la misma función**, no tener el mismo aspecto.

| Cebo propuesto | Función principal | Sustituto 1 | Sustituto 2 | Casero o barato | Temporada | Evidencia | ⚖ |
|---|---|---|---|---|---|---|---|
| Boilie 15–20 mm (anzuelo) | Selectividad media + atracción | Chufa preparada (2–3) | Maíz duro cocido (3–4) o maíz dulce + maíz artificial | Masilla dura casera | Todo el año; más pequeño en frío | 🟡 | Permitido |
| Boilie ≥24 mm o endurecido | Selectividad alta, anti-cangrejo | Chufa grande ×3 | Imitación (plástico o corcho) | Boilie secado al aire | Verano y otoño | 🟡 | Permitido |
| Pellet (cebado) | Atracción rápida | Harinas de engodo (girasol, maíz) | Boilie triturado | Pienso no medicado 🟣; harina de maíz + pan rallado | Primavera–otoño; poco en invierno | 🟢 (#37) | Permitido si no es tóxico |
| Maíz dulce o en grano | Atracción + retención | Maíz duro cocido | Garbanzo 🟡 / trigo cocido | Maíz partido cocido | Todo el año | 🟢 (#45) | Permitido |
| Cañamón | Retención | Trigo o cebada cocidos | Mijo o alpiste 🟡 | Trigo cocido | Primavera–otoño | 🟡 | Permitido |
| Chufa | Selectividad + resistencia | Boilie endurecido | (cacahuete: no recomendado) | — | Verano y otoño | 🟡 | Permitido; **siempre preparada** |
| Pasta o masilla | Atracción rápida, anzuelo blando | Pan (miga) | Boilie roto | Harina + huevo + maíz molido | Frío y primavera | 🟢 (#45: rinde menos que el maíz) | Permitido |
| Pan | Visibilidad, superficie | Flotante (corcho + maíz) | Pop-up | Corteza de pan | Verano | 🟡 | Permitido |
| Bolsa de PVA | Concentrar cebo junto al anzuelo | Method (engodo en el plomo) | Stick de engodo | Bola de engodo apretada | Todo el año; ideal en frío | 🟡 | Permitido |
| Pop-up | Visibilidad + anti-cangrejo | Maíz artificial | Corcho + cebo natural | Boilie con inserto de corcho | Todo el año | 🟡 | Permitido |
| Lombriz | Atracción animal | Asticot | — | — | Frío y primavera | 🟡 | Permitido (no es pez, almeja, mejillón ni cangrejo) |

Selección según lo que lleva el usuario (🟣): identificar la función del cebo propuesto → filtrar su inventario por función y estación → si hay cangrejo o peces pequeños, priorizar selectividad alta → si no hay equivalente, avisar («sin equivalente: pesca con X, que cumple [función secundaria]; espera menos selectividad») → **bloquear siempre los cebos prohibidos (§7)** y avisar de la preparación de chufa y legumbres.

#### 5.9.6 Avisos obligatorios de la sesión guiada

- «Los tiempos de cambio son hipótesis 🟣: no hay estudios que los fijen.»
- «La batimetría de Brovales no está verificada.»
- «Sin datos de oxígeno, estratificación ni cangrejo rojo en Brovales.»
- «Normativa revisada hasta marzo de 2026; confirma con el Servicio de Pesca y con los carteles del embalse.»
- «La app no garantiza capturas; las carpas aprenden a evitar los montajes.»

#### 5.9.7 Avisos cada 30 minutos sin molestar

Solo vibración; notificación expandible para responder sin abrir la app · intervalo de 45–60 min en invierno o tras 3 respuestas seguidas de «nada» sin cambios · pausa de 20 min tras «Captura» · se salta el aviso si el usuario registró algo en los últimos 15 min · una sola notificación agrupada · cierre automático al acabar el horario legal (⚖).

#### 5.9.8 Registro por caña, otras especies, cebado y viento (oct. 2026, sesión del usuario)

> Añadido tras una sesión real del usuario: viento muy cambiante; mucho cebado; en la caña fija entraron peces pequeños y apenas capturó; en la de carrete hubo mucha actividad al principio, luego paró, entró el barbo y apenas volvió a capturar carpas.

- **Una pista por caña** (decisión del usuario): cada caña lleva nombre («fija», «carrete»), su plan, sus avisos y sus propuestas, porque en cada peldaño se cambia una sola variable por caña (§5.9.2). La segunda caña empieza por el segundo cebo de la fase si el usuario lo lleva, para comparar (🟣).
- **Otras especies** (Brovales, §6: boga, barbo, black bass; más «pequeño sin identificar»): se anotan con su especie y **no cuentan como captura** para el plan ni para las capturas por hora-caña. Dos capturas de otras especies o cebos mordisqueados en un tramo ⇒ cebo de anzuelo más grande o selectivo (chufa, boilie duro o grande) (🟡, §5.9.1 «mordisqueado»; el maíz es poco selectivo frente a barbos y bogas, 🟡). El umbral de dos es 🟣.
- **Cebado sin gramos** (poco · normal · mucho), al empezar y en cada recebado por caña. Con cebado abundante y entrada de otras especies o pequeños ⇒ «ceba menos y más selectivo» (🟡 + 🟣). En invierno, con cebado abundante ⇒ aviso de cebado mínimo (🟢, §5.5).
- **Viento**: el usuario anota «ha cambiado el viento»; durante 2 h la app recuerda valorar la orilla que lo recibe (🟡, §5.9.1; las 2 h son 🟣). *(Oct. 2026, §5.9.12:)* el texto pide además registrar si cambian las señales (saltos, burbujas, picadas): no está demostrado que el cambio mueva a las carpas.
- Black bass: **no hay normativa verificada** en este documento sobre su captura o devolución en Brovales; la app no dice nada legal sobre él hasta verificarlo (§6.3).

#### 5.9.9 Lluvia, tormenta y entrada de agua durante la sesión (oct. 2026, sesión del usuario)

> Añadido tras una sesión con lluvia y tormenta que no se pudieron anotar. El usuario marca lo que empieza y lo desmarca al parar; pueden coincidir (lluvia fuerte + tormenta + agua turbia entrando) y quedan con su hora para comparar después qué cambió en cada caña.

- Condiciones: **lluvia ligera**, **lluvia fuerte** (se excluyen entre sí), **tormenta**, **entra agua turbia** (escorrentía observada por el usuario, que es la «escorrentía real» que exige §5.6).
- Avisos (aplicando la tabla «Por condición» de §5.6): lluvia ligera sin entrada → no cambiar el plan (🟡) · lluvia fuerte → vigilar si entra agua turbia y marcarlo (🟡) · agua turbia entrando → cola y borde de la pluma, no el centro, cebado pequeño (🟣) · verano o calor + tormenta + entrada → una caña en la caída frente a la desembocadura (🟣) · invierno + lluvia fuerte o entrada → evitar entrada y cola, cebado mínimo (🟡).
- Escalera: con agua turbia entrando (no en invierno), el peldaño «boca de recula o arroyo» va antes del cambio de zona (§5.9.2, 🟣) y cuenta como un cambio de zona.
- **Seguridad (🟢)**: con tormenta, las cañas de carbono conducen la electricidad; dejar las cañas, alejarse del agua y ponerse a cubierto. Mientras dure la tormenta la app no propone cambios. El efecto de la tormenta en la carpa no está demostrado (🔴): solo se registra.

#### 5.9.10 Puestos del usuario y el tiempo en cada aviso (oct. 2026, petición del usuario)

> Añadido para que cada aviso diga qué ha cambiado y para usar puestos reales en lugar de suponer la batimetría (§6.3).

- **Mis puestos**: nombre, zona, estructura (borde somero, primera caída, antiguo cauce, recula o entrada, punta, llano, otra), profundidad y distancia **medidas por el usuario**, orientación de la orilla (hacia dónde mira, rumbo al agua) y notas. La app no inventa profundidades ni zonas.
- **Viento respecto al puesto**: si el viento viene de la dirección a la que mira la orilla, sopla desde el agua hacia ti: *de cara*, tu orilla lo recibe (§5.4 🟡: desempate, no regla principal; definiciones y avisos prácticos en §5.9.12); de espaldas, orilla resguardada; lateral; flojo por debajo de 6 km/h (🟣).
- **Tiempo en cada aviso**: al empezar y en cada aviso se guarda la previsión de la hora en curso (serie combinada según tu prioridad de fuentes; aire corregido con el sesgo frente a AEMET), el código de tiempo de Open-Meteo (tormenta = 95–99; se usa el más severo de los modelos), el agua (medida o estimada), el ocaso y el fin legal. Se muestran los cambios desde el aviso anterior y desde el inicio con umbrales 🟣: aire ±1,5 °C, viento ±8 km/h, giro ≥ 60° (con viento ≥ 6 km/h), rachas ≥ 40 km/h, lluvia ≥ 0,2 mm/h, agua ±0,3 °C. **Nubosidad (±40 %) y presión (±2 hPa) se muestran con peso 0 (🔴)**.
- **Lo que ves manda**: es una previsión; si no cuadra con lo anotado (o da tormenta), la app pregunta «¿Llueve?» / «¿Hay tormenta?» y la respuesta activa o termina la condición (§5.9.9). Un giro o subida clara del viento previsto cuenta como cambio de viento (§5.9.8, 🟡).
- Todo queda con la sesión para aprender después (§9). Ningún dato de tiempo cambia la valoración de las reglas durante la sesión.

#### 5.9.11 Asistente de IA (oct. 2026, petición del usuario)

> Opcional. El usuario pone su propia clave (Gemini de Google AI Studio u otro proveedor compatible con OpenAI). La IA **no sustituye** a estas normas: la app las comprueba en cada respuesta y, si no se cumplen, la descarta y siguen mandando las reglas.

- **En cada aviso** la IA recibe el estado de la sesión (fase, tiempo previsto y sus cambios, condiciones anotadas, puesto y Mis puestos, equipo, configuración de cada caña, últimos avisos con dónde se vio actividad y saltos) y responde MANTENER o CAMBIAR (un cambio por caña, tipos de la escalera §5.9.2). Un CAMBIAR válido queda como propuesta pendiente de esa caña, marcada «IA», que el usuario acepta o rechaza como las demás.
- **En Estrategia** tiene su apartado: un plan por caña y **consejos por apartado** (dónde, cuándo, cebado, presentación, qué evitar, notas; como mucho 2 por apartado), cada uno con su etiqueta de evidencia, partiendo de la valoración, las ventanas legales y los consejos de las reglas. Si contradice a las reglas, lo debe decir; decide el usuario. El último plan válido del día se vuelve a mostrar sin repetir la consulta.
- **Validación (todo o nada)**: solo pasos permitidos; solo cebos y montajes del equipo y puestos de Mis puestos (por nombre); sin probabilidades ni porcentajes; sin gramos ni kilos; sin hablar de pescar de noche; sin prometer peces; etiqueta de evidencia en la decisión y en cada cambio; nada de cambios de zona a menos de 45 min del fin legal (⚖) ni por encima del límite de la fase; ningún cambio con tormenta ni fuera del horario legal; ninguna hora citada («07:30», «7h30») fuera del horario legal de hoy (⚖); consejos con apartado conocido; distancias ≤ 250 m.
- **Nunca se envía** la ubicación GPS, la cuenta ni la clave. La clave se guarda solo en el móvil (no se sincroniza ni se exporta).
- Cada consulta queda en la sesión (respuesta, validez, motivos de descarte o error) y cada propuesta lleva su origen (reglas o IA) con la decisión del usuario: base de la comparación «¿IA o reglas?» de la fase 8. Lo que diga la IA es 🟣 hasta que tus datos lo respalden.

#### 5.9.12 Viento respecto al puesto y avisos prácticos (oct. 2026, informe de viento)

> Añadido con el [informe de viento](investigacion/2026-10-viento.md). Completa §5.9.10 (que sigue igual): aquí van las definiciones sin ambigüedad, cómo puntúa el viento y los avisos que ayudan a pescar bien.

- **Definiciones:** *de cara* = el viento sopla desde el agua hacia tu orilla (tu orilla lo recibe); *de espaldas* = sopla desde tierra hacia el agua (tu orilla queda resguardada y en calma); *lateral* = el resto; *flojo* < 6 km/h (sin efecto, 🟣). En la interfaz, «orilla que recibe el viento» y «orilla resguardada», nunca «sotavento».
- **Cálculo:** ángulo entre la dirección de procedencia del viento y hacia dónde mira el puesto (al agua). ≤ 45° → de cara; ≥ 135° → de espaldas; resto → lateral.
- **Puntuación sobre el pez (solo desempate, §5.4):** según estación, aire − agua y duración. De cara solo suma con viento cálido sostenido y agua por debajo del óptimo (22 °C, §5.1). Con viento frío, «resguardado» = favorable (🟡). Con el agua en el óptimo o por encima (verano), la dirección = 0 (🟣).
- **Avisos prácticos (no puntúan al pez; sí aparecen en la app):**
  - De cara ≥15 km/h: «Lance más corto: plomo más pesado o aerodinámico, trayectoria baja. Cebo en bolas o PVA sólido; el pellet suelto se quedará corto.»
  - Lateral ≥10 km/h: «Comba: hunde el hilo, puntas bajas, bobbins más pesados; separa las líneas.»
  - Oleaje visible (de cara ≥15 km/h): «Posibles pitidos falsos: sube el peso del indicador antes de bajar la sensibilidad; mira el hilo antes de descartar una caída del bobbin.»
  - De espaldas: «Buen momento para cebar lejos; ojo, el cebo flotante deriva hacia fuera.»
  - Rachas ≥40 km/h: «Ancla el bivvy y el paraguas; cuidado en la sacada en orilla batida.»
  - Tormenta: «Si oyes un trueno, deja de pescar; caña de carbono = conductor. Vuelve 30 min después del último trueno.» (§5.9.9, 🟢 NOAA/NWS)
- **Más consejos de equipo (🟡):** hundir el hilo nada más caer el plomo con la puntera bajo el agua; nylon o fluorocarbono que se hunda (el trenzado flota y coge viento); backleads con resaca, sabiendo que restan sensibilidad; alinear las cañas con el hilo y anclar el rod-pod; con 3 cañas en 10 m y viento lateral, separar las líneas; en la orilla batida, cebar un poco «aguas arriba» del viento respecto al punto (sin fuente formal).
- **«Ha cambiado el viento» (🟣):** se mantiene el recordatorio de 2 h de §5.9.8.
- **Fetch por puesto:** cada puesto guarda los metros de agua abierta hacia donde mira (estimados en el mapa). Sin fetch (≥500 m, 🟣) no hay oleaje que remueva el fondo.
- *(Añadido oct. 2026, mapa de puestos.)* En el mapa (satélite) el usuario toca su puesto y la orilla de enfrente, hacia donde lanza: el rumbo entre los dos es la orientación exacta y la distancia es el fetch. El mapa colorea cada puesto según cómo le da el viento (le favorece, lo recibe, resguardado, lateral). **El tiempo previsto es el mismo para todo el embalse** (los modelos trabajan con cuadrículas de 1–11 km): lo que cambia de un puesto a otro es cómo le da el viento, no el aire ni la temperatura.

### 5.10 Pescar más: recomendación concreta y conocimiento aprendido (oct. 2026, petición del usuario)

> El usuario quiere que la app le diga qué hacer para sacar el máximo de carpas, sin dejar nada al azar, y que este documento mejore con sus vivencias. El horario de pesca se sigue respetando, pero sin recordarlo en cada pantalla.

- **«Qué hago ahora»** (Estrategia, arriba). Momento del día según el sol (🟣): amanecer de −1 h a +1 h 30 min de la salida del sol; anochecer de −1 h 30 min a +1 h de la puesta; horas centrales ± 2 h del mediodía solar. De noche se aconseja para el próximo amanecer.
  - **Cebos**: dos, uno por caña para comparar. Primero los que te han funcionado; si no hay datos, los de la fase (§5.9.5) con tu equipo.
  - **Presentación**: a fondo salvo que otra te haya ido mejor. En verano y con calor, zig si ves carpas arriba (§5.6).
  - **Puestos de «Mis puestos» ordenados.** Encaje de la estructura con la fase y la hora, de 0 a 3 (🟣/🟡, tabla «Dónde» de §5.8):
    - invierno: caída y cauce; someras solo a mediodía;
    - primavera: someras y reculas;
    - verano: someras al amanecer y al anochecer, caída en las horas centrales;
    - calor: lo más hondo y las entradas;
    - otoño: reculas, caída y litoral nuevo.
    - Además: +1 si el viento favorece el puesto según §5.4 (de cara con viento cálido sostenido y agua por debajo del óptimo; resguardado con viento frío; nada con el agua ya caliente); +3 si te ha funcionado; +1 si hay una lección tuya.
  - **Distancia y profundidad por fase y hora** (🟣): en verano, cerca y somero con luz baja, lejos y hondo a mediodía; en invierno, lejos y hondo salvo a mediodía. Los metros salen de tus puestos (la batimetría no está verificada, §6.3).
  - **Cebado según la demanda** (🟢, §5.8).
- **Lo que te ha funcionado** (🟣, datos propios): capturas por hora-caña por cebo, puesto, columna y momento del día, en sesiones parecidas (misma fase o agua ± 3 °C).
  - «Te ha funcionado» = al menos 2 carpas en 2 sesiones y un rendimiento ≥ 1,25 veces tu media en esas condiciones.
  - Tus datos pasan por delante de las tablas generales.
- **Conocimiento aprendido.** Al terminar cada sesión guiada, la IA la repasa con este documento y propone como mucho 3 lecciones, con condiciones (fase, agua, momento, puesto), evidencia y el apartado que confirman o corrigen. El usuario las acepta o descarta, y también puede escribir las suyas.
  - Las aceptadas se usan en «Qué hago ahora» y en la IA, y se exportan en Markdown para incorporarlas aquí en una versión nueva.
  - **Este documento sigue siendo la base**: lo aprendido lo completa o lo corrige, siempre a la vista del usuario.
- **IA orientada a capturas**: recibe este documento (§5, y §6 solo en Brovales), lo que te ha funcionado, tus lecciones y la recomendación de la app. No habla de normativa: el horario lo controla la app y se rechaza cualquier hora fuera de él.

- *(Añadido oct. 2026.)* **Chat en el puesto**: durante la sesión guiada el usuario habla con la IA (texto o dictado). La IA actúa como experta en carpas y ya conoce el embalse y sus especies, el puesto, la configuración de cada caña, la estrategia del día, el tiempo, tus datos y tus lecciones: no hay que explicárselo. Si propone cambios, valen las mismas normas que en un aviso (uno por caña, tu equipo, ⚖ sin cambio de zona a menos de 45 min del final) y quedan como propuestas que el usuario acepta o rechaza. La conversación se guarda con la sesión y entra en el repaso.

### 5.11 Varios embalses y especies acompañantes (oct. 2026, petición del usuario)

> El usuario pesca también fuera de Brovales (p. ej. el embalse de Burguillos, coto «Charco El Toro», en Burguillos del Cerro). Este documento se aplica a todos los embalses; lo que cambia de uno a otro son sus especies, si se puede cebar y los puestos del usuario.

- **Catálogo**: los cotos del «Listado completo de cotos de pesca de Extremadura» (Junta de Extremadura) con las coordenadas de sus enlaces al IGN, más Brovales (no es coto). Se pueden añadir embalses propios. El embalse activo es el más cercano (≤ 3 km) a la ubicación elegida.
- **Ficha del embalse**: la IA la prepara con este documento (🟣, sin verificar): especies (lista cerrada; lo que no sabe queda vacío), si cree que abastece a una población, resumen y notas. **El usuario confirma** cada especie y dice si se puede cebar; lo suyo manda.
- ⚖ **Cebado**: prohibido en embalses de abastecimiento (§7). Si el nombre oficial lo dice («Abto.», «Abastecimiento») y el usuario no dice otra cosa, la app no recomienda cebar; si solo lo sospecha la IA, avisa para confirmarlo.
- **Especies acompañantes → qué cambia en la pesca de la carpa** (sin cambiar la cadena de filtros de §4):
  - 🟡 **Barbo y boga**: se comen el cebo pequeño y vacían los cebados de partícula → cebos de anzuelo selectivos (boilie, chufa, pop-up) y menos maíz o cañamón suelto.
  - 🟡 **Cangrejo rojo**: destroza los cebos blandos → cebos duros (chufa, boilie endurecido) y revisar a menudo (§5.9.5).
  - 🟣 **Carpín y pez gato**: picadas pequeñas y cebos comidos en el fondo → cebos duros y grandes, o pop-up para separarlo del fondo.
  - 🟡 **Tenca**: entra en fondos con vegetación y cebos pequeños; si solo se busca carpa, cebos más grandes.
  - 🟣 **Siluro**: puede entrar al cebo de carpa → montaje y bajo más fuertes.
  - 🟣 **Black bass, lucio y lucioperca**: no compiten por el cebo; los alevines se refugian en la orilla y la carpa puede apartarse de esas zonas.
  - 🟣 **Pez sol y alburno**: atacan cebos pequeños cerca de la orilla y a media agua → cebos grandes, fondo o pop-up corto mejor que zig.
- **Lo que te ha funcionado** se calcula primero con las sesiones de ese embalse; con menos de 2 se usan también las de otros embalses (y se dice). Los puestos y las lecciones van con su embalse.
- **§6 es solo de Brovales**: en otro embalse sus datos (orillas, profundidades, nivel) no se aplican; se usan la ficha, los puestos y las mediciones del usuario.

## 6. Brovales

### 6.1 Verificado (oficial / inventario de presas MITECO)
- Presa de contrafuertes sobre el río Brovales, terminada en **1960**, en Jerez de los Caballeros (Badajoz), demarcación del Guadiana. Gestora: **CHG**. Uso: **riego**. Código MITECO presa 4060047, embalse 4029. Categoría de riesgo A.
- Altura 25 m; coronación 503 m a cota 305 m; cimentación 280,3 m; cauce 288 m. Aliviaderos: 646 m³/s.
- Capacidad en nivel máximo normal: **6,98 hm³**. Superficie **158,7 ha**. Cuenca **203 km²**.
- Plan Hidrológico Guadiana 2022–2027: masa ES040MSPF000206450 "Embalse de Brovales", lago muy modificado, tipología **E-T04**, 1,45 km². El Plan de 1998 ya declaraba la cuenca vertiente directa a Brovales y Valuengo (800 ha) como zona a proteger contra la erosión.
- Informe CHG de estado de embalses 2023: fila "Embalse de Brovales. MUY MODIFICADA. BUENO. ALTO" (parece potencial ecológico bueno; el significado de "ALTO" no se pudo comprobar). Punto de control **GN00000763**.
- Saneamiento: medida "EDAR (tratamiento adecuado) en Brovales", 0,315 M€, "no iniciado". Posible fuente de nutrientes, no cuantificada.
- Patrón de nivel 2026 (Boletín Hidrológico vía embalses.net, redondeado a hm³): 6 hm³ (85,7 %) el 22 jun (media 10 años 87,1 %); 4 hm³ el 14 sep tras perder 1 hm³ en una semana; 4 hm³ (57,1 %) el 28 sep (media 10 años 55,7 %). **Vaciado estival de riego de ~30 puntos entre junio y septiembre, recurrente.**
- Afluentes: río/arroyo Brovales y Rubiales (alimentado por los arroyos del Castaño y de Molinos).
- *(Añadido oct. 2026.)* infoembalse: 6,75 hm³ en junio de 2025 frente a una media decenal de 6,24 hm³ en junio. Registrado como «Riego» en SEPREM y MITECO.
- ⚖ No consta ningún régimen especial: no es coto (listado oficial de 23/03/2026), no figura en el Anexo II de 2022 ni en la Resolución de 14/01/2025 (falta leer el anexo completo de la Resolución de 18/03/2026). Se aplican las reglas generales de §7. **Cebado probablemente permitido**: confirmar por escrito que no se considera embalse de abastecimiento.

### 6.2 Plausible / estimado (mostrar marcado como "estimado")
- Profundidad máxima a embalse lleno ~15–17 m (por cotas). Profundidad media ~4,4–4,8 m.
- Estratificación estival: posible junto a la presa; improbable estable en colas.
- Especies según fuentes divulgativas: boga, barbo, carpa, black-bass (sin inventario oficial accesible).
- "Raro es que baje del 50 %" a finales de verano: concuerda con media decenal (~56 % a finales de septiembre).

### 6.3 No verificable / probablemente inventado (NO mostrar)
- "Demasía Hidrográfica del Guadiana" (no existe).
- Que la cola sea "inhabitable" en verano (sin datos de O₂).
- "Lo que saben los pescadores locales" sobre la cola (sin fuente).
- Estado trófico (eutrófico/mesotrófico), clorofila, Secchi, perfiles de O₂/temperatura: existen tablas en informes CHG (2018–2019 y 2023) pero no fueron accesibles. **Tarea pendiente.**
- Presencia de cangrejo rojo o Corbicula en Brovales (sin registro oficial).
  - Contexto regional (oct. 2026): el cangrejo rojo apareció en el 69,77 % de 407 cuadrículas UTM 10×10 km de Extremadura y no por encima de 750 m (Pérez-Bote, Pula y Cascos 2000, Graellsia 56:71–78). Probable en la zona, sin confirmar en Brovales: se deducirá del estado del cebo registrado en las sesiones.
- Talla de las carpas de Brovales («600–800 g», «mejor época abril–junio y septiembre–diciembre»): un blog sin contrastar (🟡 débil). **No mostrar.**

### 6.4 Valuengo (embalse vecino, mismo uso de riego)
- Abastece de agua potable a Fregenal de la Sierra y otras poblaciones; la Orden de Vedas lo trata como **coto** sin límite de puestos, permiso diario código **12081-1**, con su Plan Técnico de Gestión. Posible prohibición de cebar (confirmar).
- *(Añadido oct. 2026, ⚖ ambiguo.)* Su Plan Técnico de Gestión dice «Engodos: Sí; Masillas: Sí; Cebo de origen animal: Sí», y el art. 10.1 de la Orden permite que el plan de un coto difiera de ella. El Ayuntamiento indica que abastece a Jerez, Fregenal e Higuera la Real, y la Orden prohíbe cebar en embalses de abastecimiento sin publicar una lista. Interpretación no oficial: prevalece el Plan. La cola (ZEPA) es incompatible con la pesca todo el año.

---

## 7. Normativa extremeña (filtros legales duros)

Fuente: Orden de 7/11/2022 (en vigor desde 1/1/2023, con tramos actualizados por resoluciones de 2024 y del 18/3/2026). **Confirmar en DOE y en pescayrios.juntaextremadura.es antes de cada temporada.**

- Hasta **3 cañas** por persona en un máximo de **10 m de orilla**, con 3 señuelos por caña como máximo.
- **10 m** de distancia mínima con otro pescador si este lo pide.
- **Horario general:** desde 1 h antes de la salida del sol hasta 1 h después de su puesta.
- **Horario libre** solo desde puesto fijo en tramos del anexo II: Orellana (margen derecha), Alange, Alcántara (Alconétar y Miraltajo) y García de Sola (Guadalupejo). **Brovales NO figura.** En esos tramos los refugios deben ser pardos o caqui, <4 m y con interior visible.
- **Carpa:** "otras especies", pescable sin límite de talla, cupo ni temporada en aguas embalsadas artificialmente antes de 2007 (Brovales, 1960, lo es). Fuera de esos casos rige el régimen de especie invasora con devolución prohibida. El "cupo de 5 carpas/día" de algunas guías comerciales **es un error**.
- Valuengo: coto, permiso diario 12081-1.
- La app debe **separar ventanas "científicas" (nocturnas) de ventanas legales** y mostrar solo las legales en Brovales.
- *(Añadido oct. 2026.)* Cebos y prácticas (Ley 11/2010 y Orden de 2022):
  - **Prohibidos como cebo y en el cebado**, «en ninguna forma o estado»: almejas, mejillones, cangrejos (incluido el rojo) y peces continentales (OGV art. 8.3; dudas con los de mar, ver §5.5). Pez vivo: solo tenca de acuicultura acreditada en embalses.
  - Prohibido el **clonk** (OGV art. 8.6) y el **cebado en embalses de abastecimiento** (OGV art. 8.3).
  - Prohibidas las sustancias venenosas, desoxigenantes, paralizantes o repelentes (falta muy grave; Ley arts. 39 d y 59) y las **fuentes de luz proyectadas al agua** (Ley art. 39 f).
  - Cangrejo rojo: prohibido transportarlo vivo o devolverlo al agua; captura para autoconsumo con un máximo de 10 reteles (OGV art. 5 y DA 3.ª).
  - **Cebado previo permitido** con productos no tóxicos (Ley art. 40.2), salvo aguas trucheras o régimen especial que lo prohíba.
  - Sonda: no prohibida en aguas no trucheras.
  - Sanciones de referencia: cebar en lugar o con sustancia no autorizados, leve (20–100 €) o menos grave (101–500 €); cebo no permitido, menos grave; sustancias tóxicas, grave (501–5.000 €).

---

## 8. Fuentes de datos para la app

| Dato | Fuente | Notas |
|---|---|---|
| Meteorología actual, previsión e histórico (3–7 días) | **Open-Meteo** (forecast + archive API, sin clave) | Coordenadas Brovales ≈ 38,35 N, −6,70 W. Aire, viento (dir/vel/rachas), presión, nubosidad, precipitación, radiación |
| Observaciones de estación | **AEMET OpenData** | Clave gratuita; estaciones cercanas |
| Sol y luna | **SunCalc** (en Android, puertos tipo commons-suncalc) | Orto, ocaso, crepúsculos, fase e iluminación lunar. **Calcular también la ventana legal** |
| Nivel del embalse | **Boletín Hidrológico semanal MITECO** | Dato semanal, redondeado (±0,5 hm³ ≈ ±7 pts %). **SAIH Guadiana no tiene API pública**: acceso por cuenta (soporte@saihguadiana.com); no consta dato en tiempo real de Brovales. Diseñar: dato semanal + **entrada manual** (cota o foto de referencia fija) |
| Temperatura del agua | **Medición propia** (termómetro/registrador a 0,5 m y, si se puede, 3–5 m) | Alternativa: modelo aire→agua (media móvil ponderada de 5–10 días de T aire), calibrado con mediciones propias. Satélite Landsat 8/9 térmico: viable como complemento ocasional (8–16 días); Sentinel-3 (1 km) demasiado grueso. No probado en Brovales |
| GPS | Sensor del móvil | Detección de embalse por geofence; guardar en local |

---

## 9. Esquema de datos de la sesión

Campos mínimos:
- Fecha; inicio y fin; minutos efectivos por caña (→ **horas-caña** como esfuerzo).
- Puesto (zona codificada, no necesariamente GPS exacto); distancia y profundidad de cada caña.
- Temperatura del agua medida (superficie y, si es posible, fondo); nivel o %; turbidez estimada (escala 1–5 o Secchi casero).
- Viento observado; montaje, cebo, cantidad de cebado (kg); otros pescadores cerca (presión).
- Picadas, capturas (peso y hora), pérdidas.
- **Bolos explícitos:** una sesión sin capturas es un dato y siempre se guarda con su esfuerzo.
- **Puntuación que dio la app antes de la sesión**, guardada automáticamente (para medir capacidad predictiva sin sesgo retrospectivo).
- Auto-rellenados: meteorología de la sesión y de los 3–7 días previos, sol/luna, ventana legal, nivel.

Sesgos que hay que evitar:
- Registrar solo las sesiones buenas.
- Cambiar de puesto y cebo según la predicción (confunde causa y efecto).
- Atribuir a luna/presión lo que fue temperatura.
- Ignorar que las capturas propias reducen las siguientes.

Sesión guiada (añadido oct. 2026, §5.9):
- **Tramos:** cada configuración probada (plan A, B, C…) es un tramo con su inicio, fin, esfuerzo en horas-caña, picadas y capturas. El aprendizaje se hace por tramo, no por sesión.
- **Propuestas y decisiones:** qué propuso la app, si se aceptó o rechazó, el motivo y si el cambio fue por tiempo o **forzado por el usuario** (dos «no funciona» en 30 min).
- **Cada cambio:** hora, caña, la única variable cambiada (cebo, montaje, columna, distancia, zona), valor anterior y nuevo.
- **Avisos cada 30 min:** señales (ninguna, indirectas, directas), actividad en el anzuelo (nada, toques, picada fallada, captura), estado del cebo (intacto, mordisqueado, desaparecido o roto, no revisado), cambios del tiempo y cambios del usuario.
- **Inventario:** cebos y montajes que lleva el usuario (para proponer solo lo disponible).
- **Franja horaria de cada tramo:** el plan B suele coincidir con el atardecer; sin registrarla, la hora se confunde con el plan.
- Protocolo de tres cañas (🟣): A = recomendación, B = alternativa de un peldaño, C = exploración; rotar la posición física. ⚖ Las tres en ≤10 m de orilla, así que C solo puede explorar otra distancia o columna; otra zona exige mover las tres.

Viento (añadido oct. 2026, [informe de viento](investigacion/2026-10-viento.md)):

| Campo | Cómo | Por qué |
|---|---|---|
| Dirección de procedencia del viento (8 rumbos o grados) | AEMET (estación más cercana) + observación en la orilla | La estación puede no reflejar el viento real en el embalse |
| Velocidad media y racha máxima (km/h) | AEMET o anemómetro de mano | Umbrales de 6/10/15/40 km/h |
| Horas con dirección similar (±45°) | Calculado por la app con el histórico | Prueba la regla de duración (#61) |
| Azimut del puesto (hacia dónde miras al agua) | Brújula del móvil, guardado por puesto | Calcular de cara / espaldas / lateral |
| Fetch en la dirección del viento (m) | Estimado sobre el mapa por puesto | Separa orillas con oleaje real de las que no lo tienen |
| T aire y T agua superficial en la orilla (°C) | Termómetro a ~30 cm en la orilla + T aire | Define viento cálido o frío (aire − agua) |
| Oleaje (0 = plano, 1 = rizado, 2 = olas pequeñas, 3 = olas con espuma) | Observación | Resuspensión y problemas de indicación |
| Turbidez de la orilla | Observación (la escala de turbidez de la ficha) | Prueba la hipótesis de alimento removido |
| Línea de espuma o flotantes en tu orilla (sí/no) | Observación | Indicador visible de acumulación |
| Problemas prácticos (pitidos falsos, plomo arrastrado, lance corto, comba) | Casillas | Separar «no había peces» de «no pude pescar bien» |
| Señales de peces (saltos, burbujas, barro) y su orilla | Observación con hora (avisos de la sesión guiada) | Comparar la orilla batida con la resguardada aunque no haya capturas |
| Capturas y picadas con hora, y caña-hora pescada | Ya existente | La medida que se compara es picadas por caña-hora |

**Cómo usarlo (🟣):** compara las picadas por caña-hora entre «de cara / de espaldas / lateral», separando por estación y por el signo de aire − agua. No subas el peso de ninguna regla de viento hasta tener bastantes sesiones en cada grupo; como orientación, unas 20–30 por categoría (criterio propuesto, no un dato con fuente). Mientras tanto, la temperatura del agua manda.

---

## 10. Validación y aprendizaje con pocos datos

1. **Fijar reglas y pesos antes de recoger datos** (preregistro informal).
2. Métrica: **capturas por hora-caña**, no por sesión.
3. Actualización bayesiana sencilla (p. ej. tasa Poisson-gamma por categoría de temperatura): funciona con 20–40 sesiones.
4. Validación **leave-one-out** y comparación contra un **modelo nulo** (solo temperatura + estación).
5. **No añadir una variable hasta que mejore la predicción fuera de muestra.**
6. Con <50 sesiones, la mayoría de efectos secundarios (luna, presión) serán indistinguibles de cero: no sobreajustar.
7. Una regla 🟣 solo sube de peso si los datos propios la respaldan de forma consistente.
8. *(Añadido oct. 2026.)* Registrar también **picadas por hora-caña**: las capturas serán escasas.
9. Con pocas capturas, usar medias con contracción (shrinkage) bayesiana hacia lo que dice la estrategia y **no cambiar una regla con menos de ~10 sesiones** en esa condición.
10. Mostrar intervalos, no rankings, y tener en cuenta que un cebo que funciona al principio puede dejar de hacerlo (aprendizaje de los peces, #40).

---

## 11. Orden de construcción sugerido (MVP)

1. Ficha de sesión con GPS, hora y almacenamiento local (Room).
2. Descarga automática de meteorología actual y de los 3–7 días previos + sol/luna (Open-Meteo, SunCalc).
3. Nivel del embalse (semanal + entrada manual).
4. Filtro legal (horario, cañas) + tendencia de temperatura.
5. Estimador simple y visible de "condiciones favorables" con explicación y etiquetas de evidencia.
6. Más adelante: aprendizaje local con datos propios cuando haya 20–40 sesiones.

---

## 12. Pendientes y huecos de conocimiento

1. Abrir a mano los informes de la CHG (2018–2019 y 2023), filas de Brovales / GN00000763: clorofila a, Secchi, profundidad máxima, perfiles de T y O₂, significado de "ALTO". Umbrales CHG clorofila a: mesotrófico 2,5–8 mg/m³, eutrófico 8–25 mg/m³.
2. Medir perfiles propios de temperatura (y O₂ si es posible) en verano, junto a la presa y en la cola: sería el dato local más valioso.
3. Confirmar con el Servicio de Pesca de la Junta: prohibición de cebado en Valuengo y cambios en tramos de horario libre en 2026.
4. Localizar fuentes primarias del ritmo 08–11/19–23 h y del máximo a 28 °C, o eliminarlos (ya marcados como no usar).
5. Buscar estudios de dieta de carpa en embalses ibéricos (grupo de García-Berthou, Univ. de Girona) para validar el papel del cangrejo rojo.
6. Pedir acceso al SAIH Guadiana para ver si hay serie de cota de Brovales con más frecuencia que la semanal.
7. *(Añadido oct. 2026.)* Pedir por escrito al Servicio de Pesca y Acuicultura: si Brovales o Valuengo se consideran «embalse de abastecimiento»; si se permiten pescado o mejillón de mar como cebo («cuando sean de río»).
8. Leer el anexo completo de la Resolución de 18/03/2026 (DOE núm. 59, de 26/03/2026).
9. Batimetría propia de Brovales con sonda o plomo marcador (o datos de la CHG).
10. Calibrar con las sesiones el tiempo hasta la primera picada con cebado al momento (🔴, no existe estudio) y el efecto del viento sobre la ubicación (🔴).
11. Presencia de cangrejo rojo y tortugas en Brovales: deducirla del estado del cebo registrado.

## 13. Caveats

- Varias fuentes oficiales de la CHG no se pudieron descargar automáticamente: los datos limnológicos de Brovales son "sin datos", no "inexistentes".
- Volúmenes semanales de agregadores redondeados a hm³ (±0,5 hm³).
- Los estudios de telemetría disponibles (Flix, río Perla, lagos del norte de Europa) son de sistemas distintos a Brovales: sirven para refutar reglas universales, no para fijar reglas locales.
- Normativa citada: Orden de 2022 con modificaciones conocidas hasta marzo de 2026. Confirmar en DOE antes de cada temporada.
- *(Añadido oct. 2026.)* La telemetría de §5.9 procede de lagos de Norteamérica y Centroeuropa o del Ebro: su transferencia a un embalse monomíctico del suroeste es 🟣. La fisiología (ingesta, digestibilidad, gusto) viene de acuicultura o laboratorio con peces jóvenes. La eficacia comparada de cebos se basa en un solo estudio con caña. Las fuentes de pescadores (Eurocarp, fabricantes, blogs) son 🟡 y algunas tienen interés comercial.

- *(Añadido oct. 2026, viento.)* La física del viento viene de revistas de limnología y de NOAA/NWS; lo de carpas «siguiendo al viento» viene de revistas de pesca, tiendas y fabricantes (🟡, a veces con interés comercial y contradictorios entre sí). Los estudios de plancton son de lagos canadienses estratificados de más de 20 km²; el de capturas (Escanaba) es de un lago de tamaño parecido pero con depredadores visuales. La seiche de ≈7–8 min está calculada (Merian), no medida. Los umbrales de 2 °C, 10/15 km/h y 12 h son propuestas 🟣. Burguillos no se ha analizado por separado: aplicar la misma lógica con su fetch y orientación.

## 14. Referencias clave

- Benito, Benejam, Zamora y García-Berthou 2015. *Diel cycle and effects of water flow on activity and use of depth by common carp.* Trans. Am. Fish. Soc. 144:491–501.
- Garcia y Adelman 1985. *In situ estimate of daily food consumption and alimentary canal evacuation rates of common carp.* J. Fish Biol.
- Lovén Wallerius et al. 2020. *Hook avoidance induced by private and social learning in common carp.* TAFS 149(4):498–511.
- Czapla et al. 2023. *Reexamining one-trial learning in common carp… no evidence for hook avoidance lasting more than seven months.* Fish. Res. 259:106573.
- Žák 2021. *Diel pattern in common carp landings from angling competitions.* Fish. Res. 243:106086.
- Bauer y Schlott 2006. *Reaction of common carp to oxygen deficiency in winter.* Aquaculture Research.
- Zhang et al. 2020. Carpa en el río Perla, telemetría acústica (Water).
- FAO, ficha de especies cultivadas *Cyprinus carpio* y manual de propagación de peces de aguas cálidas.
- Plan Hidrológico del Guadiana 2022–2027 (Anexo VI); informes de estado de embalses de la CHG; inventario de presas MITECO; Ley 11/2010 de pesca y acuicultura de Extremadura; Orden General de Vedas de 7/11/2022 (DOE) y resoluciones posteriores.
- *(Añadidas oct. 2026; lista completa en `investigacion/2026-10-alternativas-sesion.md`.)*
- Bajer, Lim, Travaline, Miller y Sorensen 2010. Environ. Biol. Fishes 88:295–300.
- Ghosal, Eichmiller, Witthuhn y Sorensen 2018. Ecology and Evolution.
- Hundt et al. 2022. Ecology and Evolution.
- Bullers, Bajcz, Mensinger y Bajer 2026. Management of Biological Invasions 17(1):133–154.
- Kasumyan, Kuzishchin y Gruzdeva 2024. Journal of Ichthyology 64:689–704.
- Kasumyan y Morsi 1996; Kasumyan y Døving 2003, Fish and Fisheries 4:289–347; Kasumyan et al. 2009.
- Monk y Arlinghaus 2017. PLoS ONE.
- Klefoth, Pieterek y Arlinghaus 2013. Fish. Manag. Ecol.
- Beukema 1970.
- Penne y Pierce 2008. TAFS 137:1050–1062.
- Bajer, Chizinski y Sorensen 2011. Fish. Manag. Ecol. 18:497–505.
- Taylor, Tracey, Hartmann y Patil 2012. Mar. Freshw. Res. 63:587–597.
- Hennen et al. 2014. NAJFM.
- *Network analysis of intra- and interspecific freshwater fish interactions using year-around tracking.* J. R. Soc. Interface 18(183):20210445 (2021).
- Yamamoto et al. Aquaculture (autoselección de macronutrientes).
- Ateşşahin y Dürrani 2023. Fisheries Research 261:106640.
- Pérez-Bote, Pula y Cascos 2000. Graellsia 56:71–78.
- *(Añadidas oct. 2026, viento; lista completa en `investigacion/2026-10-viento.md`.)*
- HESS 2020, 24:3399. *Evolution and dynamics of the vertical temperature profile in an oligotrophic lake.*
- Blukacz, Sprules, Shuter y Richards 2010. Limnology & Oceanography (zooplancton y viento, lago Opeongo).
- Freshwater Biology 2022, 67:991 (zooplancton a sotavento, lago Opeongo); Cyr 2017, Water Research (fitoplancton).
- Zheng et al. 2015. J. Environ. Sci. (resuspensión, Taihu); Bailey y Hamilton 1997. Ecological Modelling (Thomsons Lake).
- Stoll, Hofmann y Fischer 2010. J. Fish Biol. (oleaje y alimentación de ciprínidos jóvenes); Stoll y Fischer 2011. Oecologia.
- Shaw, Renik y Sass 2021. PLOS ONE 16:e0257882 (viento y capturas, Escanaba Lake).
- Gorham y Boyce 1989. J. Great Lakes Res.; Martinsen, Andersen y Sand-Jensen 2019. Hydrobiologia (estratificación diurna en lagos someros).
- Brownscombe et al. 2023. Hydrobiologia (hábitat de carpa, Toronto Harbour); Bzonek, Kim y Mandrak 2021. Mar. Freshw. Res.
- NOAA/NWS, seguridad frente a rayos («When thunder roars, go indoors»).
