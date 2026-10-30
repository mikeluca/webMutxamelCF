# API de la app para la convocatoria de jugadores de otros equipos

Contrato de los endpoints y del `data` de los push. El diseño y el flujo están en `convocatoria-invitados.md`.
Todos son `/api/app/**`: llevan `Authorization: Bearer <JWT>` y devuelven JSON.
El equipaje de la convocatoria (campos `equipacionId`, `equipacionMochilaId`, `equipajeIndicado` y `equipacion`/`equipacionMochila` en las respuestas) está en `api-app-equipaje-convocatoria.md`.

Errores comunes (cuerpo = texto plano con el motivo, salvo el 409):

| Código | Cuándo |
|---|---|
| 401 | Sin token o usuario no válido |
| 403 | No tienes permiso (no gestionas ese equipo, no eres coordinador, no estás vinculado con el jugador) |
| 400 | Datos no válidos o acción que ya no procede (mensaje en el cuerpo) |
| 409 | El jugador ya está autorizado como invitado ese día. Cuerpo JSON con `codigo` (ver §5) |

## 1. Estados de una invitación

| `estadoInvitacion` | Significado | Qué muestra la app al entrenador que convocó |
|---|---|---|
| `PENDIENTE_COORDINACION` | Esperando el visto bueno de coordinación | "Pendiente de coordinación" |
| `PENDIENTE_ENTRENADOR` | Aprobado, pero su entrenador lo tiene convocado ese día y tiene que liberarlo | "Pendiente de que su entrenador lo autorice" |
| `AUTORIZADO` | Puede jugar. Sale como un convocado normal | (aparece en la lista, con la marca de invitado) |
| `RECHAZADO` | Coordinación lo ha rechazado (`motivoRechazo` opcional) | "Rechazado": elegir a otro jugador |

Transiciones:

```
alta ──(convoca un coordinador)──────────────────────────┐
alta ──────────► PENDIENTE_COORDINACION ──rechaza──► RECHAZADO
                         │ aprueba (o ya aprobado por el alta)
                         ▼
        su equipo no juega ese día ─────────────────────► AUTORIZADO
        juega y no lo tiene convocado ──────────────────► AUTORIZADO (+ aviso informativo a su entrenador)
        juega y su entrenador lo tiene convocado ───────► PENDIENTE_ENTRENADOR ──liberar──► AUTORIZADO
```

- "Su equipo juega" = tiene un partido **no cancelado** el mismo día.
- A las 12 horas de pasar a `PENDIENTE_ENTRENADOR` se avisa a coordinación, una sola vez; la invitación sigue pendiente.
- La familia puede desestimar a un invitado `AUTORIZADO` (§3.6): sigue `AUTORIZADO`, con una justificación de ausencia.
- Se borra la fila (no hay estado "cancelado") si el entrenador lo quita, se borra la convocatoria, se cancela o borra el partido o el partido cambia de fecha (§1.1).

### 1.1 Si cambia la fecha de un partido

Cuando cambia el **día** de un partido (desde la app o el panel con "actualizar partido", o por un aplazamiento detectado al importar de la FFCV, §1.2), en la misma transacción que el cambio:

- **El partido es el de destino de invitaciones:** las que no estén `RECHAZADO` (`PENDIENTE_COORDINACION`, `PENDIENTE_ENTRENADOR` o `AUTORIZADO`) se anulan como al quitar al invitado: se borra la fila y se avisa con `INVITACION_ANULADA` según el §8 (a la familia y al jugador solo si estaba autorizado; al cuerpo técnico del equipo de origen si estaba autorizado o pendiente de él), con la **fecha antigua** en `fechaPartido`. Además, a cada usuario que invitó se le manda **un** aviso `INVITACION_FECHA_CAMBIADA` con los jugadores anulados, para que, si quiere, los vuelva a invitar para la fecha nueva. Las `RECHAZADO` no se tocan.
- **El partido es el del equipo de origen de invitaciones** `PENDIENTE_ENTRENADOR` o `AUTORIZADO` cuyo partido de destino cae en la fecha antigua o en la nueva: se vuelve a aplicar la regla del solapamiento a cada una (día del partido de destino):
  - su equipo ya no juega ese día, o juega pero ya no lo tiene convocado ese día: si estaba `PENDIENTE_ENTRENADOR` pasa a `AUTORIZADO` (avisos de autorización del §8; si su equipo sigue jugando ese día, el de su entrenador es `INVITACION_INFORMATIVA`); si ya estaba `AUTORIZADO`, no cambia;
  - su equipo juega ese día y lo tiene convocado: si estaba `AUTORIZADO` pasa a `PENDIENTE_ENTRENADOR` (arranca de nuevo el plazo de 12 horas): `INVITACION_LIBERAR` a su entrenador y `INVITACION_SOLAPAMIENTO` a su familia y al jugador; si ya estaba `PENDIENTE_ENTRENADOR`, no cambia.
