# Estrategia de CarpStrategy: estado actual y plantilla para ampliarla

Generado el 2026-10-03 a partir de `app/src/main/assets/rules.json` (revisión de normativa 2026-03-18, 32 reglas) y de `docs/CONOCIMIENTO.md` §5. Es **lo que la app dice hoy**, tal cual.

**Cómo usarlo:** amplía la sección 4 (o añade filas en la plantilla de la sección 5) con lo que quieras que recomiende: qué cebo y cuánto, a qué distancia probar, si fondo, media agua o superficie. Pon a cada recomendación su **etiqueta de evidencia** y, si la hay, la fuente. Devuélvemelo y lo convierto en reglas de la app y en cambios de `CONOCIMIENTO.md`.

## 1. Cómo decide la app

- **No suma puntos.** Es una cadena de filtros por niveles: 0 legalidad → 1 hábitat → 2 temperatura → 3 modificadores físicos → 4 capturabilidad. Cada nivel vale de 0 a 1, se multiplican y el nivel más bajo es el que **limita** el resultado; un nivel alto no compensa uno bajo.
- Las reglas tienen un **tipo**: *filtro duro* (impide la sesión), *multiplicador* (resta en su nivel), *curva* (resta según un valor, p. ej. la temperatura del agua), *consejo* (aporta estrategia sin cambiar la valoración), *aviso* y *registro* (peso 0).
- Cada regla y cada texto llevan **etiqueta de evidencia**: 🟢 respaldado por estudios · 🟡 experiencia de pescadores / moderada · 🟣 hipótesis local (Brovales) · 🔵 variabilidad individual · 🔴 sin evidencia, peso 0 · ⚖ normativa.
- Las estaciones se deciden por la **temperatura del agua** (invierno < 10 °C, primavera 10→20 °C subiendo, verano > 22 °C, otoño 20→10 °C bajando), no por el calendario.
- Solo se recomiendan horas **legales** (1 h antes del orto a 1 h después del ocaso) y como máximo **3 cañas**.

## 2. Qué muestra hoy la pantalla Estrategia

1. Resultado en 5 tramos (muy desfavorables → muy favorables) y el nivel que limita.
2. Cadena de filtros 0 → 4 con el estado de cada nivel.
3. **Demanda alimentaria** en 5 barras (muy baja → muy alta). 🟢 Se calcula con la temperatura del agua. **Nunca en gramos** (CONOCIMIENTO.md §5.8: no hay datos para fijar gramos por temperatura).
4. **Cuándo**: franjas sugeridas dentro del horario legal.
5. Consejos agrupados en **Dónde, Cebado, Presentación, Evitar y Notas**: son los textos de las reglas que se cumplen hoy.
6. Reglas activadas (código, efecto, por qué y evidencia) y las registradas con peso 0.

**No existen hoy** como apartado propio: distancia de pesca, profundidad o posición en la columna (fondo, media agua, superficie), tipo y tamaño de cebo, cantidad concreta ni frecuencia de recebado. Lo poco que hay está dentro de *Presentación* o *Dónde* (ver sección 3).

## 3. Lo que la app recomienda hoy, por apartado

### Dónde

| Cuándo se muestra (condiciones) | Texto | Evidencia | Regla |
|---|---|---|---|
| `nivel_delta_7d_hm3` ≤ -0.8 | Mover las cañas a la primera caída o cambio de pendiente donde antes había litoral. | 🟣 | `nivel_bajando_rapido` |
| `nivel_delta_7d_hm3` ≥ 0.3 | Cañas someras junto a la nueva orilla y la entrada de agua. | 🟡 | `nivel_subiendo` |
| `tendencia_agua_3d_c` ≥ 1.0 | Buscar someras que se calientan antes. | 🟢 | `temp_subiendo` |
| `tendencia_agua_3d_c` ≤ -1.5 | Volver a zonas estables y dejar que el agua se estabilice. | 🟡 | `temp_bajando` |
| `temp_agua_c` entre 17 y 21 y `estacion` ∈ primavera | Someros con vegetación, sin molestar las zonas de freza. | 🟡 | `desove_17_21` |
| `estacion` ∈ invierno | Zonas estables algo más profundas junto a caída o antiguo cauce, o someras protegidas del viento frío y con sol a mediodía. | 🟡 | `estacion_invierno` |
| `estacion` ∈ primavera | Someras con vegetación o pasto inundado y reculas que se calientan antes; la boca de la recula como paso. | 🟡 | `estacion_primavera` |
| `estacion` ∈ verano | Seguir el nivel: primera caída o cambio de pendiente donde antes había litoral; sombra, vegetación y entradas si llevan agua. | 🟣 | `estacion_verano` |
| `estacion` ∈ otono | Litoral recién inundado, reculas y entradas con escorrentía real; fondos duros si hay cangrejo (sin datos en Brovales). | 🟣 | `estacion_otono` |
| `escorrentia` = sí y `estacion` ∈ invierno | Zonas estables y protegidas. | 🟡 | `escorrentia_invierno` |
| `escorrentia` = sí y `estacion` ∈ primavera, verano, otono | Cola y borde de la pluma turbia, no el centro del barro. | 🟣 | `escorrentia_turbia` |
| `escorrentia` = sí y `estacion` ∈ verano | Una caña en la caída frente a la desembocadura. | 🟣 | `tormenta_verano_entrada_fresca` |
| `viento_24h_kmh` ≥ 10 y `viento_persistencia_24h` ≥ 0.7 y `tendencia_aire_c` ≥ 0 | Desempate: orilla a sotavento, hacia donde sopla el viento (sopla del {viento_desde}, empuja hacia el {viento_hacia}). | 🟡 | `viento_calido_sotavento` |
| `tendencia_aire_c` ≤ -3 | Zonas protegidas y soleadas. | 🟡 | `frente_frio` |
| `racha_max_24h_kmh` ≥ 35 | Probar la orilla batida con fondo blando (remueve alimento) o una recula protegida si no hay señales. | 🟣 | `viento_fuerte_oleaje` |
| `estacion` ∈ verano y `viento_24h_kmh` ≤ 8 y `temp_aire_24h_c` ≥ 24 | Sombra y vegetación; cebos a media agua si hay actividad. | 🟡 | `verano_calma_calor` |
| `fin_de_semana` = sí | Evitar el puesto evidente; zonas menos obvias. | 🟢 | `presion_pesca_fin_de_semana` |

