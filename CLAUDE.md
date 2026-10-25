# CLAUDE.md — CarpStrategy

App Android personal: **diario de sesiones de pesca de carpa + estimador de condiciones** a partir de
datos meteorológicos. Zona: embalse de Brovales (Jerez de los Caballeros, Badajoz).

## Conocimiento del dominio (OBLIGATORIO)

[`docs/CONOCIMIENTO.md`](docs/CONOCIMIENTO.md) es la **única fuente de verdad** sobre la carpa, Brovales,
la normativa y el modelo. Si algo contradice lo que "sabes" de carpfishing, manda ese documento.
Léelo antes de tocar reglas, textos de estrategia o el diario. Resumen de las reglas no negociables:

1. La app **no promete localizar peces**: dice "condiciones más o menos favorables según tus datos".
2. **No se suman puntos**: el modelo es una **cadena de filtros y multiplicadores** por niveles
   (0 legalidad → 1 hábitat → 2 temperatura → 3 modificadores físicos → 4 capturabilidad). Si un nivel
   inferior es bajo, los superiores no lo compensan. Preferir pertenencia difusa a umbrales duros.
3. **Etiqueta de evidencia obligatoria** en cada regla y texto mostrado: 🟢 🟡 🔴 🟣 🔵 (§2).
4. Luna, presión barométrica, nubosidad, seiches y "viento nuevo" se **calculan y registran con peso 0**.
5. **Legalidad = filtro duro**: en Brovales solo de 1 h antes del orto a 1 h después del ocaso. Nunca
   recomendar horas nocturnas. Mostrar "última revisión" de la normativa.
6. Datos de Brovales: mostrar solo lo verificado (§6.1); lo estimado (§6.2), marcado como "estimado";
   lo no verificable (§6.3), nunca.
7. Temperatura del agua: preferir la **medición del usuario**; el modelo aire→agua es una estimación
   calibrable con esas mediciones.
8. Nivel del embalse: boletín hidrológico semanal (redondeado) + **entrada manual**. SAIH Guadiana no
   tiene API pública.
9. Diario: guardar bolos, esfuerzo en **horas-caña** y la valoración que dio la app **antes** de la sesión.
10. GPS solo en local y solo durante el uso; todo dato externo puede fallar → degradar con elegancia.

## Comandos

```bash
./gradlew assembleDebug          # compilar
./gradlew testDebugUnitTest      # tests unitarios (JUnit 5)
./gradlew lintDebug              # lint
./gradlew testDebugUnitTest --tests "*GeoPointTest"   # un test concreto
```

Los fixtures de tests están en `app/src/test/resources/` (`openmeteo/` es un recorte de una
respuesta real; `aemet/` reproduce el formato real con estaciones de prueba).

## Capa de datos (fase 1)

- `data/remote/openmeteo`: la respuesta multimodelo se lee como `JsonObject` (claves con sufijo
  de modelo). Siempre `timeformat=unixtime`.
- `data/remote/aemet`: dos pasos (sobre con `datos` → descarga). Las respuestas se leen como
  `ResponseBody` y se decodifican con el charset de la cabecera o ISO-8859-15.
- Los errores remotos se clasifican en `DataError` mediante `DataSourceException`; el
  repositorio devuelve `RefreshOutcome` y nunca borra la caché si falla.
- La BD (`CarpStrategyDatabase`) es caché re-descargable con migración destructiva.

## Datos del usuario (fase 2)

- `UserDataDatabase` (`data/userdata`) guarda registros manuales, ajustes y, más adelante, el
  diario. Esquema exportado en `app/schemas` (versionarlo): cada cambio necesita una migración;
  **nunca** migración destructiva. El CI avisa si hay esquemas sin versionar.
- `domain/manual`: `ManualField` (claves JSON, rangos y si van por hora o por día),
  `ManualRecordValidator` (común al formulario y a la importación) y `SourcePriority`.
- `data/manual/ManualDataJson`: formato `carpstrategy-datos` v1 (docs/FORMATO_DATOS.md). Si se
  cambia, actualizar a la vez la documentación, el ejemplo de `docs/ejemplos` y su copia en
  `app/src/main/assets` (un test comprueba que son idénticos y válidos).
- `domain/derived/SourceMerger`: serie horaria combinada según la prioridad del usuario.

## Parámetros derivados (fase 3)

