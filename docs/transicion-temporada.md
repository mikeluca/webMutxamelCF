# Transición de temporada: diseño final (29/10/2026)

Basado en la revisión de web `108971b` y app `bb93e3a` y en las decisiones del 28 y 29/10/2026. Complementa `plantillas-temporada.md`. Los prompts para Claude Code están en `prompts-transicion-temporada.md`.
Objetivo: tenerlo listo para el cierre de la temporada 2026-27 (1/7/2027).

## 1. Principios
- **Archivar es marcar, no copiar.** Partidos, entrenamientos, sesiones, asistencia, justificaciones, convocatorias, estadísticas y comunicaciones se quedan en sus tablas; las pantallas filtran por las fechas de la temporada activa. No hay tablas `HIST_*` ni borrados masivos.
- **Plantillas: una foto por temporada.**
  - Jugadores: `JUGADOR_TEMPORADA` (ya existe).
  - Cuerpo técnico: `CUERPO_TECNICO_TEMPORADA` (nueva).
  - Jugadores, entrenadores, familias y usuarios conservan siempre su ID.
- **Temporadas contiguas,** del 1/7 al 30/6, sin huecos ni solapes.
- **Solo se activa desde el asistente** "Cerrar temporada". En Pagos → Temporadas ya no se puede marcar una temporada como activa (salvo la primera vez, cuando no hay ninguna).

## 2. Decisiones
| # | Tema | Decisión |
|---|---|---|
| 1 | Fechas | Del 1/7 al 30/6 |
| 2-3 | Cierre | A mano, solo el rol SUPER |
| 4 | Jugadores de julio a septiembre | Categoría nueva por edad, sin equipo definitivo; entrenan en el equipo de pretemporada de su categoría (§4) |
| 5 | Pretemporada | Hay entrenamientos y amistosos en agosto: se gestionan en la app con los equipos de pretemporada |
| 6 | Reparto a equipos definitivos | Lo hace el coordinador, desde la app o desde la web |
| 7 | Continuidad | Las familias confirman en la app. Si no contestan, el jugador queda pendiente de revisar |
| 8 | Juvenil de último año | Pendiente: el coordinador elige Filial, Primer Equipo o baja |
| 9 | Filial, Primer Equipo y Veteranos | Los jugadores se quedan en su equipo |
| 10 | Dorsales | Se ponen a 0 (salvo Filial, Primer Equipo y Veteranos) |
| 11 | Pruebas | Alta con estado PRUEBA en su categoría, pendiente de pasar a un equipo definitivo |
| 12 | Año de nacimiento | Todos los jugadores lo tendrán rellenado antes del cierre |
| 13 | Cuerpo técnico | Se desasigna a todos salvo los de Primer Equipo, Filial y Veteranos |
| 14 | Entrenador sin equipo | Entra en la app y ve su perfil y las comunicaciones generales |
| 15 | Coordinadores | Sin cambios |
| 16-17 | Equipos | Se reutilizan (el Alevín A es siempre el mismo); cada temporada dice cuáles juegan; los que no juegan se ocultan |
| 18 | Web pública de julio a septiembre | "Plantillas en preparación"; resultados y clasificaciones de la temporada anterior hasta que empiece la competición |
| 19 | Historial | Solo en el panel |
| 20 | Clasificación | Se guarda solo la última de cada equipo |
| 21 | Comunicaciones | Los avisos de equipo y de categoría se ocultan al cambiar de temporada; los chats privados se mantienen; se purga lo que tenga más de 2 temporadas |
| 22 | Notificaciones de la app | Se borran al cierre |
| 23 | Horarios | Se copian desactivados y se reactivan en septiembre |
| 24 | Conceptos de pago | Se copian a la temporada nueva y luego se ajustan |
| 25 | Cuotas de otros años | Las familias no las ven. El panel tiene un apartado de **Morosidad**: listado por temporada y jugador con total, estado PENDIENTE / COBRADA / INCOBRABLE y exportación |
| 26 | Asignación de cuotas | Automática, en el momento en que el coordinador asigna el equipo definitivo; nunca a jugadores PRUEBA |
| 27 | Familias sin jugadores activos | Su cuenta se desactiva a los 3 meses, con aviso previo |
| 28 | Aviso de cambio de temporada | Se envía a todos |
| 29 | Ensayo | Obligatorio en DEV con una copia de PRO y copia de seguridad previa |
| 30 | Plazo | Se empieza ya |

## 3. Cambios de modelo (SQL)
- `JUGADOR_TEMPORADA`:
  - `ESTADO`: CONFIRMADO / PRUEBA / BAJA;
  - `CONTINUIDAD`: PENDIENTE / SIGUE / NO_SIGUE / DUDA, más FECHA_CONTINUIDAD y USUARIO_APP_CONTINUIDAD_ID;
  - `EQUIPO_DEFINITIVO`: 0/1. La categoría ya existe (CATEGORIA).
- `CUERPO_TECNICO_TEMPORADA` (CUERPO_TECNICO_ID, TEMPORADA_ID, EQUIPO_ID, PUESTO): foto de `CUERPO_TECNICO_EQUIPO` al cerrar.
- `EQUIPO_TEMPORADA` (EQUIPO_ID, TEMPORADA_ID, ACTIVO, MODALIDAD, TIPO). TIPO = NORMAL / PRETEMPORADA.
- `TEMPORADA_ID` en `CLASIFICACIONES`, `EQUIPOS_FFCV` y `HORARIOS_ENTRENAMIENTO`.
- `COMUNICACIONES`: los avisos de equipo y de categoría se filtran por FECHA ≥ inicio de la temporada activa. No hace falta columna nueva.
- `CUOTAS_JUGADOR`: estado INCOBRABLE para la morosidad.

