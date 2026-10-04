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

> Estado: **Fase 4** (motor de reglas). *Estrategia* muestra la valoración por cadena de filtros, la demanda esperada, ventanas horarias legales, la estrategia por campos y el porqué de cada regla. *Condiciones* muestra los parámetros del día y los datos en bruto.

## Fuentes de datos

| Fuente | Uso | Clave |
|---|---|---|
| [Open-Meteo](https://open-meteo.com/) | Serie base horaria (7 días pasados + 3 de previsión) con varios modelos europeos (ICON-EU, ARPEGE Europe, ECMWF IFS) para calcular la media y la divergencia entre modelos | No necesita |
| [AEMET OpenData](https://opendata.aemet.es/) | Observaciones reales de la estación más cercana (~24 h) para corregir el sesgo del modelo | **Sí** (gratuita) |
| Boletín Hidrológico semanal (MITECO) + entrada manual | Nivel del embalse (dato semanal redondeado a ±0,5 hm³). SAIH Guadiana **no tiene API pública** (acceso por cuenta) | — |

Los datos de Open-Meteo se publican con licencia CC BY 4.0, y los de AEMET están © AEMET.
Ambas fuentes se citan en la pantalla **Acerca de** y junto a los datos.

### Cómo se usan (fase 1)

- **Open-Meteo:** una petición con `models=icon_eu,meteofrance_arpege_europe,ecmwf_ifs025`,
  `past_days=7`, `forecast_days=3`, `timezone=Europe/Madrid` y `timeformat=unixtime` (horas sin
  ambigüedad en los cambios de hora). Variables: temperatura a 2 m, presión a nivel del mar,
  viento a 10 m (velocidad, dirección y rachas), nubosidad, precipitación y radiación de onda
  corta. Cada modelo llega en su propia serie (`temperature_2m_icon_eu`, …): se guardan por
  separado y se calcula la **media** y la **divergencia** (máximo − mínimo) entre modelos.
  Hay aviso de incertidumbre del viento si la velocidad difiere ≥ 10 km/h, o la dirección
  ≥ 90° con viento medio ≥ 5 km/h (umbrales de trabajo, ajustables).
- **AEMET:** se descarga el inventario de estaciones (se renueva cada 30 días), se eligen las
  3 más cercanas a menos de 60 km (distancia haversine) y se usa la primera que publique
  observación horaria. Los decimales con coma, "Ip" (inapreciable = 0) y la codificación
  ISO-8859-15 se tratan al leer. El viento se convierte de m/s a km/h.
- **Datos manuales e importados:** en *Condiciones → Datos manuales* puedes añadir registros a
  mano (por hora o por día) o importar un JSON con el formato documentado en
  [`docs/FORMATO_DATOS.md`](docs/FORMATO_DATOS.md) (ejemplo en
  [`docs/ejemplos/datos-ejemplo.json`](docs/ejemplos/datos-ejemplo.json), también copiable desde
  la app). Todo se valida (rangos, fechas, fuente obligatoria) y la importación es todo o nada.
  Lo importado se marca como *no verificado*. Se guardan en una base de datos aparte que nunca
  se borra al actualizar la app.
- **Prioridad de fuentes:** eliges el orden de Manual, AEMET y Modelos y cuáles están activas.
  Para cada hora y variable se usa la primera fuente activa con valor; la vista *Combinada*
  indica la fuente de cada valor (M, A, P).
- **Caché (Room):** todo se guarda con la hora de descarga. Sin conexión se muestra la última
  copia y su antigüedad (aviso a partir de 3 h y de 24 h). Al abrir la pantalla se actualiza si
  los datos tienen más de 1 h.

### Parámetros derivados (fase 3)

Funciones puras con tests (`domain/derived`), calculadas sobre la serie combinada según tu
prioridad de fuentes. Cada una se muestra con su etiqueta de evidencia.

| Parámetro | Cálculo | Evidencia |
|---|---|---|
| Horario legal | Orto/ocaso (algoritmo NOAA, ±1–2 min) − 1 h / + 1 h | Normativa (filtro duro) |
| Temperatura del agua | Tu medida de superficie si es de las últimas 48 h; si no, **estimación**: media ponderada del aire de los 7 bloques de 24 h previos, pesos 7…1 (el más reciente pesa más), calibrada con el desfase medio de tus medidas de los últimos 30 días (máx. ±5 °C). Tendencia: estimación ahora − hace 3 días | 🟢 (la relación con la demanda); la estimación es solo orientativa |
| Estación por el agua | < 10 °C invierno, > 22 °C verano, entre medias según tendencia (±0,5 °C/3 días); plana → calendario | 🟡 |
| Tendencia del aire | Media 24 h frente a los 3 días previos | — |
| Sesgo modelos vs AEMET | Media (modelo − estación) en las horas comunes de las últimas 48 h (mín. 6), con la estación llevada a la altitud de la rejilla (−6,5 °C/km); se resta a las temperaturas de los modelos (máx. ±5 °C) | — |
| Rachas | Días completos seguidos hasta ayer con máxima ≥ 30 °C o media ≤ 8 °C (umbrales de trabajo) | — |
| Viento 24/48 h | Dirección dominante (media vectorial ponderada), velocidad media, persistencia (0–100 %), racha máxima | 🟡 |
| Lluvia | 24 h, 72 h, índice de lluvia previa (7 bloques de 24 h antes de las 72 h, factor 0,9/día) y horas desde la última | — |
| Escorrentía probable | (72 h ≥ 20 mm **y** lluvia previa ≥ 10 mm) o 24 h ≥ 40 mm. "20 mm sobre suelo seco no cuentan" | 🟣 hipótesis local |
| Nivel del embalse | Última lectura manual/importada y variación respecto a la de hace ~7 días (±3); % ↔ hm³ con la capacidad oficial 6,98 hm³ | Dato del usuario |
| Presión (Δ3/24/72 h, σ 48 h), nubosidad, luna | Se calculan y se muestran **con peso 0** | 🔴 |

### Estrategia (fase 4)

Las reglas viven en [`app/src/main/assets/rules.json`](app/src/main/assets/rules.json) (formato
documentado en [`docs/REGLAS.md`](docs/REGLAS.md)), traducidas de `CONOCIMIENTO.md` §4.1, §5 y
§5.8 con su etiqueta de evidencia. El motor es una **cadena de filtros y multiplicadores por
niveles** (0 legalidad → 1 hábitat → 2 temperatura → 3 modificadores físicos → 4 capturabilidad):
no suma puntos, un nivel bajo no se compensa con otro alto y se muestra el nivel que limita.
Luna, presión y nubosidad aparecen con peso 0. Las ventanas horarias se sugieren siempre dentro
del horario legal. El JSON se valida al cargarlo y los errores se muestran en la app.

### Interfaz (fase 5)

Cinco pestañas según el diseño de `docs/design/`: **Hoy** (agua medida o estimada, aire, viento,
lluvia, embalse, horario legal, datos con peso 0 y gráficas de 5 días + 3 de previsión con la
banda entre modelos), **Estrategia** (resultado en 5 tramos, cadena de filtros 0 → 4, demanda,
franjas dentro del horario legal, consejos y reglas activadas con su evidencia), **Datos**
(registros por día, formulario completo e importación JSON con vista previa), **Diario** (fase 6)
y **Lugar** (GPS o coordenadas, apariencia Material/Apple y claro/oscuro, fuentes, leyenda de
evidencias, limitaciones y normativa con su fecha de revisión). La ubicación del GPS se lee una
sola vez al pulsar el botón y no sale del móvil.

### Diario (fase 6)

Pulsa **Nueva sesión** al llegar al agua: la app guarda en ese momento su valoración y, al
terminar, anotas picadas, capturas (hora, peso, caña), pérdidas y si fue **bolo** (una sesión
sin capturas también es un dato). El esfuerzo se mide en **horas-caña** y la métrica es
capturas por hora-caña. Cada ficha guarda una **copia fija** de lo que dijo la app antes de
salir (nunca se recalcula después, para no sesgar la comparación) y el contexto del momento
(agua, aire, viento, lluvia, nivel; luna y presión con peso 0). El diario muestra el resumen
del mes, cómo te fue según la valoración previa (no concluyente con menos de 20 sesiones) y
avisa de rotar puesto si capturaste en la misma zona en los últimos 14 días. Las sesiones solo
están en el móvil: **Exportar diario** guarda una copia en JSON
([formato](docs/FORMATO_DIARIO.md)).

### Sesión guiada (fase 7)

Desde **Diario → Pescar ahora (guiado)**. La app propone un plan A (cebo de la fase según la
temperatura del agua, a fondo) con lo que llevas en **Mi equipo** (cebos y montajes que añades
tú). Lo aceptas o lo rechazas con un motivo («no tengo ese cebo»…) y un comentario. Cada 30 min
(45 en invierno) te llega un aviso que **solo vibra**, con botones para anotar sin abrir la app
(nada, picada, captura). Si no funciona, propone el siguiente paso de la escalera (presentación,
montaje, capa, distancia, zona) según lo que ves: sin señales, con señales sin picadas, toques o
cebo desaparecido. **Si pulsas «No funciona» dos veces en 30 min, propone ya.** Todos los tiempos
son hipótesis 🟣 a calibrar con tus sesiones; nunca propone cambios de zona a menos de 45 min del
fin del horario legal. Cada tramo, aviso y decisión queda en el diario para aprender después.
Cada caña lleva su nombre («fija», «carrete») y su propio plan y registro. En cada aviso puedes
anotar la especie (carpa, barbo, boga, black bass o pequeño): solo la carpa cuenta como captura;
si entran otras especies o mordisquean, propone un cebo más selectivo y, si cebaste mucho, cebar
menos. Anota también el cebado (poco, normal, mucho), los recebados, si cambia el viento y, en
**Tiempo y agua**, la lluvia (ligera o fuerte), la tormenta y si entra agua turbia, juntas o por
separado y con su hora. Con tormenta la app te recuerda ponerte a salvo y no propone cambios.
Para que los avisos lleguen a su hora, permite las notificaciones y las alarmas exactas.

### Mis puestos, el tiempo en cada aviso y Estrategia (fase 7.3)

En **Lugar → Mis puestos** guardas tus puestos con lo que mediste tú: estructura (borde somero,
primera caída, antiguo cauce, recula, punta, llano), profundidad, distancia del lance y hacia
dónde mira la orilla. Al empezar la sesión guiada eliges el puesto y cada aviso trae el tiempo:
la previsión de la hora (aire, viento y rachas, lluvia, tormenta, agua medida o estimada), qué ha
cambiado desde el aviso anterior y desde el inicio, si el viento te da de cara, de espaldas o de
lado, y cuánto queda para el ocaso y el fin legal. Es una **previsión**: si no cuadra, la app
pregunta «¿Llueve?» y lo que contestas manda. Nubes y presión se muestran con peso 0. La pestaña
**Estrategia** va en orden de uso: resumen con la próxima ventana legal y **Pescar ahora
(sesión guiada)**, qué hacer (dónde y tus puestos, presentación, cebado), cuándo, avisos y, plegado,
el porqué (cadena de filtros, demanda y reglas).

### Asistente de IA (fase 7.4)

Opcional. En **Lugar → Asistente de IA** pegas la clave de tu proveedor (Gemini de Google AI
Studio, nivel gratuito, u otro compatible con OpenAI) y el modelo. La clave se queda en el móvil:
no se sube a Drive ni va en las exportaciones. En cada aviso de la sesión guiada la IA dice
**mantener** o **cambiar** (una cosa por caña) y en Estrategia prepara un **plan con IA**. La app
comprueba cada respuesta: solo tu equipo y tus puestos, horario legal, sin gramos, sin
probabilidades, sin prometer peces y con etiqueta de evidencia; si no cumple, la descarta y
siguen mandando las reglas. Tú aceptas o rechazas cada propuesta. Se envía el estado de la pesca,
nunca tu ubicación. En el nivel gratuito de Gemini, Google puede usar lo enviado para mejorar sus
productos. Los avisos preguntan además dónde has visto actividad y cuántos saltos.

### Cuenta de Google y versiones nuevas (fase 7.2)

En **Lugar → Cuenta de Google** inicias sesión una vez y tus datos (diario, datos manuales,
equipo y ajustes) quedan en una carpeta privada de tu Google Drive que solo ve la app. La app
tira de ahí al abrirse y sube cada cambio; sin cobertura funciona igual y sincroniza después.
Requiere configurar una vez un proyecto en Google Cloud: [docs/CUENTA_GOOGLE.md](docs/CUENTA_GOOGLE.md).
Cada versión se publica en **Releases** (`CarpStrategy.apk`, descarga directa) y la app avisa
cuando hay una nueva.

### Limitaciones (importante)

- **La app no mide la temperatura del agua**: lo recomendable es introducir tu propia medición
  (termómetro a 0,5 m y, si se puede, a 3–5 m). Si no la hay, se *estima* con una media móvil
  ponderada de la temperatura del aire de los últimos días, que puede desviarse varios grados y
  se calibra con tus mediciones.
- AEMET solo ofrece unas 24 h de observación horaria, así que la corrección de sesgo
  será aproximada. La estación más cercana puede estar a varios km y a otra altitud que el embalse.
- ECMWF IFS 0,25° tiene paso de 3 h: Open-Meteo rellena las horas intermedias (la lluvia aparece
  repetida en bloques de 3 h). Se asume que reparte el total entre las 3 horas (la suma se
  conserva); no se ha podido comprobar en la documentación oficial desde el entorno de desarrollo.
- La fase lunar usa el mes sinódico medio (error < 1 día): suficiente para un dato con peso 0.
- La rejilla de los modelos no coincide con el embalse: Open-Meteo devuelve el punto de rejilla
  usado (p. ej. 38,375 N, 6,688 O a 305 m), que se muestra en pantalla.
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

## Instalar en Android

APK de prueba y de producción desde **GitHub Actions → CI → Artifacts**. Configuración de la
clave de firma, instalación, actualización sin perder datos y copias de seguridad en
[`docs/INSTALACION.md`](docs/INSTALACION.md).

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
- [x] **Fase 1**: capa de datos (Open-Meteo multimodelo + AEMET + Room) y pantalla de datos en bruto
- [x] **Fase 2**: datos manuales e importación JSON, prioridad de fuentes elegible y serie combinada
- [x] **Fase 3**: parámetros derivados (presión, temperatura, agua estimada/medida, viento, lluvia, sesgo con AEMET, sol/luna y ventana legal)
- [x] **Fase 4**: motor de filtros/multiplicadores por niveles (reglas JSON con etiqueta de evidencia) y pantalla de estrategia
- [x] **Fase 5**: UI completa (diseño Material/Apple, claro/oscuro), GPS y gráficas
- [x] **Fase 6**: diario de sesiones (horas-caña, bolos, valoración previa sin sesgo, exportación)
- [x] **Fase 6.1**: preparar producción (APK firmado en la CI, foto completa de parámetros para la fase 7, copias de seguridad)
- [x] **Fase 7**: sesión guiada (plan A, avisos cada 30 min, alternativas y sustitutos de cebo, «Mi equipo»)
- [x] **Fase 7.1**: lluvia, tormenta y entrada de agua durante la sesión
- [x] **Fase 7.2**: cuenta de Google (datos en Drive) y aviso de versiones nuevas
- [ ] **Fase 8**: aprendizaje con datos propios cuando haya 20–40 sesiones (CONOCIMIENTO.md §10)

## CI

GitHub Actions (`.github/workflows/ci.yml`) compila, ejecuta los tests y pasa lint en cada push
a `main` y a `feature/**`, y en cada PR. El CI no usa ninguna clave real.