- `domain/derived/DerivedCalculator.compute(...)` reúne todo en `DerivedConditions` a partir de la
  serie combinada; `now` y la zona horaria son parámetros (nada de reloj implícito).
- Piezas: `Trends` (presión, aire, viento, lluvia/escorrentía, rachas), `WaterTemperatureModel`,
  `TemperatureBiasModel`, `ReservoirModel`, `SeasonModel`, `SolarCalculator`/`LegalWindow`,
  `MoonCalculator`. Los umbrales son constantes con nombre y comentario: cambiar ahí, no en la UI.
- Referencias de sol y luna de los tests: librería astral 3.2 (tolerancia 2 min).
- Las temperaturas de los modelos se corrigen con el sesgo frente a AEMET; las manuales y las de
  AEMET no. La temperatura del agua medida manda sobre la estimada.

## Motor de reglas (fase 4)

- Reglas en `app/src/main/assets/rules.json`, formato en `docs/REGLAS.md`. `data/rules/RulesJson`
  valida (todo o nada) y aplica las normas de CONOCIMIENTO.md §0 (rojo y exploratoria ⇒ peso 0,
  filtro duro ⇒ nivel 0). Hay tests que cargan el rules.json real y escenarios que lo evalúan.
- `domain/rules`: `RuleEngine` (cadena por niveles, factores ≤ 1, producto, nivel limitante),
  `RuleContextBuilder` (derivados → parámetros), `SessionWindows` (ventanas recortadas al
  horario legal). Nuevo parámetro ⇒ añadirlo a `RuleParameter`, al builder, a strings
  (`param_*`) y a docs/REGLAS.md.

## Interfaz (fase 5)

- Diseño de Claude Design: `docs/design/DISENO.md` y maquetas en `docs/design/referencias/`.
  Tokens en `ui/theme` (`CarpTheme(ThemePrefs)`: estilo Material/Apple × claro/oscuro, color
  dinámico opcional; `CarpTheme.colors` son colores semánticos **fijos**). Fuente Atkinson
  Hyperlegible Next en `res/font` (OFL, `docs/licencias/`).
- Componentes reutilizables en `ui/components` (insignias de evidencia y fuente, tarjetas,
  cadena de filtros, gráficas con `Canvas`). Vistas previas con `@CarpPreviews` (4 variantes).
- Navegación: Hoy · Estrategia · Datos · Diario · Lugar, más la subruta de datos en bruto.
- La apariencia y la ubicación se guardan en la tabla de ajustes de `UserDataDatabase`
  (no en DataStore). GPS: una sola lectura con `DeviceLocationProvider`, permiso pedido al pulsar.
- La lógica de cada pantalla que se pueda probar va en funciones puras (`chainRows`,
  `groupRecordsByDay`, `parseCoordinates`, `TodayCharts`) con sus tests.

## Diario de sesiones (fase 6)

- `domain/journal`: `Session` (en curso si `end == null`; horas-caña = cañas × duración salvo
  dato tecleado; bolo explícito), `SessionValidator` (errores y avisos ⚖ de horario y cañas) y
  `JournalStats` (capturas por hora-caña, resultado por valoración previa, zonas con capturas
  recientes, selección de la valoración previa).
- **Valoración previa sin sesgo retrospectivo**: `StrategyViewModel` guarda cada valoración
  (`prediction_snapshot`, una por hora y lugar, 30 días). Al crear una sesión se copia la
  calculada en ese momento (si empieza ahora) o la última de las 24 h previas al inicio. Nunca
  se recalcula después.
- Persistencia en `UserDataDatabase` v2 (tablas `session` y `prediction_snapshot`) con
  `AutoMigration(1 → 2)`. Capturas, valoración y contexto van como JSON (`JournalJson`), que
  también define la exportación `carpstrategy-diario` v1 (docs/FORMATO_DIARIO.md).
- UI en `ui/diary`: `SessionForm` y `completeSession` son puras y tienen tests.
- Fase 6.1 (preparar producción): cada `PredictionSnapshot` y el contexto de cada sesión guardan
  `FeatureSnapshot` (todos los parámetros de las reglas con sus claves JSON), el valor de cada
  nivel, las reglas activadas con su factor y la huella de rules.json (`RuleSet.fingerprint`).
  Es la base del aprendizaje de la fase 7: no quitar campos sin pensar en las sesiones ya
  guardadas. Las mediciones de la ficha (agua, turbidez, nivel) se guardan como registro de
  Datos con fuente `SessionForm.MEASUREMENT_SOURCE`. Datos y Diario se pueden exportar y
  restaurar (docs/INSTALACION.md).