### Cebado

| Cuándo se muestra (condiciones) | Texto | Evidencia | Regla |
|---|---|---|---|
| siempre | Cebado solo con productos no tóxicos. Nada de cangrejo rojo vivo ni clonk. En embalses de abastecimiento (probablemente Valuengo) no se puede cebar: confírmalo. | ⚖ | `cebado_y_cebos_normativa` |
| `nivel_delta_7d_hm3` ≥ 0.3 | Cebado pequeño: el litoral recién inundado ya aporta alimento. | 🟡 | `nivel_subiendo` |
| `tendencia_agua_3d_c` ≥ 1.0 | Subir el cebado de forma progresiva. | 🟢 | `temp_subiendo` |
| `tendencia_agua_3d_c` ≤ -1.5 | Reducir el cebado. | 🟡 | `temp_bajando` |
| `temp_agua_c` entre 17 y 21 y `estacion` ∈ primavera | No sobrecebar: pueden comer menos durante el desove. | 🟢 | `desove_17_21` |
| `estacion` ∈ invierno | Mínimo: un puñado o PVA, cebos pequeños y muy atractivos (tránsito ~6 %/h a 9 °C). | 🟢 | `estacion_invierno` |
| `estacion` ∈ primavera | Progresivo al subir la temperatura; entre 17 y 21 °C no sobrecebar. | 🟢 | `estacion_primavera` |
| `estacion` ∈ verano | Abundante pero repartido, repuesto en las ventanas de actividad. | 🟢 | `estacion_verano` |
| `estacion` ∈ otono | Bajarlo a medida que baja la temperatura; si cae de golpe (21 → 17 °C), reducir más. | 🟢 | `estacion_otono` |
| `escorrentia` = sí y `estacion` ∈ invierno | Mínimo. | 🟡 | `escorrentia_invierno` |
| `escorrentia` = sí y `estacion` ∈ primavera, verano, otono | Pequeño: el agua ya trae comida. | 🟣 | `escorrentia_turbia` |
| `tendencia_aire_c` ≤ -3 | Bajar el cebado. | 🟡 | `frente_frio` |

### Presentación

| Cuándo se muestra (condiciones) | Texto | Evidencia | Regla |
|---|---|---|---|
| siempre | Máximo 3 cañas en 10 m de orilla (3 señuelos por caña). | ⚖ | `max_canas` |
| `estacion` ∈ invierno | 1–2 cañas fijas en la misma zona, con paciencia. | 🟡 | `estacion_invierno` |
| `estacion` ∈ primavera | Cañas repartidas entre orilla somera y boca de recula; mover la que no dé señales tras varias horas. | 🟣 | `estacion_primavera` |
| `estacion` ∈ verano | Con carpas en superficie o media agua: flotantes o zig rig; si no, fondo junto a la caída. Anota la profundidad. | 🟡 | `estacion_verano` |
| `estacion` ∈ otono | Una caña en el litoral nuevo y otra en la caída próxima; registrar cuál recibe picadas. | 🟣 | `estacion_otono` |
| `fin_de_semana` = sí | Cebado discreto y presentaciones distintas. | 🟢 | `presion_pesca_fin_de_semana` |
| siempre | Si capturaste en este puesto hace poco, rota puesto y montaje; usa bajos menos visibles. | 🟢 | `presion_pesca_puesto` |

### Evitar

| Cuándo se muestra (condiciones) | Texto | Evidencia | Regla |
|---|---|---|---|
| `nivel_delta_7d_hm3` ≤ -0.8 | Cebar zonas someras que se están quedando sin agua. | 🟣 | `nivel_bajando_rapido` |
| `temp_agua_c` ≥ 28 y `viento_24h_kmh` ≤ 8 | Descartar el fondo solo por "poco oxígeno". | 🟢 | `calor_extremo_oxigeno` |
| `estacion` ∈ invierno | Cebaderos grandes, cambiar de puesto cada hora, entradas de agua fría tras lluvia fuerte. | 🟡 | `estacion_invierno` |
| `estacion` ∈ primavera | Sobrecebar tras una bajada brusca de temperatura; zonas que se enfrían con viento frío. | 🟡 | `estacion_primavera` |
| `estacion` ∈ verano | Descartar el fondo solo por "poco oxígeno" (la carpa lo tolera); cebar zonas someras que se secan. | 🟢 | `estacion_verano` |
| `estacion` ∈ otono | Dar por hecho que el otoño siempre es buena época; ignorar la tendencia térmica. | 🟡 | `estacion_otono` |
| `escorrentia` = sí y `estacion` ∈ invierno | La cola y la entrada de agua fría. | 🟡 | `escorrentia_invierno` |

### Notas

| Cuándo se muestra (condiciones) | Texto | Evidencia | Regla |
|---|---|---|---|
| `agua_medida` = no | Mide el agua a 0,5 m (y a 3–5 m si puedes): es el dato que más mejora la recomendación. | 🟡 | `agua_estimada` |
| `temp_agua_c` ≥ 28 y `viento_24h_kmh` ≤ 8 | Mide la temperatura y anota la profundidad de cada caña. | 🟡 | `calor_extremo_oxigeno` |
| `lluvia_72h_mm` ≥ 1 y `escorrentia` = no | No cambiar el plan por la lluvia; anota la lluvia y cómo estaba el suelo. | 🟡 | `lluvia_sin_escorrentia` |
| `horas_viento_incierto` ≥ 6 | Viento previsto incierto: decide la orilla con el viento que encuentres al llegar. | 🟡 | `viento_incierto` |
| siempre | Si ves burbujas, saltos o nubes de sedimento, mueve las cañas hacia esa zona y anótalo. | 🟡 | `senales_de_campo` |
| siempre | Tras horas sin señales, cambia de zona por función (calentamiento, paso, entrada) y registra el bolo con sus horas-caña. | 🟡 | `senales_de_campo` |

