# CarpStrategy

App Android nativa, **personal y no comercial**: **diario de sesiones de pesca de carpa +
estimador de condiciones**. Analiza el tiempo de los días anteriores y del propio día de pesca
y sugiere una estrategia mediante una cadena de filtros y multiplicadores configurable, siempre
con su etiqueta de evidencia. **No localiza peces.**

Zona por defecto: embalse de Brovales (Jerez de los Caballeros, Badajoz), ≈38,35 N, 6,70 O.
La ubicación se podrá cambiar por GPS o a mano.

El conocimiento del dominio (biología de la carpa, datos de Brovales, normativa extremeña y
diseño del modelo) está en [`docs/CONOCIMIENTO.md`](docs/CONOCIMIENTO.md), que es la fuente de
verdad del proyecto.

> Estado: **Fase 0** (esqueleto del proyecto). Las pantallas son provisionales.

## Fuentes de datos

| Fuente | Uso | Clave |
|---|---|---|
| [Open-Meteo](https://open-meteo.com/) | Serie base horaria (7 días pasados + 3 de previsión) con varios modelos europeos (ICON-EU, ARPEGE Europe, ECMWF IFS) para calcular la media y la divergencia entre modelos | No necesita |
| [AEMET OpenData](https://opendata.aemet.es/) | Observaciones reales de la estación más cercana (~24 h) para corregir el sesgo del modelo | **Sí** (gratuita) |
| Boletín Hidrológico semanal (MITECO) + entrada manual | Nivel del embalse (dato semanal redondeado a ±0,5 hm³). SAIH Guadiana **no tiene API pública** (acceso por cuenta) | — |

Los datos de Open-Meteo se publican con licencia CC BY 4.0, y los de AEMET están © AEMET.
Ambas fuentes se citan en la pantalla **Acerca de**.

### Limitaciones (importante)

- **La app no mide la temperatura del agua**: lo recomendable es introducir tu propia medición
  (termómetro a 0,5 m y, si se puede, a 3–5 m). Si no la hay, se *estima* con una media móvil
  ponderada de la temperatura del aire de los últimos días, que puede desviarse varios grados y
  se calibra con tus mediciones.
- AEMET solo ofrece unas 24 h de observación horaria, así que la corrección de sesgo es aproximada.
- Cada regla lleva una etiqueta de evidencia (🟢 fuerte, 🟡 moderada, 🔴 mito/insuficiente,
  🟣 hipótesis local, 🔵 variabilidad individual). Luna, presión barométrica y nubosidad se
  registran **con peso 0** porque no hay evidencia publicada en carpa.
- **Normativa:** en Brovales solo es legal pescar desde 1 h antes del orto hasta 1 h después del
  ocaso (no hay horario libre). La app nunca propone horas nocturnas. Revisa el DOE y
  pescayrios.juntaextremadura.es antes de cada temporada.

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
- [ ] **Fase 2**: parámetros derivados (presión, temperatura, agua estimada, viento, lluvia, sol/luna y ventana legal)
- [ ] **Fase 3**: motor de filtros/multiplicadores por niveles (reglas JSON con etiqueta de evidencia) y pantalla de estrategia
- [ ] **Fase 4**: UI completa, GPS, gráficas y nivel del embalse (semanal + manual)
- [ ] **Fase 5**: diario de sesiones (horas-caña, bolos, valoración previa) y aprendizaje con datos propios

## CI

GitHub Actions (`.github/workflows/ci.yml`) compila, ejecuta los tests y pasa lint en cada push
a `main` y a `feature/**`, y en cada PR. El CI no usa ninguna clave real.