## Sesión guiada (fase 7)

- `domain/guided` (puro, CONOCIMIENTO.md §5.9): `Gear` (tipos de cebo con sus funciones,
  `BaitCatalog` para elegir sustituto del equipo), `GuidedLog` (registro de **una caña**: tramos,
  avisos, propuestas y decisiones), `GuidedRecord` (una `RodTrack` por caña con nombre, avisos
  mostrados, cambios de viento, cebado inicial y condiciones de lluvia/tormenta/agua turbia con su hora), `GuidedEngine` (diagnóstico A/B/C/D y E «otras
  especies» (solo la carpa es captura), escalera por fase, tiempos 🟣 en
  `THRESHOLDS`, dos «No funciona» en 30 min ⇒ propuesta forzada, límites ⚖ del fin legal,
  `nextCheckIn`) y `GuidedSessions` (aplica cada acción a la `Session` caña a caña: picadas y
  carpas con su caña).
- Persistencia: columna `guidedJson` de `session` (BD v3, `AutoMigration(2 → 3)`) con
  `GuidedJson` (también campo `guiado` de la exportación); el equipo va en el ajuste `gear`.
- `ui/guided`: `GuidedSessionManager` (singleton; un cerrojo para pantalla, alarma y
  notificación), `CheckInScheduler` + receptores (AlarmManager, sin servicio en primer plano),
  `GuidedNotifier` (una notificación con cronómetro, solo vibra) y pantallas Guiada y Mi equipo.
  Los textos de pasos y avisos salen de `GuidedTexts` (Resources) para pantalla y notificación.

## Cuenta de Google y versiones (fase 7.2)

- `data/sync`: `SyncSnapshot` (formato `carpstrategy-sync` v1: la BD de usuario sin ids, filas
  por clave natural; los ajustes `local_…` nunca se suben), `mergeSnapshots`, `decideSync`
  (huellas SHA-256: subir, bajar, unir o nada), `DriveClient` (REST v3 con OkHttp,
  `appDataFolder`), `SyncStore` (Room), `GoogleDriveAuth` (API de autorización de Play
  Services, permiso `drive.appdata`) y `SyncManager` (al arrancar y ~15 s tras cada cambio).
  La BD local sigue siendo la fuente de verdad de la UI (funciona sin cobertura).
- `data/update/UpdateChecker`: última Release de GitHub (`v<versionName>-<versionCode>`,
  asset `CarpStrategy.apk`); aviso en `MainActivity` solo en la versión de producción.
- `data/userdata/LocalSettings`: ajustes del móvil (`local_…`) que no se sincronizan.
- Configuración de Google Cloud: docs/CUENTA_GOOGLE.md (sin secretos en el repositorio).

## Puestos, tiempo por aviso y Estrategia (fase 7.3)

- `domain/guided/Spot.kt`: `Spot` (ajuste `spots`, se sincroniza; JSON en `GuidedJson`),
  `Spots.windRelation` (orilla mirando a `facingDeg` + viento DE dónde viene → de cara/espaldas/lateral).
- `domain/guided/FieldWeather.kt`: `WeatherSnapshot` (previsión de la hora + respuestas «¿Llueve?»),
  `FieldWeather.changes`/`report` con umbrales 🟣 (nubes y presión con peso 0). `GuidedRecord`
  guarda `spot` y `weather`; un giro claro del viento cuenta como cambio de viento.
- Open-Meteo pide `weather_code` (`HourlyWeather.weatherCode`; caché v2, destructiva).
  `ui/guided/weatherSnapshotAt` toma la hora más cercana de la serie combinada y el código más severo.
- `GuidedSessionManager.onAlarm` refresca el tiempo (máx. 7 s, si no, caché) fuera del cerrojo;
  la notificación añade las líneas de `weatherLines`.
- Estrategia: resumen (+ `nextWindow` y botón a la sesión guiada) → qué hacer (+ tus puestos) →
  cuándo → avisos → «Por qué» plegado.

## Asistente de IA (fase 7.4)