### Cuándo

| Cuándo se muestra (condiciones) | Texto | Evidencia | Regla |
|---|---|---|---|
| `ventana_legal_disponible` = no | No se puede calcular el horario legal de hoy: no se recomienda ninguna sesión. | ⚖ | `horario_legal` |
| `en_horario_legal` = no | Ahora es fuera del horario legal: la recomendación es para las ventanas legales de hoy. | ⚖ | `fuera_de_horario_ahora` |
| `temp_agua_c` ≥ 28 y `viento_24h_kmh` ≤ 8 | Sesión corta al amanecer legal. | 🟡 | `calor_extremo_oxigeno` |
| `estacion` ∈ invierno | Franja central del día legal, tras 2–3 días estables y con horas de sol. | 🟣 | `estacion_invierno` |
| `estacion` ∈ primavera | Tarde y anochecer legal; también amanecer si la temperatura asciende. | 🟡 | `estacion_primavera` |
| `estacion` ∈ verano | Amanecer y anochecer legales; evitar el mediodía con calor y calma. | 🟡 | `estacion_verano` |
| `estacion` ∈ otono | Horas centrales templadas y atardecer legal; tras un frente frío esperar 1–2 días. | 🟡 | `estacion_otono` |
| `tendencia_aire_c` ≤ -3 | Si puedes, esperar 1–2 días a que se estabilice. | 🟡 | `frente_frio` |
| `estacion` ∈ verano y `viento_24h_kmh` ≤ 8 y `temp_aire_24h_c` ≥ 24 | Amanecer y anochecer legales; no insistir a mediodía. | 🟡 | `verano_calma_calor` |
| `ventana_legal_disponible` = sí | Mira las ventanas horarias sugeridas: siempre dentro del horario legal. | 🟡 | `hora_amanecer_anochecer` |

Además de estos textos, la app propone franjas horarias concretas por estación del agua (amanecer, mediodía, tarde-anochecer), siempre recortadas al horario legal.

## 4. Huecos a completar (lo que pides)

Rellena lo que sepas. Para cada recomendación indica **en qué condiciones** se aplica (usa los parámetros de la sección 6: temperatura del agua, estación, tendencia, viento, lluvia, nivel…) y su **evidencia**.

### 4.1 Cebado

Hoy solo se dice *cuánto* en términos relativos (mínimo, progresivo, abundante, reducir) y la demanda en 5 niveles. Falta:

- **Tipo de cebo** (boilies, pellets, partículas como maíz o cañamón, masillas, PVA…) según temperatura/estación.
- **Tamaño** (p. ej. boilie de 10–14 mm en frío, 20 mm en verano).
- **Cantidad**: hoy la norma es *no dar gramos*. Si quieres cantidades (p. ej. «un puñado», «0,5–1 kg por sesión»), hay que decidir si se relaja esa norma en CONOCIMIENTO.md y marcarlas como 🟡 experiencia.
- **Recebado**: cada cuánto y cuánto (p. ej. tras cada picada, cada X horas).
- **Atractivos** (dulce, pescado, aceites…) si quieres que la app opine.
- ⚖ Recordatorio legal: solo productos no tóxicos; nada de cangrejo rojo vivo ni clonk; en embalses de abastecimiento no se puede cebar (probablemente Valuengo).

| Condición (SI) | Tipo y tamaño | Cantidad / recebado | Evidencia | Fuente o motivo |
|---|---|---|---|---|
| p. ej. agua < 10 °C | | | | |
| p. ej. agua 10–17 °C subiendo | | | | |
| p. ej. agua 22–28 °C | | | | |
| | | | | |

### 4.2 Distancia

Hoy no hay ninguna recomendación de distancia. La batimetría de Brovales **no está verificada** (CONOCIMIENTO.md §6.3), así que la distancia es mejor expresarla respecto a referencias del fondo (orilla, primera caída, antiguo cauce) o como «probar a X m y ajustar».

| Condición (SI) | Distancia a probar | Referencia (orilla, caída, cauce…) | Evidencia | Fuente o motivo |
|---|---|---|---|---|
| p. ej. primavera, someras calentándose | | | | |
| p. ej. verano, nivel bajando | | | | |
| | | | | |

### 4.3 Profundidad y posición en la columna (fondo, media agua, superficie)

Lo único que hay hoy:

- *Verano con calma y calor*: «Sombra y vegetación; cebos a media agua si hay actividad» 🟡.
- Playbook de verano (CONOCIMIENTO.md §5.8): «Con carpas en superficie/media agua: flotantes o zig rig; si no, fondo junto a la caída; anotar profundidad» 🟡.
- *Agua > 28 °C con calma*: no descartar el fondo solo por «poco oxígeno» 🟢 (la carpa tolera la hipoxia).
- Zig rig, pop-up, flotantes y PVA son técnicas sin estudios comparativos en carpa (🟡).

| Condición (SI) | Fondo / media agua / superficie | Profundidad orientativa | Montaje | Evidencia | Fuente o motivo |
|---|---|---|---|---|---|
| p. ej. invierno | | | | | |
| p. ej. verano, calor y calma | | | | | |
| | | | | | |

### 4.4 Montajes y presentación

| Condición (SI) | Montaje / bajo / anzuelo | Cuándo cambiarlo | Evidencia | Fuente o motivo |
|---|---|---|---|---|
| | | | | |

## 5. Plantilla para una regla nueva

Copia este bloque por cada recomendación. Si algo no encaja en los apartados actuales, propón uno nuevo (por ejemplo **Distancia** o **Columna**) y lo añado a la pantalla.

