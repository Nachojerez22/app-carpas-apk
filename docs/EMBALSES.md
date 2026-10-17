# Embalses: catálogo y fichas

## Catálogo (`app/src/main/assets/embalses.json`)

Formato `carpstrategy-embalses`, versión 1. Se genera con `tools/` (ver tools/README.md) desde el
«Listado completo de cotos de pesca de Extremadura» de la Junta de Extremadura. Brovales se añade
a mano (no es coto).

```json
{
  "formato": "carpstrategy-embalses",
  "version": 1,
  "fuente": "…",
  "revisado": "2026-10-17",
  "embalses": [
    {
      "id": "burguillos-del-cerro-embalse-de-burguillos",
      "nombre": "Embalse de Burguillos",
      "municipio": "Burguillos Del Cerro",
      "coto": "Charco El Toro",
      "lat": 38.4044,
      "lon": -6.60302,
      "permiso_telematico": true,
      "plazas_asociados": 0,
      "plazas_resto": 0,
      "regimen": "COTO",
      "abastecimiento": false
    }
  ]
}
```

- `regimen`: `COTO` o `GENERAL` (aguas libres).
- `abastecimiento`: true si el **nombre oficial** lo dice («Abto.», «Abastecimiento»). ⚖ En ese
  caso, mientras el usuario no diga otra cosa, la app no recomienda cebar (CONOCIMIENTO.md §7).
- `plazas_*`: plazas del listado (0 = sin límite indicado).

## Embalses propios (ajuste `reservoirs`)

Una lista con el mismo formato de cada entrada más `"propio": true`. El id es `propio-<8 caracteres>`.
Se sincroniza con la cuenta y va en la copia de seguridad de la base de datos.

## Fichas (ajuste `reservoir_sheets`)

```json
[
  {
    "embalse": "burguillos-del-cerro-embalse-de-burguillos",
    "especies": [
      {"especie": "CARP", "presente": true, "confirmada": true},
      {"especie": "BARBEL", "presente": true, "confirmada": false}
    ],
    "se_puede_cebar": null,
    "abastecimiento_ia": true,
    "resumen": "…",
    "notas": [{"texto": "…", "evidencia": "PURPLE"}],
    "ia": "2026-10-17T09:00:00Z",
    "actualizada": "2026-10-17T09:05:00Z"
  }
]
```

- `especie`: `CARP`, `BARBEL`, `NASE`, `TENCH`, `CRUCIAN`, `BULLHEAD`, `CATFISH`, `BLACK_BASS`, `PIKE`,
  `PIKEPERCH`, `SUNFISH`, `BLEAK` o `CRAYFISH`.
  - `confirmada`: false si la propuso la IA y el usuario no la ha revisado.
  - Una especie que no figura en la ficha está «sin saber».
- `se_puede_cebar`: lo que dice el usuario (`true`, `false` o `null` sin saber). `abastecimiento_ia`
  es lo que dijo la IA (sin verificar).
- La IA prepara la ficha con CONOCIMIENTO.md §5. La respuesta se valida entera, como en el resto del
  asistente: sin promesas, sin porcentajes, sin gramos, sin noche y con evidencia. Si no vale, la
  ficha no cambia.
- Lo que el usuario confirmó nunca lo cambia la IA.
- Qué cambia en la estrategia por cada especie: CONOCIMIENTO.md §5.11.