- `domain/assistant`: `AiDecision`/`AiPlan`, `AiValidator` (todo o nada: pasos, equipo y puestos por
  nombre, sin %, sin gramos, sin noche, sin promesas, evidencia, ⚖ zona < 45 min, tormenta) y
  `toProposal` (propuesta con `ProposalSource.AI`). `GuidedSessions.applyAi`/`aiContext`.
- `data/assistant`: `AiClient` (OkHttp; Gemini `generateContent` con `x-goog-api-key` y
  OpenAI-compatible), `AssistantPrompts` (normas + estado; **nunca** la ubicación), `AssistantJson`,
  `Assistant` (consulta + validación → `AiOutcome`) y `AssistantSettings` (`local_ai_config`,
  `local_ai_key`; desde la fase 7.11 la clave puede ir también en la cuenta).
- Estrategia: apartado «Asistente de IA» (`AiPlanCard` + `AiAdviceCard`): plan por caña y consejos
  por `StrategyField` (`AiPlan.advice`); `AiValidator.legalHoursIssue` rechaza horas citadas fuera
  del horario legal. El último plan válido del día se restaura al abrir.
- `GuidedSessionManager.consultAi` (tras cada alarma, en `scope`, y a demanda); el último plan de
  Estrategia (`local_ai_last_plan`) se copia en la sesión si empieza en < 3 h. Todo queda en `GuidedRecord.ai`.

## Pescar más y conocimiento aprendido (fase 7.5)

- `domain/advice` (puro):
  - `DayParts` (momento del día según el sol);
  - `Experience` (unidades = tramos de cada caña o la sesión entera; `forConditions`: capturas por hora-caña por cebo, puesto, columna y momento en sesiones parecidas; `proven`);
  - `FishingAdvisor` (cebos ×2, columna, puestos con `structureFit` de §5.8, distancia y profundidad, cebado);
  - `Lessons` (`Lesson` con condiciones, origen IA o usuario, estado; `toMarkdown`).
- Las lecciones van en el ajuste `lessons` (se sincroniza; `LessonsJson`). La IA repasa cada sesión guiada al terminar (`GuidedSessionManager.reviewSession`, `AiKind.REVIEW`, validación por lección).
- `assets/conocimiento.md` es copia exacta de docs/CONOCIMIENTO.md (un test lo comprueba): **al cambiar el documento, copiarlo**. `Knowledge` extrae §5–§6 (plan y repaso) y §5.8–§5.9 (avisos) para la IA.
- `AiExtras` (tus datos, tus lecciones y la recomendación) va en los estados que se mandan a la IA. La IA no habla de normativa; el horario se sigue validando (`horario_de_pesca`).
- UI: `AdviceCard` en Estrategia; `ui/lessons` («Lo aprendido»); textos de horario sin ⚖ (normativa en «Por qué»). El plan A guiado usa `GuidedEnv.preferredBaits`.

## Varios embalses (fase 7.6)

- `domain/reservoir` (puro): `Reservoir` (catálogo o propio), `ReservoirSheet` (especies con
  `SpeciesStatus` IA/confirmada, `groundbaitAllowed`, notas), `ReservoirContext` (activo + ficha;
  sin respuesta del usuario y nombre «Abto.» ⇒ no se ceba), `Reservoirs` (más cercano ≤ 3 km,
  búsqueda sin tildes, sesiones por cercanía: **sin cambio de esquema**), `ReservoirSheets` y
  `SpeciesRules.effects` (§5.11 → `SpeciesEffects` que usa `FishingAdvisor`).
- `assets/embalses.json` (`carpstrategy-embalses` v1, docs/EMBALSES.md) se genera con `tools/`
  desde el PDF oficial de cotos. Ajustes sincronizados `reservoirs` (propios) y `reservoir_sheets`.
- `data/reservoir/ReservoirRepository`: embalse activo = el más cercano a la ubicación de Lugar;
  elegir uno es cambiar la ubicación. `Spot.reservoirId` (null = Brovales) y
  `LessonConditions.reservoirId`; `Experience.forReservoir` (propias primero, si no, todas).
- IA: `AiKind.SHEET` (`Assistant.sheet`, `AiValidator.toSheet`: respeta lo confirmado; lo que no
  sabe no se rellena). El estado lleva el bloque `embalse`; `Knowledge.forPlan(md, brovales)`
  manda §6 solo en Brovales. A la IA solo van las coordenadas públicas del catálogo.