- Si el partido cambia de hora pero no de día, no pasa nada. El convocar o no a un jugador sigue ligado a la convocatoria, que viaja con su partido.

### 1.2 Aplazamientos en la importación de la FFCV

La importación identificaba cada partido por equipo y día, así que un aplazamiento salía como un partido nuevo más un "desaparecido". Ahora, un partido FFCV **desaparecido** y una fila **nueva** del mismo equipo y la **misma jornada** se tratan como el mismo partido movido de día, siempre que el existente no esté cancelado ni tenga resultado y que su fecha antigua caiga dentro del rango de fechas del archivo:

- La previsualización lo muestra como `ACTUALIZADO` con el cambio `Fecha: 18/10/2026 → 17/10/2026` en `cambios`, y deja de salir en `desaparecidos`.
- Al confirmar se actualiza el partido existente (su `DIA`, además de hora, campo, rival, jornada y local), de modo que conserva su convocatoria, sus invitados y sus estadísticas, y se aplica el §1.1.
- Los partidos que desaparecen sin una fila nueva de la misma jornada siguen igual que antes: no se tocan y salen como aviso.

## 2. Pantalla de la convocatoria (entrenador del equipo del partido)

### GET `/api/app/partidos/{partidoId}/invitables/equipos`
Los equipos de los que este partido puede invitar jugadores. 403 si no gestionas el equipo del partido.
```json
[ { "equipoId": 2, "nombre": "Cadete B", "categoria": "Cadete", "grupo": "B" },
  { "equipoId": 3, "nombre": "Infantil A", "categoria": "Infantil", "grupo": "A" } ]
```
Reglas (siempre las aplica el servidor; la app solo pinta la lista):
- Equipo A de una categoría formativa: B, C y D de su categoría y cualquier equipo de la categoría inmediatamente inferior (según el orden de `CATEGORIAS_EDAD`). Orden de nivel A > C > B > D, solo se sube. Filial: cualquier equipo de Juvenil. Primer Equipo: Filial y cualquier equipo de Juvenil.
- Nunca: equipos de pretemporada, de grupo `Esc` o `Fem`, ni Veteranos (ni como destino ni como origen).

### GET `/api/app/partidos/{partidoId}/invitables/equipos/{equipoId}/jugadores`
Los jugadores invitables de ese equipo: activos (sin baja, no de prueba y con equipo definitivo en la temporada activa), por dorsal. 400 si el equipo no es uno de los permitidos para este partido.
```json
[ { "jugadorId": 1, "nombre": "Ana Pérez", "dorsal": 7, "yaInvitado": true,  "bloqueado": false, "motivoBloqueo": null },
  { "jugadorId": 2, "nombre": "Biel Gil",  "dorsal": 9, "yaInvitado": false, "bloqueado": true,
    "motivoBloqueo": "Ya está invitado con Cadete C el 14/11/2026" } ]
```
`yaInvitado`: ya está invitado (sin rechazar) a la convocatoria de este partido. `bloqueado`: ya está **autorizado** como invitado con otro equipo ese día; no se le puede invitar (409 `JUGADOR_YA_INVITADO_ESE_DIA` si se intenta).

## 3. Convocatoria (cambios en los endpoints existentes)

