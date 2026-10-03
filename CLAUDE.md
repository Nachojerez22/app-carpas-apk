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
ui/
  navigation/ theme/ location/ conditions/ strategy/ journal/ about/
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
