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
- id: viento_sotavento
  nivel: 3
  evidencia: amarillo
  descripcion: Viento cálido sostenido acumula agua cálida a sotavento. Fetch corto en Brovales
  peso_inicial: bajo
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
- 🟢 física: el agua calentada se acumula a sotavento. 🟡 experiencia: pescar "de cara al viento" cálido. 🔴/🟡 no demostrados: seiches y "viento nuevo". Tratarlo como **desempate**, no como regla principal.

### 5.5 Cebado y cebos (con normativa)
- Ley 11/2010 (art. 40) autoriza cebado previo con productos no tóxicos.
- Orden de 2022: **prohibido cebar en embalses de abastecimiento** (probablemente Valuengo; confirmar con el Servicio de Pesca). A Brovales (uso de riego), en principio no.
- Prohibidos como cebo en aguas de río: almejas, mejillones, cangrejos y peces continentales. El único pez vivo permitido en aguas embalsadas es la tenca de acuicultura acreditada. Prohibido el clonk.
- **Cangrejo rojo: invasor; prohibido poseerlo o transportarlo vivo.** Captura con reteles solo para autoconsumo.
- Cantidad de cebo: ajustar a temperatura (§5.1). La regla "menos cebo en frío" es correcta en el fondo (demanda baja), aunque la explicación de "no digiere" no lo sea.

### 5.6 Montajes
- Pop-up, Zig Rig y bolsas de PVA son técnicas de pescadores con lógica pero **sin estudios comparativos en carpa** (🟡).
- Wallerius 2020 respalda tres ideas generales: rotar presentaciones en aguas presionadas, usar bajos menos visibles, no repetir el mismo montaje en el mismo puesto con peces ya capturados.

### 5.7 Presión de pesca
- Las capturas repetidas en un puesto reducen la capturabilidad durante semanas o meses. La app registra capturas por zona y sugiere rotar.

---

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

### 6.4 Valuengo (embalse vecino, mismo uso de riego)
- Abastece de agua potable a Fregenal de la Sierra y otras poblaciones; la Orden de Vedas lo trata como **coto** sin límite de puestos, permiso diario código **12081-1**, con su Plan Técnico de Gestión. Posible prohibición de cebar (confirmar).

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

---

## 10. Validación y aprendizaje con pocos datos

1. **Fijar reglas y pesos antes de recoger datos** (preregistro informal).
2. Métrica: **capturas por hora-caña**, no por sesión.
3. Actualización bayesiana sencilla (p. ej. tasa Poisson-gamma por categoría de temperatura): funciona con 20–40 sesiones.
4. Validación **leave-one-out** y comparación contra un **modelo nulo** (solo temperatura + estación).
5. **No añadir una variable hasta que mejore la predicción fuera de muestra.**
6. Con <50 sesiones, la mayoría de efectos secundarios (luna, presión) serán indistinguibles de cero: no sobreajustar.
7. Una regla 🟣 solo sube de peso si los datos propios la respaldan de forma consistente.

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

## 13. Caveats

- Varias fuentes oficiales de la CHG no se pudieron descargar automáticamente: los datos limnológicos de Brovales son "sin datos", no "inexistentes".
- Volúmenes semanales de agregadores redondeados a hm³ (±0,5 hm³).
- Los estudios de telemetría disponibles (Flix, río Perla, lagos del norte de Europa) son de sistemas distintos a Brovales: sirven para refutar reglas universales, no para fijar reglas locales.
- Normativa citada: Orden de 2022 con modificaciones conocidas hasta marzo de 2026. Confirmar en DOE antes de cada temporada.

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
