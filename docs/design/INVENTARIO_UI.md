# Inventario de funciones de la interfaz (revisión de UI, fase 7.28)

Lista de todo lo que se puede hacer en la app, pantalla por pantalla, con dónde estaba antes de la
revisión (0.33.0) y dónde está ahora (0.34.0). Regla de la revisión: **no se pierde ninguna función**;
solo se reubican, se renombran o se les cambia el icono. Al final, la comprobación.

## Navegación

| Función | Antes | Ahora |
|---|---|---|
| Pestañas | Hoy · Estrategia · Datos · Diario · Preparar | Hoy · **Preparar** · Estrategia · Diario · **Datos** (orden de una salida: mirar → preparar → pescar → apuntar) |
| Icono de Preparar | chincheta de ubicación | lista con marcas (es una lista de pasos, no un lugar) |
| Volver de una subpantalla | botón de texto «Volver» | flecha ← arriba a la izquierda (misma en las 12 subpantallas, con «Volver» para lectores de pantalla) |
| Volver a la sesión guiada abierta | solo desde la notificación o Diario → Sesión en curso | además, **banda «Sesión guiada en curso»** encima de las pestañas en toda la app (desde qué hora y próximo aviso) |
| Abrir la guiada desde la notificación | igual | igual |

## Hoy
Fase 3 (0.36.0): fila nueva «Ver la estrategia para hoy ›» bajo el horario legal (lleva a Estrategia). Sin cambios de función: actualizar, avisos de antigüedad, temperatura del agua (medida/estimada) y «Añadir
medición», datos clave, horario legal, datos con peso 0, gráficas de presión y temperatura, prioridad de
fuentes, «Ver datos en bruto» (subpantalla, ahora con flecha ←), «Fijar ubicación» (abre Preparar).

## Preparar
| Función | Antes | Ahora |
|---|---|---|
| Ver embalse activo, «Cambiar de embalse», «Ficha del embalse» | sección Embalse con botones | **① Embalse**: tarjeta con el nombre en grande y filas «Cambiar de embalse ›» y «Ficha del embalse ›» con lo que hay dentro |
| Elegir el puesto de hoy (Automático o uno) y «Mis puestos» | sección Puesto | **② Puesto**: mismos chips y fila «Mis puestos ›» |
| Mi equipo (cañas, cebos, lo que llevas hoy) | sección con un botón | **③ Tus cañas y cebos**: fila «Mi equipo ›» |
| Preparar la próxima salida: elegir día y hora, preparar, «Preparar de nuevo», «Ver la preparación», cómo fue cada paso | tarjeta en medio | **④ Preparar la próxima salida** (misma tarjeta, numerada) |
| Aviso de la próxima salida (interruptor y permiso de notificaciones) | casi al final | justo debajo de ④, con la que va |
| Lo aprendido | sección con un botón | apartado «Más»: fila «Lo aprendido ›» |
| Ajustes | botón al final | **engranaje arriba a la derecha** y fila «Ajustes ›» en «Más» |
| Ubicación: GPS, nombre, latitud/longitud, guardar, volver a la predeterminada | en medio | al final (se toca poco; Hoy → «Fijar ubicación» sigue llevando aquí) |
| Versión | al final | al final |

## Ajustes (Preparar → ⚙)
Cuenta de Google (entrar, sincronizar, salir), asistente de IA (ahora fila «Configurar el asistente ›»
con lo que hay dentro), apariencia (estilo, tema, color dinámico), fuentes, leyenda de evidencias,
limitaciones, normativa con «última revisión» y versión. Flecha ← para volver. Sin cambios de función.

## Estrategia
| Función | Antes | Ahora |
|---|---|---|
| Salida preparada de hoy: Activar, Reevaluar, nuevo plan, cerrar revisión, «Ver la preparación» | arriba | arriba (igual) |
| Condiciones para hoy (escala y nivel que limita) | arriba | igual |
| ① Dónde: embalse/puesto, recomendación, «Pescar en …», «Cambiar embalse o puesto», ficha | igual | igual (título numerado común con Preparar) |
| ② Lo que llevas hoy (número de cañas sin cañas apuntadas, «Cambiar cañas y cebos») | igual | igual |
| ③ Tu plan: mejor rato, origen del plan, preguntar a la IA / otra vez, configurar IA, una línea por caña con su porqué, cebado y engodo, especies, consejos IA, «Si cambia algo», Lo aprendido | igual | igual |
| **Empezar sesión guiada con este plan** | al final, debajo de «Detalles» | **justo debajo del plan** (sin bajar hasta el final) |
| Detalles: qué hago ahora, consejos IA, qué hacer, cuándo, avisos, rotar, evitar, cadena, demanda, reglas, registradas, no evaluables, normativa | plegado | plegado (igual), después del botón principal |
| Acceso «Preparar la próxima salida» | botón con borde al final | enlace al final (es secundario) |

