import json, sys, re, unicodedata
rows = json.load(open(sys.argv[1]))
def slug(s):
    s = unicodedata.normalize('NFKD', s).encode('ascii', 'ignore').decode().lower()
    return re.sub(r'[^a-z0-9]+', '-', s).strip('-')
def num(v):
    return int(v) if v and v.isdigit() else None
out = [{
    "id": "brovales", "nombre": "Embalse de Brovales", "municipio": "Jerez de los Caballeros", "coto": None,
    "lat": 38.35, "lon": -6.70, "permiso_telematico": False, "plazas_asociados": None, "plazas_resto": None,
    "regimen": "GENERAL", "abastecimiento": False,
}]
seen = {"brovales"}
for r in rows:
    if not r["masa"] or r["lat"] is None or not (37.9 < r["lat"] < 40.6 and -7.7 < r["lon"] < -4.5):
        continue
    name = re.sub(r"\s+", " ", r["masa"]).strip().rstrip(".")
    mun = re.sub(r"\s+", " ", r["municipio"] or "").strip()
    base = slug(f"{mun}-{name}")
    i, rid = 2, base
    while rid in seen:
        rid = f"{base}-{i}"; i += 1
    seen.add(rid)
    out.append({
        "id": rid, "nombre": name, "municipio": mun, "coto": (r["coto"] or "").strip().title() or None,
        "lat": r["lat"], "lon": r["lon"], "permiso_telematico": r["modelo50"],
        "plazas_asociados": num(r["asociados"]), "plazas_resto": num(r["resto"]),
        "regimen": "COTO", "abastecimiento": bool(re.search(r"\bAbto\b|Abastecimiento", name, re.I)),
    })
doc = {
    "formato": "carpstrategy-embalses", "version": 1,
    "fuente": "Listado completo de cotos de pesca de Extremadura (Junta de Extremadura, pescayrios.juntaextremadura.es), coordenadas de sus enlaces del IGN; Brovales añadido a mano (no es coto).",
    "revisado": "2026-10-17",
    "embalses": out,
}
json.dump(doc, open(sys.argv[2], "w"), ensure_ascii=False, indent=1)
print(len(out), sum(e["abastecimiento"] for e in out))
print([e for e in out if "burguillos" in e["id"]])