- UI: `ui/reservoir` (Embalses y Ficha), tarjeta Embalse en Lugar, avisos en «Qué hago ahora».

## Chat con la IA en la sesión (fase 7.7)

- `ChatMessage` en `GuidedRecord.chat` (JSON `chat` en `GuidedJson`/exportación). `AiKind.CHAT`,
  `AiChatReply` (texto + cambios por caña) y `AiValidator.validate(reply, ctx)` (texto hasta
  `MAX_CHAT_TEXT`; cambios con las mismas normas que un aviso, `checkChanges`).
- `AssistantPrompts.CHAT_SYSTEM` (experto en carpas que ya conoce embalse, puesto, cañas y
  estrategia) y `chatState` (estado del aviso + `estrategia_del_dia` + `conversacion` +
  `mensaje_del_usuario`). `GuidedSessions.applyChat` guarda pregunta y respuesta y deja los cambios
  como propuestas pendientes. `GuidedSessionManager.chat` (fuera del cerrojo; aplica dentro).
- UI: `ChatCard` en la pantalla Guiada. El repaso (`reviewState`) incluye la conversación.

## Viento (fase 7.8)

- `domain/advice/WindRules` (puro, CONOCIMIENTO.md §5.4 y §5.9.12):
  - `temperature` (aire − agua ≥ 2 °C cálido, ≤ −2 frío);
  - `favours`: de cara solo con viento cálido sostenido y agua < 22 °C; de espaldas con viento frío; agua caliente ⇒ 0;
  - `tips`: avisos prácticos que no puntúan.
  `FishingAdvisor` lo usa (`AdvisorInput.airC/windPersistence/gustKmh`, `SpotPick.windTips`).
- rules.json:
  - `viento_calido_orilla_receptora`, `viento_frio_resguardo`, `viento_calido_agua_caliente` (peso 0) y `viento_fuerte_oleaje` (≥ 15 km/h);
  - parámetro nuevo `delta_aire_agua_c`.
- Diario: `SessionContext.observed` (`WindObservation`: viento en el puesto, oleaje, espuma, aire en la orilla, problemas), en el JSON del contexto (`observacion_viento`): **sin cambio de esquema**. `completeSession` lo conserva al recalcular. `Spot.fetchM` (`fetch_m`).
- El informe completo está en `docs/investigacion/2026-10-viento.md`.

## Mapa de puestos (fase 7.9)

- `domain/guided/SpotGeometry.kt` (puro):
  - `SpotMap.geometry`: puesto + orilla de enfrente → `facingDeg` y `fetchM`;
  - `destination`: línea de lance;
  - `windKind` → `SpotWindKind` para el color del marcador;
  - `MapWind`: viento de 24 h, aire y agua.
  `Geo.bearingDeg`. `Spot.point` (`lat`/`lon` en `SpotDto`): **nunca va a la IA**.
- `ui/guided/SpotsMapScreen` (maps-compose 9.0.1 + play-services-maps 20.1.0, satélite híbrido). `SpotDialog` es `internal` y tiene `onRelocate`. `SpotsViewModel` expone `center` y `wind`.
- Clave `MAPS_API_KEY` (local.properties, entorno o secreto de la CI) → `manifestPlaceholders` y `BuildConfig.MAPS_CONFIGURED`. Sin clave no se crea el mapa: hay que mostrar el aviso, porque el SDK falla sin clave. Pasos en docs/CUENTA_GOOGLE.md.

## Estrategia por embalse y puesto (fase 7.10)

- Estrategia empieza con `PlaceCard`: desplegable con tus embalses (`Reservoirs.withSpots`: los que tienen
  puestos, más el activo; elegir uno = cambiar la ubicación) y «Otro embalse…» (`ReservoirsRoute`); debajo,
  los puestos de ese embalse («Automático» o uno). La elección va en el ajuste local `chosenSpotKey(reservoirId)`
  (`local_strategy_spot_…`, no se sincroniza); un puesto borrado o de otro embalse vuelve a automático.
- `AdvisorInput.chosenSpotId` → `FishingAdvice.chosen` y `better` (otro puesto con ≥ `BETTER_SPOT_MARGIN`
  puntos más: solo se avisa). Las reglas siguen siendo del embalse entero (el tiempo es el mismo).