## Sesión guiada
| Función | Antes | Ahora |
|---|---|---|
| Permisos (notificaciones, alarmas exactas), empezar (cañas, puesto, zona, cebado, agua medida), «Mis puestos», «Mi equipo» | igual | igual |
| Sesión olvidada → terminar con horas | arriba | arriba |
| Cabecera (fase, próximo aviso, «Nada en ninguna», viento, condiciones) | igual | igual |
| Propuestas por caña (aceptar / no) y mensajes | tras tiempo, IA y chat | **justo debajo de la cabecera** |
| Elegir caña, plan de la caña, registro del aviso | tras tiempo, IA y chat | justo después de las propuestas |
| Tiempo (¿Llueve?…), opinión de la IA (preguntar), chat con la IA | arriba, antes de lo de cada aviso | debajo del registro |
| Historial, Terminar (con horas), avisos legales | al final | al final |

## Mi equipo (fase 2, 0.35.0)
| Función | Antes | Ahora |
|---|---|---|
| Cañas (fija/carpfishing, longitud, la llevo hoy), añadir y editar | sección al final de una lista larga | **pestaña Cañas** (la primera) |
| Cebos: «Hoy llevo», «Empezar de cero hoy», guardados por último uso, «Del diario», ficha de la IA, añadir, editar, medida de cebado | primera sección | **pestaña Cebos** |
| Montajes: añadir y editar | sección del medio | **pestaña Montajes** |
| Cuántas cañas y cebos llevas hoy | había que contarlos | resumen arriba y número en cada pestaña |
| «Buscar de nuevo las fichas pendientes» | arriba | arriba (igual, en todas las pestañas) |

## Resto de subpantallas (solo cambia «Volver» → flecha ←)
- **Mis puestos** y **Mapa de puestos**: añadir, editar, borrar, recolocar en el mapa.
- **Embalses** y **Ficha del embalse**: buscar, elegir, embalse propio, ficha con la IA, confirmar especies, cebado.
- **Asistente de IA**: proveedor, clave (móvil/cuenta), modelos, probar, modo de avisos, consumo. Antetítulo «Ajustes».
- **Lo aprendido**: añadir, aceptar/rechazar, repasar la última sesión, exportar.
- **Ver la preparación**: foto de la salida preparada y «Activar».
- **Importar JSON** (Datos) y **Datos en bruto** (Hoy).

## Diario y Datos
Sin cambios de función: sesiones, sesión en curso, nueva sesión, ficha (capturas, fotos, viento observado,
mediciones), borrar, exportar/restaurar diario y fotos, «¿Acierta la app?»; Datos: filtros, nuevo registro,
editar, borrar, importar y exportar.

## Fase 3 (0.36.0): accesibilidad e interacción (skills ui-ux-pro-max y frontend-design)
| Cambio | Por qué (regla de la skill) |
|---|---|
| Títulos de pantalla, de sección y de paso marcados como encabezado para TalkBack | Accesibilidad (prioridad 1): navegar por encabezados |
| «Ver detalles» de Estrategia con flecha ▾/▴ y estado «Plegado/Desplegado» | Estado visible y anunciado, no solo por el texto |
| Hoy → «Ver la estrategia para hoy ›» | Navegación (prioridad 9): siguiente paso sin buscar la pestaña |
| Guiada: selector de caña en varias líneas si los nombres son largos | Layout (prioridad 5): nada se sale de la pantalla |
| Mi equipo: pestañas en una línea con «…» si no caben | Layout: sin saltos de línea en controles |

## Comprobación
Se compararon, fichero a fichero, todas las acciones de la interfaz (`viewModel::…`, `onX = …`,
`onClick = …` y rutas de navegación) entre 0.33.0 y esta versión: las mismas, más las nuevas (banda de la
sesión guiada y engranaje de Ajustes). Ninguna ruta ni pantalla se ha quitado.
