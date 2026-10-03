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
ui/
  navigation/ theme/ components/   navegación de 5 pestañas, tokens del diseño y componentes
  today/ strategy/ manual/ diary/ place/   pestañas Hoy, Estrategia, Datos, Diario y Lugar
                      (diary/: lista, ficha de sesión y exportación del diario)
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

## Git

- Una rama por fase: `feature/fase-N-<tema>`. Commits pequeños con Conventional Commits
  (`feat:`, `fix:`, `test:`, `docs:`, `build:`, `ci:`, `refactor:`, `chore:`).
- Un PR por fase hacia `main` con descripción y checklist. **No se hace merge sin aprobación
  del propietario.**