```
Nombre corto:            (p. ej. cebado_invierno_tamano)
SI (condiciones):        (p. ej. temp_agua_c ≤ 10 y tendencia_agua_3d_c ≥ 0)
ENTONCES
  Cebado:               
  Distancia:            
  Fondo / media agua:   
  Presentación/montaje: 
  Evitar:               
Evidencia:               🟢 / 🟡 / 🟣 / 🔵 / 🔴
Fuente o motivo:         
¿Cambia la valoración?   no (solo consejo) / sí: resta en el nivel N (factor 0–1)
```

Para que la app aprenda después con tus sesiones (fase 7), conviene que lo que recomiendes también se **anote en el diario**. Hoy la ficha guarda profundidad, cañas, cebo, montaje y cebado en kg; **no guarda distancia ni posición en la columna por caña**: si las añades aquí, las añado también a la ficha de sesión.

## 6. Parámetros que pueden usar las condiciones

| Clave | Qué es |
|---|---|
| `temp_agua_c` | Temperatura del agua (°C): tu medida de las últimas 48 h o la estimación |
| `agua_medida` | Sí si la anterior es una medida tuya |
| `tendencia_agua_3d_c` | Cambio del agua en 3 días (°C, + = calentándose) |
| `estacion` | invierno, primavera, verano u otono (por el agua) |
| `temp_aire_24h_c` | Media del aire en 24 h (°C) |
| `tendencia_aire_c` | Aire de las últimas 24 h frente a los 3 días previos (°C) |
| `dias_calor / dias_frio` | Días seguidos de calor o frío hasta ayer |
| `viento_24h_kmh` | Viento medio 24 h (km/h) |
| `viento_persistencia_24h` | 0–1: lo constante que ha sido la dirección |
| `racha_max_24h_kmh` | Racha máxima 24 h (km/h) |
| `horas_viento_incierto` | Horas de las próximas 24 en que los modelos discrepan |
| `lluvia_24h_mm / lluvia_72h_mm` | Lluvia acumulada (mm) |
| `escorrentia` | Sí si es probable que la lluvia llegue al embalse 🟣 |
| `nivel_delta_7d_hm3` | Cambio del nivel en 7 días (hm³, − = vaciando) |
| `nivel_pct` | Nivel del embalse (%) |
| `ventana_legal_disponible / en_horario_legal` | Horario legal de hoy / ahora |
| `fin_de_semana` | Sábado o domingo (más presión de pesca) |
| `mes` | 1–12 |
| `presion_delta_24h_hpa, nubosidad_pct, luna_iluminacion` | Solo para registro con peso 0 🔴 |

Si necesitas una condición que no esté (p. ej. hora del día, turbidez medida, profundidad del puesto), dímelo y la añado.

## Anexo A. Todas las reglas actuales

### Nivel 0 · Legalidad

**`horario_legal`** · filtro duro · ⚖ · peso alto  
Solo se puede pescar desde 1 h antes del orto hasta 1 h después del ocaso. Brovales no tiene horario libre.  
*Condiciones:* `ventana_legal_disponible` = no  
*Fuente:* Orden de Vedas de 7/11/2022 y resoluciones posteriores  
- Cuándo: No se puede calcular el horario legal de hoy: no se recomienda ninguna sesión.

**`fuera_de_horario_ahora`** · aviso · ⚖ · peso 0  
Ahora mismo estás fuera del horario legal.  
*Condiciones:* `en_horario_legal` = no  
- Cuándo: Ahora es fuera del horario legal: la recomendación es para las ventanas legales de hoy.

**`max_canas`** · aviso · ⚖ · peso 0  
Máximo 3 cañas por persona en 10 m de orilla, con 3 señuelos por caña como máximo.  
*Condiciones:* siempre  
- Presentación: Máximo 3 cañas en 10 m de orilla (3 señuelos por caña).

**`cebado_y_cebos_normativa`** · aviso · ⚖ · peso 0  
Cebado previo permitido con productos no tóxicos (Ley 11/2010, art. 40). Prohibido cebar en embalses de abastecimiento (probable en Valuengo, confirmar). Cangrejo rojo: prohibido poseerlo o transportarlo vivo. Prohibido el clonk.  
*Condiciones:* siempre  
- Cebado: Cebado solo con productos no tóxicos. Nada de cangrejo rojo vivo ni clonk. En embalses de abastecimiento (probablemente Valuengo) no se puede cebar: confírmalo.

### Nivel 1 · Hábitat posible

**`nivel_bajando_rapido`** · multiplicador · 🟣 · peso medio · factor 0.8  
Bajada rápida del nivel (≈ −1 hm³/semana en verano): las reculas someras pierden agua y el litoral cambia cada semana.  
*Condiciones:* `nivel_delta_7d_hm3` ≤ -0.8  
- Dónde: Mover las cañas a la primera caída o cambio de pendiente donde antes había litoral.
- Evitar: Cebar zonas someras que se están quedando sin agua.

**`nivel_subiendo`** · consejo · 🟡 · peso bajo  
Nivel subiendo e inundando pasto: litoral nuevo con comida terrestre.  
*Condiciones:* `nivel_delta_7d_hm3` ≥ 0.3  
- Dónde: Cañas someras junto a la nueva orilla y la entrada de agua.
- Cebado: Cebado pequeño: el litoral recién inundado ya aporta alimento.

### Nivel 2 · Temperatura

**`temp_agua_demanda`** · curva (pertenencia) · 🟢 · peso alto  
La demanda alimentaria sube con la temperatura del agua: tránsito digestivo de 6 %/h a 9 °C a 32,7 %/h a 26,5 °C; consumo de 0,4 % a 4,1 % del peso al día. Curva suavizada a calibrar.  
*Condiciones:* siempre  
*Fuente:* Garcia y Adelman 1985  
*Curva:* `temp_agua_c` → 5 °C: 0.05, 9 °C: 0.15, 14 °C: 0.3, 20 °C: 0.6, 24 °C: 0.9, 26.5 °C: 1.0, 28 °C: 1.0, 30 °C: 0.75, 33 °C: 0.6  

