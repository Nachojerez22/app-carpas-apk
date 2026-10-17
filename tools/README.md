# tools/

Scripts para regenerar `app/src/main/assets/embalses.json` (formato `carpstrategy-embalses` v1,
docs/EMBALSES.md) a partir del PDF oficial «Listado completo de cotos de pesca de Extremadura»
(pescayrios.juntaextremadura.es). No forman parte de la app ni de la CI.

Requisitos: `pdftotext` (poppler-utils) y Python 3 con `pypdf`.

```bash
pdftotext -bbox LISTADO_completo_COTOS_WEB.pdf cotos.html     # palabras con su posición
python3 tools/enlaces_pdf.py LISTADO_completo_COTOS_WEB.pdf enlaces.json   # enlaces «VER» (IGN)
python3 tools/cotos_pdf_a_json.py cotos.html enlaces.json cotos.json        # filas del listado
python3 tools/catalogo_embalses.py cotos.json app/src/main/assets/embalses.json
```

- Las coordenadas salen de los enlaces del visor del IGN (UTM ETRS89). El huso se deduce del
  valor de X (29 si X > 500 000, si no 30), porque algunos enlaces traen el código EPSG cortado.
- Se descartan las filas sin enlace válido o fuera de Extremadura.
- Brovales se añade a mano (no es coto).
- Si cambia el PDF, revisa el número de filas y algún embalse conocido (p. ej. Burguillos) antes
  de subir el JSON. El test `ReservoirJsonTest` carga el catálogo real.