- La IA también ayuda a elegir: compara tus puestos y devuelve `puesto_recomendado` (`AiPlan.bestSpot`,
  validado: puesto tuyo y con evidencia) con botón «Pescar en …»; con `puesto_elegido` (sin coordenadas)
  el plan por caña es para ese puesto. La sesión guiada lo trae marcado (`GuidedUiState.preferredSpotId`).
- Un puesto nuevo pertenece al embalse más cercano a su punto del mapa (`Reservoirs.forSpot`); sin punto, al activo.

## Clave de la IA en la cuenta (fase 7.11)

- `AssistantSettings`: la clave se guarda en el móvil (`local_ai_key`) y, si el usuario activa el
  interruptor, también en el ajuste sincronizado `ai_key_cuenta` (manda la de la cuenta; el móvil donde
  se escribe guarda copia). Proveedor y modelo van en `ai_config_cuenta` (antes `local_ai_config`, que
  queda como respaldo). Ninguno va en las exportaciones. «Borrar clave» quita las dos.

## Estrategia en 3 pasos (fase 7.12)

- `StrategyContent`: arriba, siempre, `ResultCard` («Condiciones para hoy»: escala y nivel que limita) →
  ① embalse (`ReservoirStep`) → ② puesto y cañas (`SpotStep`, con la recomendación de
  la IA o de la app) → ③ cebos de hoy (`BaitsStep`, fase 7.13) → ④ un solo plan (`PlanStep`) → «Detalles» plegado («Qué hago ahora»
  completo, reglas, horas, porqué) → al final `StartStep` («Empezar sesión guiada con este plan»).
- `domain/advice/DayPlan.kt` (puro): `DayPlans.build` hace el plan de la app por caña con `FishingAdvice`
  (3.ª caña con la columna alternativa) y, con un `AiPlan` válido, la IA manda en lo que dice
  (`PlanSource`); `contingencies` = «Si cambia algo» con los tiempos 🟣 de `GuidedEngine.THRESHOLDS`,
  `noSignalLadder` y los `WindTip` del puesto.
- IA automática (opción A): `planKey` (día, embalse, puesto o automático, cañas, franja) y
  `dayPlanFor` (el plan de la IA solo cuenta si su clave coincide). `StrategyViewModel` consulta sola
  una vez por clave (espera 1,5 s, cancela la anterior; si falla no reintenta sola) y guarda la clave
  en `local_strategy_plan_key`. Cañas en `local_strategy_rods` (`KEY_RODS`); la guiada las trae marcadas.

## Mis cebos y engodo (fase 7.13)

- `GearItem`: `brand`, `description` (qué lleva, del usuario), `url` (tienda), `available` («hoy lo llevo»;
  `List<GearItem>.today()` es lo único que usan el plan, la IA y la guiada) y `sheet` (`BaitSheet` de la IA, 🟣).
  Tipos nuevos `GROUNDBAIT_MIX`, `FLOUR`, `LIQUID` (no van al anzuelo). JSON del ajuste `gear` con campos nuevos
  y valores por defecto (equipo antiguo = se lleva hoy).
- `domain/advice/Groundbait.kt`: `GroundbaitAmounts` (tu medida `groundbait_normal_kg`, se sincroniza:
  poco ¼, mucho 2×, la mitad al empezar, 🟣); sin medida no hay kilos. `DayPlan.groundbaitAmount`/`aiGroundbait`.
- IA: el plan recibe `cebado` (nivel y tu medida) y los cebos con marca, `que_lleva` y `ficha_ia`, y devuelve
  `engodo` (`AiGroundbait`: mezcla en partes con tus cebos, `kg_por_cana` solo con tu medida y ≤ 1,5 × «mucho»;
  los textos siguen sin gramos). `AiKind.BAIT`: `Assistant.baitSheet` con Gemini y herramientas
  `google_search` + `url_context` (`AiClient.complete(web = true)`, sin JSON estricto); solo van marca,
  nombre, lo que escribió el usuario y el enlace. Si Google rechaza la búsqueda (cuota 429 del nivel
  gratuito o herramienta no admitida), se repite sin búsqueda (`BAIT_SYSTEM_NO_SEARCH`): ficha con
  `searched = false`, sin fuentes y como mucho 🟣.