### POST `/api/app/convocatorias` y PUT `/api/app/convocatorias/{id}`
Cuerpo igual que hasta ahora, más un campo opcional:
```json
{ "partidoId": 10, "horaConvocatoria": "10:00", "lugarConvocatoria": "Vestuarios",
  "jugadoresIds": [6, 8, 9], "invitadosIds": [1, 3] }
```
- `jugadoresIds`: solo jugadores del **propio** equipo (como siempre).
- `invitadosIds`: jugadores de otros equipos.
  - En el POST, los invitados que se dan de alta.
  - En el PUT, **`null` o ausente = no se tocan**; una lista = la convocatoria queda con exactamente esos invitados (los que faltan se quitan, `[]` quita a todos).
  - Máximo **3** invitados por convocatoria, sin contar los rechazados ni los que la familia ha desestimado (400 si se pasa).
- Un invitado nuevo queda `PENDIENTE_COORDINACION` y se avisa a coordinación. Si quien guarda es **coordinador o ADMIN_APP**, queda aprobado al momento (y pasa a `AUTORIZADO` o `PENDIENTE_ENTRENADOR` según el §1). Reinvitar a un `RECHAZADO` lo vuelve a poner `PENDIENTE_COORDINACION`.
- 400: equipo de origen no permitido, jugador que no está activo, jugador del propio equipo, jugador con la ausencia justificada a ese partido, más de 3 invitados. Cambiar el `partidoId` de una convocatoria que tiene invitados (sin rechazar) también es 400: hay que quitarlos antes. Cambiar la **fecha del propio partido** sí se puede: anula las invitaciones (§1.1).
- 409 (§5): `JUGADOR_YA_INVITADO_ESE_DIA` al invitar a quien ya está autorizado con otro equipo ese día; `JUGADOR_INVITADO_ESE_DIA` si alguno de `jugadoresIds` (los que entran) ya está autorizado como invitado con otro equipo ese día (su entrenador no puede convocarlo).

### Respuesta de la convocatoria (POST, PUT, GET)
Cada elemento de `jugadores` gana estos campos (`invitado = false` y el resto `null` en los jugadores del propio equipo):
```json
{ "jugadorId": 1, "jugador": "Ana Pérez", "ausenciaJustificada": false, "motivoAusencia": null,
  "invitado": true, "equipoOrigenId": 2, "equipoOrigen": "Cadete B",
  "estadoInvitacion": "AUTORIZADO", "motivoRechazo": null }
```
- Primero van los que ya juegan (propios e invitados `AUTORIZADO`); después, los invitados `PENDIENTE_*` y `RECHAZADO`.
- `motivoAusencia` de un **invitado** desestimado solo viene para el usuario que lo invitó; para el resto del cuerpo técnico es `null` (con `ausenciaJustificada = true`).

### 3.6 Desestimar a un invitado: POST `/api/app/partidos/{partidoId}/invitados/{jugadorId}/desestimar`
Lo hace la familia o el propio jugador (usuario vinculado con él).
```json
{ "motivo": "Tiene un cumpleaños familiar" }
```
- Es una **justificación de ausencia** a ese partido (`JUSTIFICACIONES_FALTA_PARTIDO`): motivo de 3 a 500 caracteres y mismo plazo (hasta la hora de convocatoria). 201 con la justificación (`{partidoId, jugadorId, jugador, motivo, fechaCreacion}`).
- Solo para un invitado `AUTORIZADO` en ese partido (400 si no). Sigue en la lista con su motivo.
- Se avisa solo a quien lo convocó (`INVITACION_DESESTIMADA`), para que convoque a otro: el motivo va en la bandeja y **nunca en el push**.
- Retirarla: `DELETE /api/app/partidos/{partidoId}/justificaciones/{jugadorId}` (el mismo de siempre).
- La familia de un invitado `AUTORIZADO` también puede usar `POST /api/app/partidos/{id}/justificaciones` y `GET /api/app/partidos/{id}/convocatoria?jugadorId=` (el detalle de la convocatoria del otro equipo); un invitado sin autorizar recibe 403 / 400.

## 4. Coordinación (rol COORDINADOR o ADMIN_APP)

