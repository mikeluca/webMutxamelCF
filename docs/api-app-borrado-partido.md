# Borrar un partido con todos sus datos

Borrar un partido borra también, en una sola transacción y sin tocar las claves ajenas de la base de datos:
`PARTIDO_LIVE_EVENTOS`, `PARTIDO_ALINEACION`, `PARTIDO_ESTADISTICAS_JUGADOR`, `JUSTIFICACIONES_FALTA_PARTIDO`,
`CONVOCATORIA_JUGADOR` de su convocatoria, `CONVOCATORIAS` y por último `PARTIDOS`. Si un paso falla no se borra
nada. (Si se añade una tabla con `PARTIDO_ID`, hay que añadirla en `PartidoDaoImpl.eliminar`.)

## Permisos

| Quién | Partido sin datos | Partido con datos |
|---|---|---|
| Panel web (`/admin/**`) | sí | sí |
| COORDINADOR / ADMIN_APP (app) | sí | sí |
| ENTRENADOR del equipo (app) | sí | **403** |
| Otro usuario | 403 | 403 |

"Con datos" = convocatoria, resultado, retransmisión (eventos), alineación o estadísticas.
Texto del 403 al entrenador: `Este partido tiene datos (convocatoria, resultado o retransmisión): pide a coordinación que lo borre`.

## Avisos al borrar

1. Siempre, como antes: `InvitadosService.alCancelarPartido` (invitados autorizados o pendientes de su entrenador).
2. Si el partido aún no se ha jugado y tiene convocatoria: aviso "Convocatoria anulada" a los convocados del
   propio equipo (mismo texto que al eliminar una convocatoria; no a quien tenga la ausencia justificada).
   "Ya se jugó" = tiene resultado, o tiene eventos de retransmisión, o su fecha es anterior a hoy. Si ya se jugó, sin avisos.

## Resumen para la confirmación

`GET /api/app/partidos/{id}/resumen-borrado` (JWT; mismos permisos de equipo que borrar: 403 si no gestiona el
equipo, 400 si el partido no existe). Panel: `GET /admin/calendario-resultados/{id}/resumen-borrado` (404 si no existe).

```json
{
  "partidoId": 31,
  "tieneDatos": true,
  "puedeBorrar": true,
  "tieneConvocatoria": true,
  "convocados": 14,
  "tieneResultado": true,
  "eventosRetransmision": 23,
  "tieneAlineacion": true,
  "jugadoresAlineacion": 16,
  "filasEstadisticas": 16
}
```

- `convocados`: filas de la convocatoria, invitados incluidos (0 sin convocatoria).
- `eventosRetransmision`: eventos de la retransmisión, anulados incluidos.
- `puedeBorrar`: `false` solo para un entrenador con `tieneDatos`; la app puede mostrar el mensaje de coordinación
  en vez del diálogo.

Texto de confirmación sugerido (el del panel): "Se borrarán también: la convocatoria (14 convocados), el resultado,
la retransmisión (23 eventos), la alineación y las estadísticas de 16 jugadores. No se puede deshacer." Sin datos:
"¿Eliminar este partido?".

`DELETE /api/app/partidos/{id}` → 204; 403 con el texto de arriba o sin permiso; 400 si no existe.

## Auditoría

Una línea `AUDITORIA borrado de partido` en el log (INFO) con el usuario (`usuarioAppId=` o `usuarioPanel=`), el
partido, el equipo, el rival, el día y los recuentos de lo borrado. Sin nombres de jugadores.
