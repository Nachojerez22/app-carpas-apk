# Formato `carpstrategy-diario` (versión 1)

Copia de seguridad del diario de sesiones que genera **Diario → Exportar diario (JSON)**. Las
sesiones solo se guardan en el móvil (la app no usa la copia en la nube de Android), así que
conviene exportar de vez en cuando. Es JSON legible en UTF-8.

```json
{
  "formato": "carpstrategy-diario",
  "version": 1,
  "exportado": "2026-10-03T10:00:00Z",
  "sesiones": [
    {
      "inicio": "2026-09-27T05:20:00Z",
      "fin": "2026-09-27T10:40:00Z",
      "lat": 38.35,
      "lon": -6.7,
      "zona": "NORTH",
      "zona_detalle": "carrizal norte",
      "profundidad_m": 2.5,
      "canas": 3,
      "horas_cana": 16.0,
      "cebo": "Boilie de pescado 20 mm",
      "montaje": "Pelo · plomo en línea",
      "cebado_kg": 1.5,
      "otros_pescadores": 1,
      "picadas": 4,
      "perdidas": 1,
      "capturas": [{ "hora": "2026-09-27T06:05:00Z", "peso_kg": 8.4, "cana": 2, "especie": "Carpa común" }],
      "bolo": false,
      "valoracion_previa": {
        "calculada": "2026-09-26T19:40:00Z", "lat": 38.35, "lon": -6.7, "bloqueada": false,
        "favorabilidad": 0.72, "tramo": "FAVORABLE", "nivel_limitante": "PHYSICAL", "demanda": "MEDIUM"
      },
      "contexto": { "temp_agua_c": 18.1, "agua_medida": true, "luna_iluminacion": 0.3 },
      "se_cumplio": "PARTLY",
      "notas": "Burbujeo en el borde del carrizo",
      "creada": "2026-09-27T11:00:00Z"
    }
  ]
}
```

## Campos de cada sesión

| Campo | Tipo | Notas |
|---|---|---|
| `inicio`, `fin` | ISO-8601 UTC | `fin` ausente = sesión en curso |
| `lat`, `lon` | grados decimales | Lugar de la app al crear la sesión |
| `zona` | `NORTH`, `EAST`, `SOUTH`, `WEST` | Zona codificada (CONOCIMIENTO.md §9); opcional |
| `zona_detalle` | texto | Lo escribe el usuario |
| `canas` | entero | ⚖ La normativa permite como máximo 3 |
| `horas_cana` | número | Esfuerzo: tecleado (`horas_cana_tecleadas`) o cañas × duración. Solo informativo al importar |
| `picadas`, `perdidas` | entero | |
| `capturas` | lista | `hora` (UTC), `peso_kg`, `cana`, `especie`; todo opcional |
| `bolo` | booleano | Respuesta explícita; una sesión sin capturas es un dato |
| `valoracion_previa` | objeto | Copia fija de lo que dijo la app **antes** de la sesión. `tramo`: `VERY_UNFAVORABLE` … `VERY_FAVORABLE`; `nivel_limitante`: `HABITAT`, `TEMPERATURE`, `PHYSICAL`, `CATCHABILITY`. Desde 0.7.0 también `parametros`, `niveles` (valor 0–1 de cada nivel), `reglas_activas` (id → factor aplicado) y `huella_reglas` (versión de rules.json) |
| `contexto` | objeto | Rellenado al guardar: agua (y si era medida), aire 24 h, viento, lluvia 72 h, nivel %, horario legal, y con peso 0 la luna y la presión. Desde 0.7.0, `parametros` con todos los parámetros de las reglas al inicio de la sesión |
| `se_cumplio` | `YES`, `PARTLY`, `NO` | Opcional |
| `guiado` | objeto | Desde 0.8.0, solo en sesiones guiadas: tramos, avisos y decisiones (ver abajo) |

`parametros` es un objeto `{ "numeros": {…}, "booleanos": {…}, "textos": {…} }` con las claves
de `rules.json` (docs/REGLAS.md), p. ej. `{"numeros": {"temp_agua_c": 18.1}, "textos": {"estacion": "otono"}}`.

### Sesión guiada (`guiado`)