- `domain/advice/BaitShelf.kt` (puro): uso de cada cebo en el diario (`usage`, por nombre sin tildes; varios
  cebos separados por comas), «Hoy llevo» / «Mis cebos guardados» por último uso (`view`), «Empezar de cero
  hoy» (`clearToday`) y cebos del diario que faltan en tu lista (`fromDiary` → `fromUsage`).
- UI: `GearEditor` (singleton: guardar, borrar, hoy, de cero, del diario, ficha) para Estrategia (paso ③) y Mi equipo;
  `GearDialog`, `BaitRow` y `GroundbaitNormalRow` son `internal` en `ui/guided/GearScreen.kt`.

## Consumo de la IA (fase 7.14)

- `domain/assistant/AiUsage.kt` (puro): `AiCall` (tokens de entrada, salida y razonamiento, búsquedas,
  error de cuota con su mensaje), `UsageDay` por **día de cuota de Google** (`QUOTA_ZONE` =
  America/Los_Angeles), `record`/`merge`/`today`/`lastDays` (`KEEP_DAYS` = 7), `AiLimits` y `level`
  (aviso al 80 %, `NEAR_FRACTION`).
- `AiClient` apunta cada respuesta (`usageMetadata` de Gemini, `usage` de OpenAI) y cada 429 en su
  `AiUsageRecorder`. `AiUsageStore` guarda en el ajuste sincronizado `ai_usage_<móvil>` (id en el
  ajuste local `device_id`; cada móvil solo escribe el suyo) y la pantalla suma todos. Límites en `ai_limits`
  (`AiUsageJson`). UI: `AiUsageCard` al final de Ajustes del asistente. Sin Claude: decisión del propietario.
- 0.20.1: `AiException.Offline` lleva `OfflineReason` (DNS, tiempo, TLS, rechazo, corte) y el detalle técnico
  sin la clave; un corte se repite una vez (`NETWORK_ATTEMPTS`), DNS y tiempo agotado no. Apuntar el consumo
  nunca rompe una consulta (`note` con `runCatching`; `AiUsageStore` con `CoroutineExceptionHandler`).
- 0.20.2: los 500/502/503/504 del proveedor también se repiten una vez (`SERVER_ERRORS`). `AiException.description`
  (código y mensaje) llega a `AiExchange.errorDetail` (no se guarda) y se muestra en la ficha del cebo y en Estrategia.
- 0.20.3: una ficha sin búsqueda guarda por qué (`BaitSheet.searchError`/`searchErrorDetail`, JSON
  `motivo_sin_busqueda`/`detalle_sin_busqueda`): cuota (con la hora de renovación) u otro rechazo de Google.

## Preparar la salida y gastar menos IA (fase 7.15)

- Ficha del cebo (`Assistant.baitSheet(…, searchAllowed)`): con enlace solo `url_context` (`AiClient.complete(search =
  false)`, `BAIT_SYSTEM_URL_ONLY`: no gasta búsquedas de Google); sin enlace, `google_search`. Tras un «cuota agotada»
  de la búsqueda, `AiUsageStore` guarda `ai_search_blocked_until` (se sincroniza) hasta `AiUsage.nextReset` y no se
  vuelve a intentar. `List<GearItem>.pendingSheets` + `GearEditor.refreshPendingSheets` («Buscar de nuevo las fichas
  pendientes» en Mi equipo; lo pulsa el usuario). El consumo avisa de las búsquedas agotadas.
- `domain/guided/AiCheckInPolicy` (puro): `AiCheckInMode` (NEVER, ON_CHANGE por defecto, ALWAYS) en `AiConfig.checkInMode`
  (JSON `modo_avisos`; el antiguo `en_cada_aviso` sí ⇒ ON_CHANGE). ON_CHANGE pregunta la primera vez y si cambia el
  tiempo o las condiciones, «No funciona», otras especies, toques/fallos o tras `MAX_QUIET` (2 h, 🟣).
- `ui/strategy/TripPrep.kt` (puro): `nextOuting` (inicio legal de la próxima salida + 30 min), `outingKey` (`planKey` sin
  la franja) y `aiPlanFor` (plan pedido para lo elegido o, si no, el preparado para la misma salida).
  `StrategyViewModel.prepareNextOuting`: previsión, plan de la IA evaluado a esa hora (`withInputs`) y fichas pendientes.
  El plan preparado va en el ajuste local `ai_prepared_plan` (+ `strategy_prepared_key`): no se pisa con el automático,
  evita la consulta automática de esa salida y la sesión guiada lo copia si es de hoy. `PrepareStep` al final de Estrategia.

