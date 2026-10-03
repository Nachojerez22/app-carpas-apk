# CarpStrategy — especificación de diseño

Fuente de verdad visual para implementar la UI en Jetpack Compose + Material 3.
Los tokens ya están en código en `ui/theme/` y los componentes base en `ui/components/Badges.kt`.

## 1. Temas: 4 variantes

| Estilo | Modo | ColorScheme | Uso |
|---|---|---|---|
| **Material** (por defecto) | claro | `MaterialLightColors` | principal |
| Material | oscuro | `MaterialDarkColors` | principal |
| Apple | claro | `AppleLightColors` | alternativo |
| Apple | oscuro | `AppleDarkColors` | alternativo |

- `ThemePrefs(style, mode = System|Light|Dark, dynamicColor)` se guarda en DataStore y se elige en **Lugar → Apariencia**.
- Color dinámico (Android 12+) solo aplica al estilo Material, y es opcional.
- `CarpColors` (evidencia, avisos, bandas de gráfica, horas nocturnas) es **fijo**: nunca dinámico y es igual en ambos estilos.
- Diferencias de **estilo Apple** (consulta `CarpTheme.style`):
  - `groupedLists`: filas en bloques redondeados de 14 dp, divisor de 0,5 dp con sangría y cabecera de sección en MAYÚSCULAS de 13 sp.
  - `largeTitle`: título de 36 sp, sobre él una línea pequeña en mayúsculas (fecha · lugar).
  - `translucentNavBar`: barra inferior `surfaceContainer` semitransparente con desenfoque; icono activo en `primary`, sin píldora.
  - Interruptores verdes tipo iOS (51×31), control segmentado gris con pastilla blanca.
  - Tarjeta del agua sólida `tertiaryContainer`, número de 84 sp en peso ligero.
- Estilo Material: tarjetas `surface` con borde `outlineVariant` de 1 dp sin sombra, NavigationBar M3 con indicador `primaryContainer` y FAB extendido.

## 2. Tipografía
Atkinson Hyperlegible Next (empaquetada en `res/font`, debe funcionar offline). Cifras tabulares siempre. Escala en `Type.kt` (cuerpo 17 sp, dato clave 64 sp).

## 3. Espaciado y forma
Margen de pantalla 16 dp · espacios 4/8/12/16/24/32 · táctil mínimo 48 dp.
Radios Material: 6 (insignia) · 12 (chip/campo) · 16 (tarjeta) · 20 (sección) · 28 (botón).

## 4. Componentes reutilizables
| Componente | Reglas |
|---|---|
| `EvidenceBadge(evidence)` | Color + forma: Fuerte ●, Moderada ◐, Sin evidencia/peso 0 ⊘, Hipótesis local ◆, Variabilidad ∿. Siempre visible junto a cada recomendación y regla. |
| `SourceBadge(source)` | M contorno arcilla · A relleno agua · P discontinuo gris. 20 dp. Junto a cada dato meteorológico. |
| `WaterTempCard(measured)` | **Medida**: fondo `tertiaryContainer`, chip «MEDIDA» con ✓, cifra exacta. **Estimada**: borde discontinuo `tertiary`, «≈ 18 °C ± 1,5», chip «ESTIMADA», tendencia en línea discontinua. |
| `DataCard(label, value, unit, source, footnote, warning?)` | Rejilla 2 columnas. Con `warning` (p. ej. discrepancia de viento): borde 2 dp `warnStrong` + línea «Modelos: 8–22 km/h». |
| `WarningChip(type)` | Stale y ModelDivergence → `warnContainer`; Unverified → `secondaryContainer`. Icono + texto, alto ≥ 36 dp. |
| `TrendChart` | 5 días observados (línea sólida 3 dp) + hoy (línea vertical discontinua y punto) + 3 días de previsión (media discontinua sobre fondo `surfaceVariant`) + banda min–max de los 3 modelos (`divergenceBand`). Etiquetas: 28/9 · 30 · 2/10 · **Hoy** · 5. |
| `LegalWindowBar` | Barra de 24 h; las horas no legales van rayadas (`nightBase`/`nightStripe`), la ventana legal en `primary` y «ahora» como marca vertical. Muestra amanecer y anochecer. |
| `FilterChainStep` | Número en círculo + título + píldora de estado (Superado / Limita / Suma / Neutro). El nivel que limita lleva fondo `secondaryContainer`. |
| `DemandMeter` | 5 barras crecientes (Muy baja → Muy alta), sin gramos. |
| `SourcePriorityRow` | Posición, SourceBadge, nombre/descripción, subir/bajar (o arrastrar en Apple), Switch. |

