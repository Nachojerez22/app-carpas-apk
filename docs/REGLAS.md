# Reglas de estrategia: `app/src/main/assets/rules.json`

Formato `carpstrategy-reglas` versión 1. Las reglas iniciales traducen
[`CONOCIMIENTO.md`](CONOCIMIENTO.md) §4.1, §5 y §5.8. Los umbrales son **iniciales**: se
ajustarán con tus datos (§10). Si cambias el archivo, la app lo valida al arrancar y, si hay
errores, los muestra en *Estrategia* y no usa ninguna regla.

## Cómo se evalúa (cadena de filtros, NO suma de puntos)

1. **Nivel 0 (legalidad):** si se cumple un `filtro_duro`, no se recomienda sesión.
2. **Niveles 1–4:** cada nivel vale el producto de los factores efectivos de sus reglas activas
   (sin reglas activas vale 1). Factor efectivo = `1 − peso × (1 − factor)`, con
   peso alto = 1, medio = 0,6, bajo = 0,3 y 0 = 0. Los factores son ≤ 1: un nivel solo limita.
3. **Valoración** = producto de los niveles 1–4 (0–100). Si un nivel inferior es bajo, los
   superiores no lo compensan. Se muestra el **nivel limitante** (el de menor valor).
4. Las reglas de peso 0 (rojo, exploratorias, avisos) aparecen en el "por qué" pero no modifican.
5. Sin temperatura del agua (medida o estimada) no hay valoración.
6. Las ventanas horarias se sugieren según la estación del agua y **siempre dentro del horario legal**.

Bandas: < 20 muy desfavorables · < 40 desfavorables · < 60 intermedias · < 80 favorables · resto
muy favorables. Demanda alimentaria (§5.1): < 10 °C muy baja · < 14 baja · < 20 media · < 24 alta
· ≤ 28 muy alta · > 28 incierta.

## Estructura

```json
{
  "formato": "carpstrategy-reglas",
  "version": 1,
  "revision_normativa": "2026-03-18",
  "reglas": [ { … } ]
}
```

| Campo | Obligatorio | Valores |
|---|---|---|
| `id` | sí | minúsculas, números y `_`; único |
| `nivel` | sí | `0`, `1`, `2`, `3`, `4` o `"exploratoria"` |
| `tipo` | sí | `filtro_duro` (solo nivel 0) · `aviso` · `multiplicador` · `pertenencia` · `consejo` · `registro` |
| `evidencia` | sí | `verde` 🟢 · `amarillo` 🟡 · `rojo` 🔴 · `morado` 🟣 · `azul` 🔵 · `normativa` ⚖ |
| `peso` | sí | `alto` · `medio` · `bajo` · `"0"`. **Rojo y exploratoria exigen `"0"`** (§0.2) |
| `descripcion` | sí | Texto que se muestra en el "por qué" |
| `fuente` | no | Referencia |
| `condiciones` | no | Objeto `{parametro: condición}`; todas deben cumplirse. Vacío = siempre |
| `factor` | si `multiplicador` | 0 a 1 |
| `pertenencia` | si `pertenencia` | `{"parametro": "temp_agua_c", "puntos": [[x, y], …]}`; x creciente, y en 0–1, interpolación lineal |
| `estrategia` | no | Lista de `{"campo", "texto", "evidencia"?}`; `campo` ∈ `donde`, `cuando`, `cebado`, `presentacion`, `evitar`, `notas`. La evidencia por defecto es la de la regla |

Condiciones: numéricas `{"min": 17, "max": 21}` (inclusivas, basta una), booleanas
`{"es": true}`, de texto `{"en": ["primavera", "otono"]}`. Si falta el dato de un parámetro, la
regla aparece en "Sin datos para evaluar".

Marcadores en los textos: `{viento_desde}`, `{viento_hacia}` (rumbo de 8 puntos).

## Parámetros disponibles

| Clave | Tipo | Origen |
|---|---|---|
| `temp_agua_c` | número | Medida (≤ 48 h) o estimada (fase 3) |
| `agua_medida` | sí/no | Si la anterior es medida |
| `tendencia_agua_3d_c` | número | Estimación ahora − hace 3 días |
| `estacion` | texto | `invierno`, `primavera`, `verano`, `otono` (por el agua) |
| `temp_aire_24h_c`, `tendencia_aire_c` | número | Media 24 h y diferencia con los 3 días previos |
| `dias_calor`, `dias_frio` | número | Rachas hasta ayer |
| `viento_24h_kmh`, `viento_persistencia_24h`, `racha_max_24h_kmh` | número | Viento 24 h (persistencia 0–1) |
| `horas_viento_incierto` | número | Horas de las próximas 24 con divergencia entre modelos |
| `lluvia_24h_mm`, `lluvia_72h_mm` | número | Acumulados |
| `escorrentia` | sí/no | Escorrentía probable (🟣) |
| `nivel_delta_7d_hm3`, `nivel_pct` | número | Nivel del embalse (datos manuales) |
| `presion_delta_24h_hpa`, `nubosidad_pct`, `luna_iluminacion` | número | Solo para reglas de peso 0 |
| `ventana_legal_disponible`, `en_horario_legal` | sí/no | Horario legal de hoy / ahora |
| `fin_de_semana` | sí/no | Sábado o domingo (más presión de pesca) |
| `mes` | número | 1–12 |