**`agua_estimada`** · aviso · 🟡 · peso 0  
La temperatura del agua es una estimación a partir del aire, no una medida.  
*Condiciones:* `agua_medida` = no  
- Notas: Mide el agua a 0,5 m (y a 3–5 m si puedes): es el dato que más mejora la recomendación.

**`temp_subiendo`** · consejo · 🟢 · peso medio  
Agua calentándose varios días seguidos (p. ej. de 15 a 19 °C).  
*Condiciones:* `tendencia_agua_3d_c` ≥ 1.0  
- Cebado: Subir el cebado de forma progresiva.
- Dónde: Buscar someras que se calientan antes.

**`temp_bajando`** · multiplicador · 🟡 · peso medio · factor 0.85  
Agua enfriándose varios días seguidos (p. ej. de 21 a 17 °C).  
*Condiciones:* `tendencia_agua_3d_c` ≤ -1.5  
- Cebado: Reducir el cebado.
- Dónde: Volver a zonas estables y dejar que el agua se estabilice.

**`desove_17_21`** · multiplicador · 🟢 · peso medio · factor 0.85  
Entre 17 y 21 °C en primavera: posible desove (inicio 17–18 °C, óptimo 18–22 °C). Concentración en someros con vegetación y posible menor alimentación (variable).  
*Condiciones:* `temp_agua_c` entre 17 y 21 y `estacion` ∈ primavera  
*Fuente:* FAO  
- Dónde: Someros con vegetación, sin molestar las zonas de freza. (🟡)
- Cebado: No sobrecebar: pueden comer menos durante el desove.

**`calor_extremo_oxigeno`** · multiplicador · 🟡 · peso bajo · factor 0.8  
Agua > 28 °C con calma: bajar la confianza de cualquier predicción. La carpa tolera la hipoxia: no descartar el fondo por "anóxico".  
*Condiciones:* `temp_agua_c` ≥ 28 y `viento_24h_kmh` ≤ 8  
- Cuándo: Sesión corta al amanecer legal.
- Notas: Mide la temperatura y anota la profundidad de cada caña.
- Evitar: Descartar el fondo solo por "poco oxígeno". (🟢)

**`estacion_invierno`** · consejo · 🟡 · peso medio  
Invierno por temperatura del agua (< 10 °C): ingesta muy baja.  
*Condiciones:* `estacion` ∈ invierno  
- Dónde: Zonas estables algo más profundas junto a caída o antiguo cauce, o someras protegidas del viento frío y con sol a mediodía.
- Cuándo: Franja central del día legal, tras 2–3 días estables y con horas de sol. (🟣)
- Cebado: Mínimo: un puñado o PVA, cebos pequeños y muy atractivos (tránsito ~6 %/h a 9 °C). (🟢)
- Presentación: 1–2 cañas fijas en la misma zona, con paciencia.
- Evitar: Cebaderos grandes, cambiar de puesto cada hora, entradas de agua fría tras lluvia fuerte.

**`estacion_primavera`** · consejo · 🟡 · peso medio  
Primavera por temperatura del agua (10 → 20 °C, subiendo): la ingesta crece rápido.  
*Condiciones:* `estacion` ∈ primavera  
- Dónde: Someras con vegetación o pasto inundado y reculas que se calientan antes; la boca de la recula como paso. (🟡)
- Cuándo: Tarde y anochecer legal; también amanecer si la temperatura asciende.
- Cebado: Progresivo al subir la temperatura; entre 17 y 21 °C no sobrecebar. (🟢)
- Presentación: Cañas repartidas entre orilla somera y boca de recula; mover la que no dé señales tras varias horas. (🟣)
- Evitar: Sobrecebar tras una bajada brusca de temperatura; zonas que se enfrían con viento frío.

**`estacion_verano`** · consejo · 🟡 · peso medio  
Verano por temperatura del agua (> 22 °C): demanda máxima (hasta ~4 % del peso al día). En Brovales, vaciado por riego.  
*Condiciones:* `estacion` ∈ verano  
- Dónde: Seguir el nivel: primera caída o cambio de pendiente donde antes había litoral; sombra, vegetación y entradas si llevan agua. (🟣)
- Cuándo: Amanecer y anochecer legales; evitar el mediodía con calor y calma.
- Cebado: Abundante pero repartido, repuesto en las ventanas de actividad. (🟢)
- Presentación: Con carpas en superficie o media agua: flotantes o zig rig; si no, fondo junto a la caída. Anota la profundidad.
- Evitar: Descartar el fondo solo por "poco oxígeno" (la carpa lo tolera); cebar zonas someras que se secan. (🟢)

**`estacion_otono`** · consejo · 🟡 · peso medio  
Otoño por temperatura del agua (20 → 10 °C, bajando). Que el otoño sea siempre buena época es experiencia de pescadores, no ciencia.  
*Condiciones:* `estacion` ∈ otono  
- Dónde: Litoral recién inundado, reculas y entradas con escorrentía real; fondos duros si hay cangrejo (sin datos en Brovales). (🟣)
- Cuándo: Horas centrales templadas y atardecer legal; tras un frente frío esperar 1–2 días.
- Cebado: Bajarlo a medida que baja la temperatura; si cae de golpe (21 → 17 °C), reducir más. (🟢)
- Presentación: Una caña en el litoral nuevo y otra en la caída próxima; registrar cuál recibe picadas. (🟣)
- Evitar: Dar por hecho que el otoño siempre es buena época; ignorar la tendencia térmica.

### Nivel 3 · Modificadores físicos

**`lluvia_sin_escorrentia`** · consejo · 🟡 · peso bajo  
Ha llovido pero sin escorrentía probable (suelo seco): 20 mm sobre suelo seco no alteran el agua.  
*Condiciones:* `lluvia_72h_mm` ≥ 1 y `escorrentia` = no  
- Notas: No cambiar el plan por la lluvia; anota la lluvia y cómo estaba el suelo.

**`escorrentia_invierno`** · multiplicador · 🟡 · peso bajo · factor 0.8  
Lluvia fría fuerte con escorrentía en invierno: la entrada aporta agua helada.  
*Condiciones:* `escorrentia` = sí y `estacion` ∈ invierno  
- Dónde: Zonas estables y protegidas.
- Cebado: Mínimo.
- Evitar: La cola y la entrada de agua fría.