```json
"guiado": {
  "canas": [
    { "id": 1, "nombre": "fija",
      "tramos": [
        { "inicio": "…", "fin": "…", "paso": "INITIAL", "cebo": "BOILIE", "cebo_nombre": "Boilie fresa 20",
          "columna": "BOTTOM", "montaje": "Pelo 25 lb", "forzado": false,
          "avisos": [ { "hora": "…", "senales": "INDIRECT", "actividad": "CATCH", "especie": "BARBEL",
                        "estado_cebo": "NIBBLED", "no_funciona": false, "cambio_usuario": "BAIT", "recebado": "LOW" } ] }
      ],
      "propuestas": [
        { "propuesta": { "paso": "SELECTIVE", "situacion": "OTHER_FISH", "cebo": "TIGERNUT",
                         "forzada": false, "evidencia": "YELLOW", "creada": "…" },
          "decision": "REJECTED", "motivo": "NO_BAIT", "comentario": "no la llevo", "decidida": "…" }
      ] }
  ],
  "cebado_inicial": "HIGH",
  "condiciones": [ { "hora": "…", "tipo": "STORM", "activa": true }, { "hora": "…", "tipo": "STORM", "activa": false } ],
  "cambios_viento": ["…"],
  "avisos_mostrados": ["…"],
  "puesto": { "id": 3, "nombre": "Punta del cauce", "zona": "WEST", "estructura": "OLD_CHANNEL",
              "profundidad_m": 4.5, "distancia_m": 60, "orientacion_grados": 270, "notas": "fondo duro" },
  "tiempo": [
    { "hora": "…", "aire_c": 21.5, "viento_kmh": 12, "viento_dir_grados": 250, "rachas_kmh": 30,
      "nubosidad_pct": 80, "precipitacion_mm": 0.4, "codigo_tiempo": 95, "presion_hpa": 1012,
      "agua_c": 22.1, "agua_medida": false, "ocaso": "…", "fin_legal": "…",
      "llueve_usuario": true, "tormenta_usuario": false }
  ]
}
```

- Una entrada en `canas` por caña, con su nombre (puede ir vacío: «Caña N»).
- Un **tramo** es el tiempo pescado en una caña con una misma configuración; al aceptar una
  propuesta empieza otro. `paso`: `INITIAL`, `PRESENTATION`, `RIG`, `COLUMN`, `DISTANCE`, `ZONE`,
  `ANTI_CRAB`, `SELECTIVE`.
- `senales`: `NONE`, `INDIRECT`, `DIRECT` · `actividad`: `NOTHING`, `TOUCHES`, `MISSED` (picada
  fallada), `CATCH` · `estado_cebo`: `NOT_CHECKED`, `INTACT`, `NIBBLED`, `GONE` ·
  `cambio_usuario`: `BAIT`, `RIG`, `COLUMN`, `DISTANCE`, `ZONE`.
- `especie` (solo con `CATCH`; ausente = carpa): `BARBEL`, `NASE` (boga), `BLACK_BASS`, `SMALL`
  (pequeño sin identificar) · `recebado` y `cebado_inicial`: `LOW`, `NORMAL`, `HIGH`.
- `cebo`: `BOILIE`, `BOILIE_HARD`, `PELLET`, `MAIZE`, `HEMP`, `TIGERNUT`, `PASTE`, `BREAD`, `POPUP`,
  `PVA`, `WORM`, `OTHER` · `columna`: `BOTTOM`, `POPUP`, `ZIG`, `SURFACE`.
- `decision`: `PENDING`, `ACCEPTED`, `REJECTED` · `motivo`: `NO_BAIT`, `NOT_CONVINCED`,
  `ALREADY_TRIED`, `CONDITIONS_DIFFER`, `OTHER` · `forzada`: pedida con dos «No funciona» en 30 min.
- `avisos_mostrados`: avisos que lanzó la app, contestados o no.
- Las picadas falladas y las carpas anotadas en la sesión guiada también suman en `picadas` y
  `capturas` de la sesión (la captura lleva la caña). Las otras especies solo quedan en `guiado`.
- `cambios_viento`: momentos en que el usuario anotó que el viento cambió.
- `condiciones`: lluvia, tormenta y agua turbia que empiezan (`activa: true`) o terminan
  (`false`). `tipo`: `LIGHT_RAIN`, `HEAVY_RAIN`, `STORM`, `MUDDY_INFLOW`. Desde 0.8.1.
- `paso` también puede ser `INFLOW` (boca de la recula o del arroyo, con agua turbia entrando).
- `puesto` (desde 0.9.0): copia del puesto de **Mis puestos** elegido al empezar. `zona`: `NORTH`,
  `EAST`, `SOUTH`, `WEST` · `estructura`: `SHALLOW_EDGE`, `FIRST_DROP`, `OLD_CHANNEL`,
  `INLET_BAY`, `POINT`, `FLAT`, `OTHER` · `orientacion_grados`: hacia dónde mira la orilla (0 = N).
- `tiempo` (desde 0.9.0): previsión de la hora en curso al empezar y en cada aviso. `codigo_tiempo`
  es el código WMO de Open-Meteo (95–99 = tormenta). `nubosidad_pct` y `presion_hpa` tienen peso 0.
  `llueve_usuario` y `tormenta_usuario`: respuesta a «¿Llueve?» / «¿Hay tormenta?» en ese aviso
  (ausente = sin contestar). Mis puestos se guardan en el ajuste `spots` con el mismo formato que
  `puesto`.

Los campos sin valor se omiten. Las claves desconocidas se ignoran al leer. **Diario →
Restaurar una copia del diario** lee este formato y omite las sesiones que ya existen (misma
hora de inicio).
