# Borrar un partido

## Desde la app: solo partidos sin datos

`DELETE /api/app/partidos/{id}` (cualquier rol con permiso sobre el equipo: ENTRENADOR del equipo, COORDINADOR, ADMIN_APP).

- Partido **sin datos** → 204 y se borra. Antes se avisa a los invitados, como siempre (`InvitadosService.alCancelarPartido`).
- Partido **con datos** (convocatoria, resultado, retransmisión, alineación o estadísticas) → **409** y no se borra nada. Texto:
  `Este partido tiene datos (convocatoria, resultado o retransmisión). Solo se puede borrar desde el panel web.`
- Sin permiso sobre el equipo → 403; partido inexistente → 400.

El rol no cambia nada: ni COORDINADOR ni ADMIN_APP pueden borrar desde la app un partido con datos. No hay endpoint de
resumen para la app.

## Desde el panel web: borrado en cascada

Solo `PartidoService.eliminarComoAdmin` (panel `/admin/calendario-resultados/{id}/eliminar`) borra un partido con datos,
en una sola transacción y sin tocar las claves ajenas: `PARTIDO_LIVE_EVENTOS`, `PARTIDO_ALINEACION`,
`PARTIDO_ESTADISTICAS_JUGADOR`, `JUSTIFICACIONES_FALTA_PARTIDO`, `CONVOCATORIA_JUGADOR` de su convocatoria,
`CONVOCATORIAS` y `PARTIDOS`. Si un paso falla no se borra nada. (Si se añade una tabla con `PARTIDO_ID`, hay que
añadirla en `PartidoDaoImpl.eliminar`.)

Antes de borrar: `InvitadosService.alCancelarPartido` y, si el partido aún no se ha jugado y tiene convocatoria, el
aviso "Convocatoria anulada" a los convocados del propio equipo (no a quien tenga la ausencia justificada). "Ya se
jugó" = tiene resultado, o eventos de retransmisión, o su fecha es anterior a hoy: entonces, sin avisos.

El diálogo de confirmación del panel pide antes `GET /admin/calendario-resultados/{id}/resumen-borrado`:

```json
{ "partidoId": 31, "tieneDatos": true, "puedeBorrar": true, "tieneConvocatoria": true, "convocados": 14,
  "tieneResultado": true, "eventosRetransmision": 23, "tieneAlineacion": true, "jugadoresAlineacion": 16,
  "filasEstadisticas": 16 }
```

(`puedeBorrar` es siempre `true` en el panel.) 404 si el partido no existe.

## Auditoría

Línea INFO `AUDITORIA borrado de partido` con el usuario (`usuarioAppId=` o `usuarioPanel=`), el partido, el equipo, el
rival, el día y los recuentos de lo borrado. Sin nombres de jugadores.