**`escorrentia_turbia`** · consejo · 🟣 · peso bajo  
Lluvia fuerte con escorrentía real: la cola puede enturbiarse y la entrada trae alimento.  
*Condiciones:* `escorrentia` = sí y `estacion` ∈ primavera, verano, otono  
- Dónde: Cola y borde de la pluma turbia, no el centro del barro.
- Cebado: Pequeño: el agua ya trae comida.

**`tormenta_verano_entrada_fresca`** · consejo · 🟣 · peso bajo  
Verano con escorrentía: la entrada fría y oxigenada se hunde por el cauce (física 🟢; efecto en la carpa sin demostrar).  
*Condiciones:* `escorrentia` = sí y `estacion` ∈ verano  
- Dónde: Una caña en la caída frente a la desembocadura.

**`viento_calido_sotavento`** · consejo · 🟡 · peso bajo  
Viento sostenido 24 h sin enfriamiento: el agua calentada se acumula a sotavento (física). En Brovales el fetch es corto: úsalo como desempate.  
*Condiciones:* `viento_24h_kmh` ≥ 10 y `viento_persistencia_24h` ≥ 0.7 y `tendencia_aire_c` ≥ 0  
- Dónde: Desempate: orilla a sotavento, hacia donde sopla el viento (sopla del {viento_desde}, empuja hacia el {viento_hacia}).

**`frente_frio`** · multiplicador · 🟡 · peso medio · factor 0.85  
Frente frío o viento frío súbito: la demanda cae con el agua más fría.  
*Condiciones:* `tendencia_aire_c` ≤ -3  
- Cebado: Bajar el cebado.
- Dónde: Zonas protegidas y soleadas.
- Cuándo: Si puedes, esperar 1–2 días a que se estabilice.

**`viento_fuerte_oleaje`** · consejo · 🟣 · peso bajo  
Viento fuerte con oleaje en la orilla expuesta.  
*Condiciones:* `racha_max_24h_kmh` ≥ 35  
- Dónde: Probar la orilla batida con fondo blando (remueve alimento) o una recula protegida si no hay señales.

**`verano_calma_calor`** · consejo · 🟡 · peso bajo  
Verano con calma y calor.  
*Condiciones:* `estacion` ∈ verano y `viento_24h_kmh` ≤ 8 y `temp_aire_24h_c` ≥ 24  
- Cuándo: Amanecer y anochecer legales; no insistir a mediodía.
- Dónde: Sombra y vegetación; cebos a media agua si hay actividad.

**`viento_incierto`** · aviso · 🟡 · peso 0  
Los modelos discrepan en el viento previsto: la parte de viento de esta recomendación es poco fiable.  
*Condiciones:* `horas_viento_incierto` ≥ 6  
- Notas: Viento previsto incierto: decide la orilla con el viento que encuentres al llegar.

### Nivel 4 · Capturabilidad

**`hora_amanecer_anochecer`** · consejo · 🟡 · peso medio  
Las ventanas útiles son el amanecer y el anochecer legales. El pico nocturno de las competiciones (Žák 2021) no es aplicable en Brovales.  
*Condiciones:* `ventana_legal_disponible` = sí  
- Cuándo: Mira las ventanas horarias sugeridas: siempre dentro del horario legal.

**`presion_pesca_fin_de_semana`** · multiplicador · 🟢 · peso bajo · factor 0.9  
Más presión de pesca en fin de semana o festivo (otros pescadores, ruido, barcas).  
*Condiciones:* `fin_de_semana` = sí  
- Dónde: Evitar el puesto evidente; zonas menos obvias.
- Presentación: Cebado discreto y presentaciones distintas.

**`presion_pesca_puesto`** · consejo · 🟢 · peso medio  
Las capturas repetidas en un puesto bajan la capturabilidad durante semanas o meses (sin evidencia de más de 7 meses).  
*Condiciones:* siempre  
*Fuente:* Lovén Wallerius et al. 2020; Czapla et al. 2023  
- Presentación: Si capturaste en este puesto hace poco, rota puesto y montaje; usa bajos menos visibles.

**`senales_de_campo`** · consejo · 🟡 · peso medio  
Burbujas, saltos, nubes de sedimento o carpas en movimiento son la mejor señal del día, por encima de cualquier predicción.  
*Condiciones:* siempre  
- Notas: Si ves burbujas, saltos o nubes de sedimento, mueve las cañas hacia esa zona y anótalo.
- Notas: Tras horas sin señales, cambia de zona por función (calentamiento, paso, entrada) y registra el bolo con sus horas-caña.

### Nivel Exploratorias (peso 0)

**`luna`** · registro · 🔴 · peso 0  
Fase lunar: sin estudios en carpa. Se registra para comprobarlo con tus datos.  
*Condiciones:* siempre  

**`presion_barometrica`** · registro · 🔴 · peso 0  
Presión y su tendencia: solo como indicador de cambio de tiempo, nunca "X hPa = bueno". La carpa es fisóstoma.  
*Condiciones:* siempre  

**`nubosidad`** · registro · 🔴 · peso 0  
Nubosidad: sin evidencia en carpa. Se registra con peso 0.  
*Condiciones:* siempre  

## Anexo B. Estrategias de CONOCIMIENTO.md §5 (fuente de las reglas)

> Las conversaciones originales sí daban estrategias, pero mezclaban fisiología demostrada, experiencia de pescadores y mecanismos físicos convertidos en reglas. Aquí van separadas. **La app debe mostrar siempre la etiqueta.**

#### 5.1 Por temperatura del agua (lo mejor respaldado)