## 4. Pretemporada (julio a septiembre)
Al cerrar se crea (o se reactiva) **un equipo de pretemporada por categoría formativa**: "Alevín (Pretemporada)", con EQUIPO_TEMPORADA.TIPO = PRETEMPORADA.
- Reutiliza todo lo que ya existe: horarios, sesiones, asistencia, convocatorias y amistosos, sin código nuevo en la app.
- Los jugadores de la categoría (también los de PRUEBA) quedan en él con EQUIPO_DEFINITIVO = 0; el coordinador le asigna entrenadores.
- No genera cuotas; se oculta en la web pública.
- En septiembre, el coordinador mueve a cada jugador a su equipo definitivo. Al quedar vacío, el equipo de pretemporada se desactiva.

## 5. Flujo completo
1. **Abril:** crear la temporada 2027-28 y revisar `CATEGORIAS_EDAD` y los años de nacimiento (el cierre se bloquea si falta alguno).
2. **1 de mayo:** se genera el borrador con la categoría por edad de cada jugador (Filial, Primer Equipo y Veteranos se quedan en su equipo). Se envía a las familias la pregunta "¿Seguirá X en 2027-28?" (Sí / No / Aún no lo sé), con recordatorio semanal.
3. **Mayo y junio:**
   - altas de PRUEBA en el borrador;
   - el coordinador resuelve las excepciones: NO_SIGUE → baja; DUDA o sin respuesta → decide; Juvenil de último año.
4. **1 de julio, "Cerrar temporada"** (SUPER, una transacción, confirmación escribiendo el nombre de la temporada). Se bloquea si queda algún pendiente o algún jugador sin año de nacimiento. Hace esto:
   1. guarda las fotos: `CUERPO_TECNICO_TEMPORADA` (la última clasificación y los enlaces FFCV ya llevan su TEMPORADA_ID);
   2. prepara `EQUIPO_TEMPORADA` de la nueva y los equipos de pretemporada;
   3. aplica el borrador: bajas, PRUEBA, dorsales a 0 y cada jugador formativo a su equipo de pretemporada;
   4. desasigna al cuerpo técnico, salvo el de Primer Equipo, Filial y Veteranos;
   5. desactiva los horarios, los copia a la nueva temporada (desactivados) y borra las sesiones futuras;
   6. copia los conceptos de pago;
   7. borra las notificaciones de la app;
   8. activa la temporada nueva;
   9. envía el aviso a todos.
5. **Julio a septiembre:** pretemporada, con los equipos de pretemporada en la app.
6. **Septiembre, reparto:** el coordinador asigna los equipos definitivos desde la app o la web.
   - En ese momento se generan las cuotas del jugador (salvo PRUEBA), EQUIPO_DEFINITIVO = 1 y se le pone dorsal.
   - Después: reactivar los horarios, enlazar EQUIPOS_FFCV, importar el PDF de la FFCV para validar (fase 2 de `plantillas-temporada.md`) y crear los equipos que falten.
7. **Tareas periódicas:**
   - purga de comunicaciones de más de 2 temporadas;
   - cuentas de familias sin jugadores activos: aviso y desactivación a los 3 meses.

## 6. Correcciones previas (fase 0, detectadas en la revisión)
- Pagos → Temporadas permite activar una temporada sin pasar por el asistente, y al editar la activa sin marcar la casilla la desactiva. Además no impide que dos temporadas se solapen.
- Las estadísticas y la asistencia de equipo (`ResumenTemporadaDaoImpl`) salen de `JUGADORES.EQUIPO_ID` (plantilla actual): tienen que salir de `JUGADOR_TEMPORADA`.
- Listados sin filtro de temporada: `PartidoServiceImpl.obtenerPorEquipo` (Resultados del panel), `ConvocatoriaServiceImpl.obtenerPorEquipo` (app) y `EntrenamientoServiceImpl.obtenerPorEquipo` sin rango.
- Bug: `PartidoServiceImpl.validarPartidoAnteriorTieneResultado` mira el último partido del equipo de cualquier temporada. Si el último de la temporada anterior se quedó sin resultado, bloquearía el primero de la nueva.
- (Descartado) Asignar cuotas ya resuelve el equipo por EQUIPO_ID con un JOIN y excluye las bajas: no hace falta cambiarlo.

## 7. Plan de desarrollo
| Fase | Cuándo | Contenido |
|---|---|---|
| 0 | Noviembre | Correcciones del §6 |
| 1 | Noviembre y diciembre | SQL del §3 con su esquema H2 y adaptación de las consultas a EQUIPO_TEMPORADA (equipos activos) y a TEMPORADA_ID (FFCV, clasificaciones, horarios) |
| 2 | Enero y febrero | Panel: asistente ampliado (borrador por categoría, PRUEBA, continuidad, excepciones), "Cerrar temporada", equipos de pretemporada, reparto en la web e historial |
| 3 | Febrero y marzo | Backend y app: confirmación de continuidad (familias), reparto y altas de PRUEBA para el coordinador, aviso de cambio de temporada |
| 4 | Marzo y abril | Cuotas automáticas al asignar equipo definitivo, apartado de Morosidad, purgas y desactivación de cuentas |
| — | Primavera de 2027 | La app tiene que estar publicada en las tiendas para que las familias puedan confirmar |
| — | Mayo de 2027 | Ensayo completo en DEV con una copia de PRO y lanzamiento del borrador y de las confirmaciones |
| — | 1/7/2027 | Cierre real, con copia de seguridad previa |
