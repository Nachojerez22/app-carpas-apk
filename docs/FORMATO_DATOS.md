# Formato de importación `carpstrategy-datos` (versión 1)

Archivo JSON (UTF-8) para cargar en la app datos medidos u obtenidos por ti: temperatura del
agua, nivel del embalse, observaciones de otra estación, etc. Ejemplo completo:
[`ejemplos/datos-ejemplo.json`](ejemplos/datos-ejemplo.json) (también se puede copiar desde la
app: *Condiciones → Datos manuales → Copiar ejemplo*).

> **Importante:** si generas el archivo con un asistente (Claude, ChatGPT…), pídele que use
> **solo** datos que tú le des o que copie de una fuente real, y que la indique en `fuente`. Un
> asistente puede inventar números verosímiles. La app marca todo lo importado como
> *"importado, no verificado"* y muestra siempre su fuente.

## Estructura

```json
{
  "formato": "carpstrategy-datos",
  "version": 1,
  "ubicacion": { "nombre": "Embalse de Brovales", "lat": 38.35, "lon": -6.70 },
  "zona_horaria": "Europe/Madrid",
  "registros": [ { … }, { … } ]
}
```

| Campo | Obligatorio | Descripción |
|---|---|---|
| `formato` | sí | Siempre `"carpstrategy-datos"` |
| `version` | sí | Siempre `1` |
| `ubicacion` | no | `lat`/`lon` en grados decimales. Si falta, se usa la ubicación de la app. Si está a más de 10 km, la app avisa |
| `zona_horaria` | no | Zona IANA de las horas sin zona. Por defecto `Europe/Madrid` |
| `registros` | sí | Lista de 1 a 5000 registros |

## Registros

Cada registro lleva **`hora` o `fecha`** (no ambas), una **`fuente`** y al menos un valor.

| Campo | Descripción |
|---|---|
| `hora` | Momento de la medida: `"2026-10-03T08:00"` (hora local de `zona_horaria`), también con segundos, con espacio en vez de `T`, o con zona (`"2026-10-03T06:00Z"`, `"…+02:00"`). Se redondea a la hora en punto más cercana al combinar con las otras fuentes |
| `fecha` | Día completo: `"2026-09-28"`. Solo admite los campos marcados como *día* |
| `fuente` | Obligatoria. De dónde sale el dato: "termómetro propio", "Boletín Hidrológico MITECO", "estación X"… |
| `notas` | Opcional, texto libre |

No se admiten horas futuras (margen de 1 h) ni fechas posteriores a hoy: son datos observados,
no previsiones.

### Valores

Los números pueden ir como número (`14.2`) o como texto con coma (`"14,2"`). `null` = sin dato.

| Clave | Unidad | Rango admitido | Admite |
|---|---|---|---|
| `temp_aire_c` | °C | −30 a 55 | hora |
| `presion_hpa` | hPa (nivel del mar) | 900 a 1100 | hora |
| `viento_kmh` | km/h | 0 a 250 | hora |
| `viento_dir_grados` | grados de procedencia (0 = N, 90 = E) | 0 a 360 | hora |
| `racha_kmh` | km/h | 0 a 300 | hora |
| `nubosidad_pct` | % | 0 a 100 | hora |
| `lluvia_mm` | mm en la hora anterior | 0 a 500 | hora |
| `humedad_pct` | % | 0 a 100 | hora |
| `temp_agua_superficie_c` | °C (≈0,5 m) | −1 a 40 | hora |
| `temp_agua_fondo_c` | °C | −1 a 40 | hora (indica también `profundidad_fondo_m`) |
| `profundidad_fondo_m` | m | 0 a 100 | hora |
| `turbidez` | escala entera 1 (clara) a 5 (chocolate) | 1 a 5 | hora |
| `lluvia_dia_mm` | mm en el día | 0 a 1000 | día |
| `nivel_embalse_hm3` | hm³ | 0 a 100 | hora o día |
| `nivel_embalse_pct` | % de capacidad | 0 a 110 | hora o día |
| `cota_embalse_m` | m s. n. m. | 0 a 2000 | hora o día |

Los rangos solo detectan errores de tecleo (p. ej. `presion_hpa: 10184`). Una clave desconocida
no impide importar, pero la app avisa (suele ser una errata, como `temp_aire`).

## Validación al importar

La importación es **todo o nada**: si algún registro tiene errores, no se importa ninguno y la
app lista cada error con su número de registro y campo. Los avisos (clave desconocida, ubicación
lejana, temperatura de fondo sin profundidad, registro repetido) no impiden importar.

Si se reimporta un registro con la misma `hora`/`fecha` y la misma `fuente` en la misma
ubicación, **sustituye** al anterior (así se puede corregir un archivo y volver a cargarlo).

## Cómo se usan los datos

En *Condiciones* eliges la **prioridad de fuentes** (Manual, AEMET, Modelos: orden y cuáles
están activas). Para cada hora y variable se usa la primera fuente activa que tenga valor, y en
pantalla se indica de qué fuente sale cada valor. Si hay varios registros manuales en la misma
hora, gana el más reciente.