| Rango | Qué dice la ciencia (🟢) | Experiencia / hipótesis (🟡/🟣) |
|---|---|---|
| **<10 °C** | Ingesta muy baja; tránsito ~6 %/h a 9 °C | Poco cebo, cebos pequeños y muy atractivos, puesto fijo largo. Zonas algo más profundas/estables; días soleados tras varios estables; agua que primero se calienta por la tarde. Agregación invernal en cauce = hipótesis |
| **10–17 °C** (primavera/otoño) | La ingesta crece rápido con la temperatura | Orillas someras que se calientan antes son prioritarias en primavera. Cebado progresivo creciente |
| **17–22 °C** | Ventana de desove (17–18 °C inicio, óptimo 18–22 °C) | Concentración en someros con vegetación; posible menor alimentación durante el desove. La app avisa como "variable" |
| **22–28 °C** | Máxima demanda (hasta ~4 % peso/día a 26,5 °C) | Mayor sentido del cebado abundante; actividad en horas frescas |
| **>28 °C, O₂ bajo** | La carpa tolera hipoxia; no se puede descartar el fondo por "anóxico" | Bajar confianza en cualquier predicción; priorizar amanecer |

#### 5.2 Por hora del día
- Hay picos nocturnos de captura en competiciones (Žák 2021) pero el uso de profundidad varía según el embalse (Flix vs río Perla).
- **En Brovales solo es legal pescar desde 1 h antes del orto hasta 1 h después del ocaso.** Las ventanas útiles son amanecer y anochecer *legales*. La app las calcula con SunCalc y **no ofrece horas nocturnas**.
- No usar "08–11 h y 19–23 h" (fuente no verificada).

#### 5.3 Por nivel del embalse
- Con vaciado activo (~−1 hm³/semana en verano) las reculas someras pierden agua y el litoral cambia cada semana. 🟣 Plausible: desplazar cañas a primeras caídas o cambios de pendiente cercanos.
- En subidas otoñales con pasto inundado: 🟡 experiencia de pescadores, buscar el nuevo litoral.
- Registrar cota o % en cada sesión.

#### 5.4 Por viento
- 🟢 física: el agua calentada se acumula a sotavento. 🟡 experiencia: pescar "de cara al viento" cálido. 🔴/🟡 no demostrados: seiches y "viento nuevo". Tratarlo como **desempate**, no como regla principal.

#### 5.5 Cebado y cebos (con normativa)
- Ley 11/2010 (art. 40) autoriza cebado previo con productos no tóxicos.
- Orden de 2022: **prohibido cebar en embalses de abastecimiento** (probablemente Valuengo; confirmar con el Servicio de Pesca). A Brovales (uso de riego), en principio no.
- Prohibidos como cebo en aguas de río: almejas, mejillones, cangrejos y peces continentales. El único pez vivo permitido en aguas embalsadas es la tenca de acuicultura acreditada. Prohibido el clonk.
- **Cangrejo rojo: invasor; prohibido poseerlo o transportarlo vivo.** Captura con reteles solo para autoconsumo.
- Cantidad de cebo: ajustar a temperatura (§5.1). La regla "menos cebo en frío" es correcta en el fondo (demanda baja), aunque la explicación de "no digiere" no lo sea.

#### 5.6 Montajes
- Pop-up, Zig Rig y bolsas de PVA son técnicas de pescadores con lógica pero **sin estudios comparativos en carpa** (🟡).
- Wallerius 2020 respalda tres ideas generales: rotar presentaciones en aguas presionadas, usar bajos menos visibles, no repetir el mismo montaje en el mismo puesto con peces ya capturados.

#### 5.7 Presión de pesca
- Las capturas repetidas en un puesto reducen la capturabilidad durante semanas o meses. La app registra capturas por zona y sugiere rotar.

#### 5.8 Playbook por estación y por condiciones (SI → ENTONCES)

> Punto de partida operativo. **Casi todo es experiencia de pescadores o hipótesis local**: la app lo muestra con su etiqueta (Respaldado 🟢 · Experiencia/moderada 🟡 · Hipótesis local 🟣 · Sin evidencia, peso 0 🔴) y lo sustituye por los datos del propio usuario cuando haya 20–40 sesiones. Las estaciones se deciden por la **temperatura del agua**, no por el calendario. Todo respeta el filtro legal (§7): solo horas legales, máx. 3 cañas.

##### Por estación (disparador: temperatura del agua y tendencia)

| | **Invierno** (agua <10 °C) | **Primavera** (10→20 °C, subiendo) | **Verano** (>22 °C) | **Otoño** (20→10 °C, bajando) |
|---|---|---|---|---|
| **Contexto Brovales** | Nivel semanal; puede recuperarse con lluvias tras el riego | Nivel alto (≈86 % a finales de junio): litoral disponible antes del vaciado | Vaciado por riego (≈ −30 puntos de junio a septiembre); arroyos con estiaje | Primeras lluvias pueden frenar el vaciado y subir nivel; vigilar escorrentía |
| **Dónde** 🟡 | Zonas estables algo más profundas junto a caída/antiguo cauce, o someras protegidas del viento frío y con sol a mediodía | Someras con vegetación/pasto inundado y reculas que se calientan antes; boca de recula como paso (🟣) | 🟣 Seguir el nivel: primera caída o cambio de pendiente donde antes había litoral; sombra, vegetación, entradas si llevan agua | 🟣 Litoral recién inundado, reculas y entradas con escorrentía real; fondos duros si hay cangrejo (sin datos en Brovales) |
| **Cuándo** | 🟣 Franja central del día legal, tras 2–3 días estables y horas de sol | 🟡 Tarde y anochecer legal; amanecer si la temperatura asciende | 🟡 Amanecer y anochecer legales; evitar mediodía con calor y calma | 🟡 Horas centrales templadas y atardecer legal; tras frente frío esperar 1–2 días |
| **Cebado** 🟢 | Mínimo: puñado o PVA, cebos pequeños y muy atractivos (6 %/h a 9 °C) | Progresivo al subir la temperatura; 17–21 °C pueden comer menos por el desove: no sobrecebar | Demanda máxima (~4 % del peso/día): abundante pero repartido, repuesto en ventanas de actividad | Bajar a medida que baja la temperatura; si cae de golpe (21→17 °C) reducir más |
| **Presentación** | 🟡 1–2 cañas fijas en la misma zona, paciencia | 🟣 Cañas repartidas entre orilla somera y boca de recula; mover la que no dé señales tras varias horas | 🟡 Con carpas en superficie/media agua: flotantes o zig rig; si no, fondo junto a la caída; anotar profundidad | 🟣 Una caña en litoral nuevo y otra en la caída próxima; registrar cuál recibe picadas |
| **Evita** | Cebaderos grandes, cambiar de puesto cada hora, entradas de agua fría tras lluvia fuerte | Sobrecebar tras bajada brusca de temperatura; zonas que se enfrían con viento frío | 🟢 Descartar el fondo solo por "poco oxígeno" (la carpa lo tolera); cebar zonas someras que se secan | Dar por hecho que el otoño siempre es buena época (experiencia, no ciencia); ignorar la tendencia térmica |