## Stack

- Kotlin 2.4, AGP 9.4 (Kotlin integrado: **no** se aplica `org.jetbrains.kotlin.android`) y Gradle 9.6.
- Compose (Material 3), Navigation Compose con rutas `@Serializable` tipadas, Hilt (KSP),
  Room (KSP), Retrofit + Kotlinx Serialization, Coroutines/Flow y WorkManager.
- Tests: JUnit 5 (Jupiter, mediante `useJUnitPlatform()`), MockK y Turbine.
- Todas las versiones están en `gradle/libs.versions.toml`. Antes de subir una versión,
  compruébala en la documentación oficial.

## Estructura (`app/src/main/java/com/nachojerez/carpstrategy/`)

```
data/
  remote/openmeteo/   API + DTOs de Open-Meteo (multimodelo)
  remote/aemet/       API en dos pasos de AEMET OpenData + DTOs
  local/              Room: entidades, DAOs, base de datos (caché con marca de tiempo, diario)
  repository/         implementaciones de repositorios (offline-first)
domain/
  model/              modelos de dominio puros
  usecase/            casos de uso
  derived/            parámetros derivados: funciones PURAS y 100 % testeadas
  rules/              motor de filtros/multiplicadores (reglas en assets/rules.json)
  journal/            diario: sesiones, validación y estadísticas (capturas por hora-caña)
  guided/             sesión guiada: equipo, motor de alternativas y registro por tramos
ui/
  navigation/ theme/ components/   navegación de 5 pestañas, tokens del diseño y componentes
  today/ strategy/ manual/ diary/ place/   pestañas Hoy, Estrategia, Datos, Diario y Lugar
                      (diary/: lista, ficha de sesión y exportación del diario)
  guided/             sesión guiada, Mi equipo, avisos (alarmas) y notificación
  conditions/         datos en bruto por fuente (subruta desde Hoy) y su ViewModel
di/                   módulos de Hilt
```

Los paquetes se crean conforme los necesita cada fase.

## Convenciones

- **Idioma:** los textos de la UI, la documentación y los comentarios de dominio van en
  español. Los identificadores de código van en inglés.
- Todos los textos de la UI van en `res/values/strings.xml`, nunca hardcodeados en Compose.
- La capa `domain` no depende de Android (es Kotlin puro y testeable en la JVM).
- Los cálculos derivados son funciones puras sin I/O ni reloj implícito: el instante actual se
  pasa como parámetro.
- Los ViewModels exponen `StateFlow<UiState>`, y los repositorios devuelven `Flow` desde Room
  (fuente única de verdad).
- Si un dato es una estimación o no es fiable (por ejemplo, la temperatura del agua), se indica
  en la UI y en el README.
- Hay que citar Open-Meteo (CC BY 4.0) y AEMET en la pantalla "Acerca de".

## Secretos

- `AEMET_API_KEY` se lee de `local.properties` o de la variable de entorno y se expone en
  `BuildConfig.AEMET_API_KEY`.
- **Nunca** se suben `local.properties`, claves ni keystores (`*.jks`, `*.keystore`).
- Firma de producción: `CARP_KEYSTORE_FILE`, `CARP_KEYSTORE_PASSWORD`, `CARP_KEY_ALIAS` y
  `CARP_KEY_PASSWORD` (local.properties o entorno). En la CI salen de los secretos
  `CARP_KEYSTORE_BASE64` y compañía; la clave debe ser siempre la misma (docs/INSTALACION.md).
- La CI compila y prueba en cada ejecución, pero **solo publica el APK de producción** (Release en
  `main`). Ya no se sube el APK de prueba: decisión del propietario, oct. 2026.
  `versionCode` = número de ejecución (`CARP_VERSION_CODE`).

## Git

- Una rama por fase: `feature/fase-N-<tema>`. Commits pequeños con Conventional Commits
  (`feat:`, `fix:`, `test:`, `docs:`, `build:`, `ci:`, `refactor:`, `chore:`).
- Un PR por fase hacia `main` con descripción y checklist. **No se hace merge sin aprobación
  del propietario.**