### GET `/api/app/invitaciones/pendientes`
Pendientes de coordinación **y** pendientes de su entrenador, de partidos no cancelados desde hoy, por fecha del partido. 403 si no eres coordinador.
```json
[ {
    "id": 31, "estado": "PENDIENTE_ENTRENADOR", "convocatoriaId": 100, "partidoId": 10,
    "jugadorId": 1, "jugador": "Ana Pérez",
    "equipoOrigenId": 2, "equipoOrigen": "Cadete B",
    "equipoDestinoId": 1, "equipoDestino": "Cadete A",
    "fechaPartido": "2026-11-14", "horaPartido": "11:00", "rival": "Pego",
    "usuarioInvitaId": 7, "convoca": "Cuerpo técnico de Cadete A",
    "fechaSolicitud": "2026-11-12T10:00:00", "fechaPeticionEntrenador": "2026-11-12T10:05:00",
    "plazoVencido": false,
    "jugadorConvocadoEnOrigen": true,
    "solapamiento": [ { "partidoId": 11, "hora": "09:30", "rival": "Denia" } ],
    "otraInvitacion": { "convocatoriaJugadorId": 40, "equipoDestinoId": 6, "equipoDestino": "Cadete C",
                        "partidoId": 13, "horaPartido": "12:00", "rival": "Javea", "estado": "PENDIENTE_COORDINACION" }
} ]
```
- `id`: el id de la invitación para aprobar, rechazar y liberar.
- `estado`: `PENDIENTE_COORDINACION` o `PENDIENTE_ENTRENADOR`. `fechaPeticionEntrenador` solo en este último; `plazoVencido = true` si han pasado 12 horas desde entonces (la app lo marca).
- `solapamiento`: partidos no cancelados de su equipo de origen ese día (lista vacía = no hay). `jugadorConvocadoEnOrigen`: su entrenador lo tiene convocado en alguno.
- `convoca`: nombre de quien invitó o, si su cuenta no lo tiene, "Cuerpo técnico de {equipo de destino}" (`usuarioInvitaId` lo identifica).
- `otraInvitacion`: **su otra invitación ese día** en otro partido (`null` si no hay); la app la marca como "tiene otra invitación ese día" con el equipo. Si esa otra está `AUTORIZADO`, esta ya no se puede aprobar (409 `JUGADOR_YA_INVITADO_ESE_DIA`).

### POST `/api/app/invitaciones/{id}/aprobar`
Sin cuerpo. Vale el de cualquier coordinador. 200 con la invitación, ya en su estado final (`AUTORIZADO` o `PENDIENTE_ENTRENADOR`):
```json
{ "id": 31, "convocatoriaId": 100, "partidoId": 10, "jugadorId": 1, "jugador": "Ana Pérez",
  "equipoOrigenId": 2, "equipoOrigen": "Cadete B", "estado": "AUTORIZADO", "motivoRechazo": null }
```
400: la invitación ya no está pendiente de coordinación, o el partido está cancelado. 409 `JUGADOR_YA_INVITADO_ESE_DIA`.

### POST `/api/app/invitaciones/{id}/rechazar`
Cuerpo opcional `{ "motivo": "Ese día juega con su equipo" }` (hasta 500 caracteres). 200 con la invitación en `RECHAZADO`. Se avisa a quien invitó (el motivo, en la bandeja).

### POST `/api/app/invitaciones/{id}/liberar`
Sin cuerpo. Solo para invitaciones `PENDIENTE_ENTRENADOR` (400 si no). Lo hace:
- **el cuerpo técnico del equipo de origen**: el jugador se quita de la convocatoria de su equipo de ese día y la invitación pasa a `AUTORIZADO`;
- **coordinación** (COORDINADOR o ADMIN_APP), desde su lista de pendientes: hace lo mismo y además avisa al cuerpo técnico del equipo de origen (`INVITACION_LIBERADA_COORDINACION`).

La familia del jugador recibe dos avisos: que ya no está convocado con su equipo ese día y que sí lo está con el otro. 403 si no gestionas el equipo de origen. 409 `JUGADOR_YA_INVITADO_ESE_DIA` si entretanto quedó autorizado con otro equipo (no se hace nada).