## 5. Pantallas (navegación inferior: Hoy · Estrategia · Datos · Diario · Lugar)
1. **Hoy**: barra de antigüedad de los datos + Actualizar, chips de aviso, WaterTempCard, 4 DataCard (aire, viento con persistencia, lluvia 24/72 h, embalse % + hm³ + variación semanal), LegalWindowBar, «Se muestran, pero no cuentan» (luna y presión con insignia peso 0), gráfica de presión, gráfica de temperatura (aire y agua) y prioridad de fuentes.
2. **Estrategia**: tarjeta de resultado («Moderadamente favorables», escala de 5 y chip «Limita el nivel 2 · Temperatura», más el descargo «No indica dónde están los peces»), cadena de filtros 0→4, demanda alimentaria, Cuándo (franjas solo dentro de la ventana legal), Dónde / Cebado / Presentación / Qué evitar (en `errorContainer`) y reglas activadas (código, efecto, porqué e insignia).
3. **Datos**: segmentado Todos/Por hora/Por día, aviso de registros sin verificar, lista agrupada por día (fuente M, origen Manual/Importado/Sin verificar; este último con borde discontinuo arcilla) y FAB «Nuevo registro». Sub-pantallas: **Alta/edición** (todo opcional: fecha, hora, por hora/día, agua sup./fondo, turbidez 1–5, aire, presión, viento con dirección de 8 rumbos y velocidad, lluvia, nivel en hm³/%/cota, notas, eliminar) e **Importar JSON** (pegar/archivo/copiar ejemplo, editor monoespaciado, vista previa por registro con errores por campo, «Importar N válidos»).
4. **Diario**: resumen del mes, tarjetas de sesión (fecha, horas, zona, picadas/capturas/pérdidas, chip de resultado o **Bolo** explícito, «La app estimó: …»). **Ficha**: horas + chip de horario legal, resultado con Bolo Sí/No y lista de capturas, puesto por zonas sobre mapa esquemático, equipo (cañas, horas-caña calculadas, cebo, montaje), copia fija de la valoración previa con «¿Se cumplió?» y notas.
5. **Lugar**: ubicación (GPS / Pin / Coordenadas, mapa, lat/lon, usar mi ubicación), **Apariencia** (estilo Material/Apple, tema Sistema/Claro/Oscuro, color dinámico), fuentes (Open-Meteo CC BY 4.0, AEMET con su atribución, tus datos), leyenda de evidencias, limitaciones y normativa con «Última revisión».

## 6. Estados (en todas las pantallas con datos)
- **Vacío**: ilustración simple + «Aún no hay datos para este lugar» + Fijar ubicación / Añadir medición.
- **Cargando**: LinearProgressIndicator arriba + esqueletos `surfaceContainerHigh`.
- **Error**: tarjeta `errorContainer` con causa + Reintentar; mostrar lo disponible (manual/AEMET), «—» en lo que falta.
- **Sin conexión**: banda `warnContainer` «Sin conexión · datos de hace 14 h», tarjetas con borde discontinuo y chip de antigüedad, aviso en Estrategia «valoración con datos antiguos».

## 7. Reglas no negociables
- Nunca prometer localizar peces; hablar de «condiciones más o menos favorables según tus datos».
- **Horario legal**: [amanecer − 1 h, anochecer + 1 h]. El ViewModel recorta cualquier franja recomendada a esa ventana; la UI nunca muestra horas nocturnas como recomendables.
- Toda recomendación lleva `EvidenceBadge`; todo dato meteorológico lleva `SourceBadge`.
- La temperatura del agua medida y la estimada deben distinguirse siempre (borde, «≈», chip).
- Contraste: texto ≥ 4,5:1 (los tokens ya lo cumplen); no comunicar nada solo con color.
- Todo el texto en español.

## 8. Referencias visuales
- `docs/design/referencias/*.dc.html`: fuente HTML de cada maqueta (valores exactos de márgenes, tamaños y textos). Prefijo `A` = estilo Apple.
- `docs/design/capturas/*.png`: exporta las mesas de trabajo desde el lienzo (Compartir → Exportar → PNG) y ponlas aquí.
