# CarpStrategy

App Android nativa, **personal y no comercial**, que analiza el tiempo de los días anteriores
y del propio día de pesca y propone una **estrategia de pesca de carpas** a partir de reglas
configurables. Zona por defecto: Brovales / Jerez de los Caballeros (Badajoz, Extremadura),
38.37 N, 6.88 O. La ubicación se podrá cambiar por GPS o a mano.

> Estado: **Fase 0** (esqueleto del proyecto). Las pantallas son provisionales.

## Fuentes de datos

| Fuente | Uso | Clave |
|---|---|---|
| [Open-Meteo](https://open-meteo.com/) | Serie base horaria (7 días pasados + 3 de previsión) con varios modelos europeos (ICON-EU, ARPEGE Europe, ECMWF IFS) para calcular la media y la divergencia entre modelos | No necesita |
| [AEMET OpenData](https://opendata.aemet.es/) | Observaciones reales de la estación más cercana (~24 h) para corregir el sesgo del modelo | **Sí** (gratuita) |
| SAIH Guadiana / boletín hidrológico | Nivel de los embalses (fase 2+, **pendiente de investigar**) | — |

Los datos de Open-Meteo se publican con licencia CC BY 4.0, y los de AEMET están © AEMET.
Ambas fuentes se citan en la pantalla **Acerca de**.

### Limitaciones (importante)

- **La temperatura del agua NO se mide**: es una *estimación* a partir de una media móvil
  ponderada de la temperatura del aire de los últimos días. Puede desviarse varios grados,
  sobre todo en embalses profundos o tras cambios bruscos de tiempo.
- AEMET solo ofrece unas 24 h de observación horaria, así que la corrección de sesgo es aproximada.
- Las reglas de estrategia iniciales son **PLACEHOLDER** (ejemplos) hasta que se sustituyan
  por conocimiento real de pesca.

## Obtener la API key de AEMET

1. Ve a <https://opendata.aemet.es/centrodedescargas/altaUsuario> e introduce tu correo.
2. AEMET te envía un email de confirmación y, después, otro con la API key (una cadena JWT larga).
3. Copia `local.properties.example` como `local.properties` (en la raíz del proyecto) y añade:
   ```properties
   AEMET_API_KEY=tu_clave
   ```
4. `local.properties` está en `.gitignore`, así que **nunca se sube**. La clave se inyecta en
   `BuildConfig.AEMET_API_KEY` al compilar. También puedes pasarla con la variable de entorno
   `AEMET_API_KEY`.

Sin clave la app compila y funciona, pero sin observaciones de AEMET (lo indica en *Acerca de*).

> Nota: cualquier clave incluida en un APK se puede extraer. Para un uso personal es aceptable;
> no distribuyas el APK con tu clave.

## Requisitos y compilación

- JDK 17 o superior, y Android SDK con la plataforma 37 (Android Studio la instala sola).
- Kotlin 2.4, AGP 9.4 y Gradle 9.6 (con wrapper incluido). minSdk 26, targetSdk 37.

```bash
./gradlew assembleDebug        # APK de depuración en app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # tests unitarios (JUnit 5)
./gradlew lintDebug            # lint
```

## Arquitectura

Un único módulo `:app` con el patrón MVVM + Repository + UseCases. Usa Jetpack Compose
(Material 3), Hilt, Room, Retrofit + Kotlinx Serialization, Coroutines/Flow y, más adelante,
WorkManager. Los detalles de los paquetes y las convenciones están en [`CLAUDE.md`](CLAUDE.md).

## Fases

- [x] **Fase 0**: proyecto, estructura, CI, documentación
- [ ] **Fase 1**: capa de datos (Open-Meteo multimodelo + AEMET + Room) y pantalla de datos en bruto
- [ ] **Fase 2**: parámetros derivados (presión, temperatura, agua estimada, viento, lluvia, astronomía)
- [ ] **Fase 3**: motor de reglas JSON y pantalla de estrategia
- [ ] **Fase 4**: UI completa, GPS y gráficas
- [ ] **Fase 5**: diario de capturas y mejoras

## CI

GitHub Actions (`.github/workflows/ci.yml`) compila, ejecuta los tests y pasa lint en cada push
a `main` y a `feature/**`, y en cada PR. El CI no usa ninguna clave real.