Liberar **sin el endpoint**: si el entrenador de origen quita al jugador de su propia convocatoria (PUT de la convocatoria o borrarla), las invitaciones `PENDIENTE_ENTRENADOR` de esos jugadores ese día pasan solas a `AUTORIZADO`. Con ese push (`INVITACION_LIBERAR`) la app lleva al entrenador a su convocatoria (`convocatoriaOrigenId`) para que lo quite.

## 5. Error 409

```json
{ "codigo": "JUGADOR_INVITADO_ESE_DIA", "mensaje": "Ana ya está convocado como invitado con Cadete A el 14/11/2026: no puedes convocarlo ese día.",
  "jugadorId": 1, "equipoDestinoId": 1, "equipoDestino": "Cadete A", "fecha": "2026-11-14" }
```

| `codigo` | Cuándo | Dónde |
|---|---|---|
| `JUGADOR_INVITADO_ESE_DIA` | Se intenta convocar (en `jugadoresIds`) a un jugador que ya está **autorizado** como invitado con otro equipo ese día. Aplica a su entrenador y a cualquiera que lo convoque. | POST/PUT convocatoria |
| `JUGADOR_YA_INVITADO_ESE_DIA` | Se intenta invitar, aprobar o liberar a un jugador que ya está **autorizado** como invitado con otro equipo ese día. Si la otra invitación solo está pendiente, sí se permite. | POST/PUT convocatoria (`invitadosIds`), aprobar, liberar |

`equipoDestino` es el equipo con el que ya está autorizado. El mensaje está listo para mostrarlo, pero la app debe decidir por `codigo`.

## 6. Calendario y Mis partidos de la familia y del jugador

`GET /api/app/calendario?equipoId=1&desde=…&hasta=…&jugadorId=5` ya devuelve, además de los partidos del equipo, los partidos de **otros equipos** en los que ese jugador está **autorizado** como invitado (solo `AUTORIZADO`, no cancelados), mezclados y ordenados por fecha y hora. Cada partido de ese tipo lleva:

| Campo | Valor |
|---|---|
| `invitado` | `true` (en el resto, `false`) |
| `convocado` | `true` |
| `equipoId`, `equipo` | el equipo del partido (el de destino) |
| `ausenciaJustificada`, `motivoAusencia`, `puedeJustificar`, `limiteJustificacion` | igual que en un partido propio; `puedeJustificar` es lo que habilita "Desestimar" (§3.6) |

La convocatoria de ese partido (`GET /api/app/partidos/{id}/convocatoria?jugadorId=`) la ve el invitado autorizado igual que un jugador del equipo (`convocado = true`).

## 7. Retransmisión y estadísticas

- **Retransmisión:** la lista de jugadores del partido en directo (la que se usa para elegir al autor de un gol) incluye a los invitados `AUTORIZADOS`. Los pendientes o rechazados no salen.
- **Estadísticas por jugador del partido:** el roster del partido incluye a los invitados autorizados (solo si el equipo del partido es F11, como siempre).
- **Estadísticas del equipo** (`/api/app/equipos/{id}/estadisticas` y `/tarjetas`): el invitado sale en las del equipo **con el que jugó**, con `invitado: true` (en `goleadores`, `asistentes` y `jugadores` de tarjetas). Solo cuenta lo que hizo en partidos de ese equipo. No se suma a las de su propio equipo ni a su resumen de temporada.
- **Sanciones por tarjetas:** las amarillas se cuentan por equipo del partido (cada equipo juega su competición). Una amarilla como invitado no se suma a las de su equipo ni al revés; el aviso de "a una amarilla" o "posible sanción" va al cuerpo técnico del equipo del partido.

## 8. Push: `data` para abrir la pantalla correcta

Todos los valores son texto. Cada push crea también la notificación en la bandeja (mismo título y mensaje, salvo el motivo, que solo va en la bandeja). Se respetan las preferencias de avisos del usuario. Ningún push lleva el motivo de la familia ni el de coordinación.