##### Por condición (disparadores del motor de reglas)

| SI (parámetros) | ENTONCES | Evidencia |
|---|---|---|
| **Lluvia** ligera, suelo seco, sin entrada visible | No cambiar el plan (20 mm sobre suelo seco no alteran el agua); registrar lluvia y humedad del suelo | 🟡 |
| **Lluvia** fuerte con escorrentía real y cola turbia | Probar cola y borde de la pluma turbia (no el centro del barro); cebado pequeño: el agua ya trae comida | 🟣 |
| Verano + tormenta con entrada más fresca | Entrada fría y oxigenada se hunde por el cauce: una caña en la caída frente a la desembocadura | 🟣 (física 🟢, efecto en carpa sin demostrar) |
| Invierno + lluvia fría fuerte | Evitar entrada y cola (agua helada); zonas estables y protegidas, cebado mínimo | 🟡 |
| Primavera/otoño + lluvia templada + nivel subiendo | Litoral recién inundado: cañas someras junto a nueva orilla y entrada, cebado pequeño | 🟡 |
| **Viento** cálido sostenido 24–48 h hacia orilla con fetch | Priorizar orilla a sotavento (acumula agua cálida y alimento); en Brovales el fetch es corto: desempate | 🟡 |
| Frente frío o viento frío súbito | Bajar cebado, zonas protegidas y soleadas, esperar (la demanda cae con el agua más fría) | 🟡 |
| Verano + calma + sol + calor | Amanecer y anochecer, sombra y vegetación, cebos a media agua si hay actividad; no insistir a mediodía | 🟡 |
| Nublado o presión bajando | No usar la presión como regla; registrar nubosidad y presión con **peso 0** | 🔴 |
| Viento fuerte con oleaje en orilla expuesta | Probar orilla batida con fondo blando (remueve alimento) o recula protegida si no hay señales | 🟣 |
| **Temperatura** subiendo 3 días (15→19 °C) | Subir cebado progresivamente, buscar someras que se calientan; 17–21 °C vigilar desove | 🟢 |
| Temperatura bajando 3 días (21→17 °C) | Reducir cebado, volver a zonas estables, dejar que el agua se estabilice | 🟡 |
| Agua >28 °C y calma | Bajar confianza de cualquier predicción; sesión corta en el amanecer; medir temperatura y registrar profundidad | 🟡 |
| **Nivel** bajando rápido (≈ −1 hm³/semana) | Mover cañas a la primera caída o cambio de pendiente; no cebar zona somera que se queda sin agua | 🟣 |
| Nivel subiendo e inundando pasto | Cañas someras junto a la nueva orilla, cebado pequeño (litoral nuevo con comida terrestre) | 🟡 |
| **Campo**: burbujas, saltos, nubes de sedimento, carpas en movimiento | Cañas hacia esa zona: mejor señal del día, por encima de cualquier predicción; anotarla | 🟡 |
| Mucha presión (fin de semana, otros pescadores, ruido, barcas) | Evitar el puesto evidente; zonas menos obvias, cebado discreto, presentaciones distintas | 🟢 |
| Ya capturaste en el puesto hace pocos días | Rotar puesto y montaje (evitación del anzuelo: semanas o meses, ≤7 meses) | 🟢 |
| Horas sin señales ni picadas | Cambiar de zona por función (calentamiento, paso, entrada); registrar el bolo con sus horas-caña | 🟡 |
| Historial propio en esas condiciones | Repetir lo que funcionó; con 20–40 sesiones los datos propios pasan por delante de este playbook | 🟣 |

**Notas de implementación:**
- Las condiciones se evalúan en este orden: legalidad → temperatura/tendencia → nivel → entradas/lluvia con escorrentía → viento → señales de campo → presión de pesca. Varias pueden activarse a la vez; mostrar al usuario **todas las reglas activas con su etiqueta** y la que domina (la de nivel inferior en la cadena de §4).
- "Lluvia con escorrentía real" exige lluvia acumulada, humedad del suelo y tiempo desde la última lluvia (no basta un valor de precipitación).
- Las cantidades de cebo se expresan como **demanda esperada (muy baja → muy alta)**, nunca como gramos fijos: no hay datos para fijar gramos por temperatura.
- Zig rig, flotantes, PVA y similares son técnicas de pescadores sin estudios comparativos en carpa (🟡).

```yaml
## Ejemplo de regla del playbook (formato sugerido)
- id: pb_nivel_bajando_rapido
  cuando:
    nivel_delta_7d_hm3: "<= -0.8"      # umbral inicial a calibrar
    estacion_por_temp: "verano"
  entonces:
    recomendar: "Mover cañas a primera caída / cambio de pendiente cercano al litoral anterior"
    evitar: "Cebar zona somera que se queda sin agua"
  evidencia: morado        # hipótesis local
  peso_inicial: bajo
- id: pb_lluvia_escorrentia_invierno
  cuando:
    escorrentia_real: true
    temp_agua_c: "< 10"
  entonces:
    recomendar: "Zonas estables y protegidas, cebado mínimo"
    evitar: "Cola y entrada de agua fría"
  evidencia: amarillo
  peso_inicial: bajo
```
