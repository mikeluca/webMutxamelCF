# Convocatoria de jugadores de otros equipos: diseño (30/10/2026)

Un entrenador puede convocar para su partido a jugadores de otros equipos del club, con unas reglas de nivel, el visto bueno de coordinación y, si su equipo juega el mismo día, la liberación de su propio entrenador.

## 1. Quién puede ser invitado

| Equipo del partido (destino) | Puede invitar a jugadores de (origen) |
|---|---|
| Equipo A de una categoría formativa | B, C y D de su categoría; cualquier equipo de la categoría inmediatamente inferior |
| Equipo C | B y D de su categoría; cualquier equipo de la categoría inferior |
| Equipo B | D de su categoría; cualquier equipo de la categoría inferior |
| Filial | cualquier equipo de Juvenil |
| Primer Equipo | Filial y cualquier equipo de Juvenil |
| Veteranos | nadie (y sus jugadores no pueden ser invitados) |

- **Orden de nivel dentro de una categoría:** A > C > B > D. Solo se sube: B → A, B → C, C → A, D → B / C / A. Si no existe C, solo B → A.
- **Categoría inmediatamente inferior:** según el ORDEN de CATEGORIAS_EDAD (Escuelita → Prebenjamín → Benjamín → Alevín → Infantil → Cadete → Juvenil). Desde la inferior vale cualquier equipo hacia cualquier equipo.
- **No participan:** equipos de grupo Esc o Fem, equipos de pretemporada, Veteranos.
- **Solo jugadores activos:** sin baja, que no sean PRUEBA y con equipo definitivo en la temporada activa.
- **Cualquier partido:** competición y amistosos.
- **Máximo 3 invitados por convocatoria.** No cuentan los rechazados por coordinación ni los que la familia ha desestimado.

## 2. Flujo

1. **El entrenador del partido los añade.** Debajo de sus jugadores, la convocatoria tiene "Jugadores de otros equipos": muestra los equipos permitidos y, en cada uno, sus jugadores. El invitado queda **pendiente de coordinación**. Al guardar, el resto de la convocatoria se publica como siempre.
2. **Coordinación da el visto bueno.** Se avisa a todos los coordinadores (y ADMIN_APP), y vale el de cualquiera. Si quien convoca es coordinador, queda aprobado automáticamente.
   - **Rechazo:** se avisa al entrenador que convocó para que elija a otro jugador.
3. **Solapamiento.** Hay solapamiento si su equipo de origen juega (partido no cancelado) **el mismo día**.
   - **Su entrenador ya lo ha convocado para ese partido:** el invitado queda **pendiente de su entrenador**. Su entrenador recibe un aviso que explica el solapamiento y le lleva a su propia convocatoria para liberarlo, es decir, quitarlo de ella. Mientras tanto, el entrenador que lo invitó ve en su lista "Pendiente de que su entrenador lo autorice".
   - **Su equipo juega ese día, pero su entrenador no lo ha convocado (o aún no hay convocatoria):** el jugador queda libre y autorizado, y su entrenador recibe un aviso informativo.
   - **Plazo:** si a las **12 horas de pedírselo** a su entrenador no lo ha liberado, se avisa a los coordinadores (una sola vez).
4. **Autorizado.** Cuando coordinación aprueba y no hay solapamiento, o su entrenador lo libera, el invitado queda autorizado y **solo entonces** se avisa a su familia, al jugador y a su propio entrenador. Aparece normal en la lista de convocados.
5. **Su entrenador no puede convocarlo después.** Si el jugador ya está autorizado con otro equipo un día, su propio entrenador no puede convocarlo para un partido de ese mismo día: la app se lo explica.
6. **La familia puede desestimarlo.** Lo hace con un motivo, hasta la hora de convocatoria, igual que una justificación de ausencia: el jugador sigue en la lista con el motivo. Solo el entrenador que lo convocó ve ese motivo, y recibe un aviso que le permite convocar a otro jugador en su lugar. El motivo nunca va en el texto del push.
7. **Si se cancela algo.** Si el partido se cancela, o se borra la convocatoria o se quita al invitado, se avisa a su familia (si ya estaba autorizado) y a su entrenador.

## 3. Dónde se ve

- **Calendario y Mis partidos de la familia y del jugador:** el partido del otro equipo aparece solo cuando el invitado está **autorizado**.
- **Retransmisión:** el invitado autorizado se puede elegir como autor de un gol.
- **Estadísticas:**
  - Sus goles, asistencias, tarjetas y partidos con el otro equipo aparecen **solo en las estadísticas del equipo con el que jugó**, marcado como "invitado", y no se suman a su propio equipo.
  - Los avisos de sanción por tarjetas cuentan por la competición del partido.
  - El partido registra las estadísticas por jugador si el equipo del partido es F11.

## 4. Modelo (propuesta)

Columnas nuevas en `CONVOCATORIA_JUGADOR`, todas nulas para los jugadores del propio equipo:

| Columna | Contenido |
|---|---|
| `EQUIPO_ORIGEN_ID` | Equipo habitual del invitado (FK a EQUIPO) |
| `ESTADO_INVITACION` | PENDIENTE_COORDINACION, PENDIENTE_ENTRENADOR, AUTORIZADO, RECHAZADO |
| `FECHA_SOLICITUD` | Cuándo se pidió |
| `USUARIO_COORDINADOR_ID` y `FECHA_COORDINACION` | Quién dio el visto bueno o rechazó, y cuándo |
| `FECHA_PETICION_ENTRENADOR` | Cuándo se pidió la liberación a su entrenador |
| `AVISO_12H_ENVIADO` | Si ya se avisó a coordinación por el plazo de 12 horas |
| `FECHA_AUTORIZACION` | Cuándo quedó autorizado |
| `MOTIVO_RECHAZO` | Motivo de coordinación, opcional |

La desestimación de la familia reutiliza `JUSTIFICACIONES_FALTA_PARTIDO`.

## 5. Pendiente de decidir más adelante

Nada. Si aparece algún caso nuevo durante la implementación, se pregunta.