| `tipo` | A quién | Cuándo | Claves de `data` (además de `tipo`) | La app abre |
|---|---|---|---|---|
| `INVITACION_PENDIENTE` | Todos los coordinadores y ADMIN_APP (menos quien invita) | Un entrenador invita a un jugador | `invitacionId`, `convocatoriaId`, `partidoId`, `jugadorId`, `fechaPartido` | Lista de pendientes (§4) |
| `INVITACION_RECHAZADA` | Quien invitó | Coordinación rechaza | ídem | La convocatoria (`convocatoriaId`), para elegir a otro |
| `INVITACION_LIBERAR` | Cuerpo técnico del equipo de origen (sin entrenador, coordinación) | Aprobada, pero tiene a su jugador convocado ese día | ídem + `convocatoriaOrigenId` | Su convocatoria (`convocatoriaOrigenId`) para quitarlo, o `POST /invitaciones/{invitacionId}/liberar` |
| `INVITACION_INFORMATIVA` | Cuerpo técnico del equipo de origen | Autorizado: su equipo juega ese día pero no lo tenía convocado | `invitacionId`, `convocatoriaId`, `partidoId`, `jugadorId`, `fechaPartido` | Calendario en `fechaPartido` |
| `INVITACION_AUTORIZADA` | Cuerpo técnico del equipo de origen | Autorizado sin solapamiento | ídem | Calendario en `fechaPartido` |
| `INVITACION_PLAZO` | Todos los coordinadores y ADMIN_APP | 12 horas sin liberar (una sola vez) | `invitacionId`, `partidoId`, `jugadorId`, `fechaPartido` | Lista de pendientes (§4) |
| `INVITACION_LIBERADA_COORDINACION` | Cuerpo técnico del equipo de origen | Coordinación libera al jugador | `invitacionId`, `convocatoriaId`, `partidoId`, `jugadorId`, `fechaPartido` | Su convocatoria / calendario |
| `INVITACION_DESESTIMADA` | Quien invitó | La familia desestima (motivo solo en la bandeja) | `convocatoriaId`, `partidoId`, `jugadorId` | La convocatoria, para elegir a otro |
| `INVITACION_ANULADA` | Familia y jugador (solo si estaba autorizado) y cuerpo técnico del equipo de origen (si estaba autorizado o pendiente de él) | Se quita al invitado, se borra la convocatoria, se cancela o borra el partido o **cambia su fecha** (§1.1; `fechaPartido` es entonces la fecha antigua) | familia: `partidoId`, `fechaPartido`; cuerpo técnico: ídem + `invitacionId`, `jugadorId` (sin `convocatoriaId`: puede no existir ya) | Calendario en `fechaPartido` |
| `INVITACION_FECHA_CAMBIADA` | Quien invitó (uno por usuario) | El partido de destino cambia de fecha y se anulan sus invitaciones (§1.1) | `convocatoriaId`, `partidoId`, `fechaPartido` (la nueva), `fechaAnterior`, `jugadorIds` (los anulados, separados por comas) | La convocatoria, para volver a invitarlos |
| `INVITACION_SOLAPAMIENTO` | Familia y jugador | Un invitado `AUTORIZADO` vuelve a `PENDIENTE_ENTRENADOR` porque su equipo cambia un partido y coincide con ese día (§1.1) | `invitacionId`, `convocatoriaId`, `partidoId`, `jugadorId`, `fechaPartido` | El calendario en `fechaPartido` |
| `CONVOCATORIA` | Familia y jugador | Pasa a `AUTORIZADO` (aviso de convocatoria normal: "Has sido convocado con Cadete A…"), o deja de estar convocado con su equipo al liberarlo | `convocatoriaId`, `partidoId`, `fechaPartido` (al liberarlo, los de la convocatoria de su equipo) | El detalle de la convocatoria o el calendario, como siempre |

Solo se avisa a la familia, al jugador y a su entrenador cuando el invitado queda **autorizado** (nunca en `PENDIENTE_*`). Quien provoca el cambio no recibe su propio aviso.

## 9. Plazo de 12 horas

Un job corre cada 15 minutos (Europe/Madrid) y avisa a coordinación de las invitaciones `PENDIENTE_ENTRENADOR` con más de 12 horas desde `fechaPeticionEntrenador`. Se marca `AVISO_12H_ENVIADO = 1` aunque no hubiera a quién avisar, para no repetirlo.
