# Retransmisión del Primer Equipo: nombre de retransmisión, alineación y portada (contrato para la app)

Tres cambios sobre la retransmisión en directo. Todo lo nuevo es aditivo salvo lo indicado en el apartado 4.
Textos de los avisos: [avisos.md](avisos.md) (sección 6).

Script de base de datos: `sql/2026-11-08_nombre_retransmision.sql` (se ejecuta **antes** de desplegar).

## 1. Nombre de retransmisión del jugador

`JUGADORES.NOMBRE_RETRANSMISION` (opcional, máximo 40 caracteres), que se rellena en la ficha del jugador del panel
("Nombre en retransmisión"). Sirve para cualquier jugador, también un juvenil o filial que juegue con el Primer Equipo
como invitado.

El **nombre a mostrar en el directo** es siempre el mismo: el de retransmisión si lo tiene (no vacío) y, si no, nombre y
apellidos. Se usa en todo lo que sale hacia fuera del directo:

- el push del gol (`directo.gol.*`),
- los goleadores del push del final (`directo.final.*`),
- el push de la alineación (`directo.alineacion.*`),
- los goleadores de la portada (apartado 3).

No cambian: el autor "Otro" (texto libre) y `"p.p."` (gol en propia puerta del rival, que se traduce al idioma de cada
aviso). En la pantalla de retransmisión se sigue viendo **nombre y apellidos** (`GET /{partidoId}` → `goleadores` y
`eventos[].autor`), para que quien retransmite reconozca al jugador.

## 2. Jugadores elegibles y alineación

### `GET /api/app/partido-en-vivo/{partidoId}/convocados`

Sigue devolviendo una **lista** (las versiones de la app que ya la leen no se rompen). Cada elemento:

```json
{
  "jugadorId": 10,
  "dorsal": 9,
  "nombre": "Pablo Garcia",
  "nombreCompleto": "Pablo Garcia",
  "nombreRetransmision": "Pablito",
  "origen": "CONVOCATORIA"
}
```

- `nombre`: igual que `nombreCompleto` (se mantiene por compatibilidad).
- `nombreRetransmision`: lo que saldrá en los avisos y la portada. Si el jugador no tiene uno propio, es igual que
  `nombreCompleto`. Muestra los dos para que se reconozca al jugador (p. ej. "Pablo Garcia · Pablito").
- `origen` (el mismo en todos los elementos):
  - `CONVOCATORIA`: el partido tiene convocatoria; la lista son los **convocados efectivos**: los del propio equipo sin
    ausencia justificada a ese partido y los invitados de otro equipo en estado `AUTORIZADO` que la familia no ha
    desestimado. Quedan fuera los invitados pendientes (de coordinación o de su entrenador), los rechazados, los
    desestimados y los ausentes justificados.
  - `PLANTILLA`: el partido no tiene convocatoria; la lista es toda la plantilla activa del equipo.
- Orden: por dorsal (los que no tienen, al final) y después por nombre.

Esta misma lista es la que valida el autor de un gol (`POST /{partidoId}/gol-favor` con `jugadorId`) y la alineación.

### `POST /api/app/partido-en-vivo/{partidoId}/alineacion` (nuevo)

Rol `RETRANSMISION`. El partido tiene que ser del Primer Equipo, de hoy y no estar cancelado.

```json
{ "titulares": [12, 10, 15], "suplentes": [11, 20] }
```

Cada lista va **en el orden en que debe salir en el aviso**. `suplentes` es opcional (ausente, `null` o `[]`). Respuesta:
`200` sin cuerpo.

El push lleva los nombres de retransmisión, en el orden recibido, separados por `", "`:

| Caso | Clave | es |
|---|---|---|
| Con suplentes | `directo.alineacion.texto` | `Once inicial: A, B, C` + línea en blanco + `Suplentes: D, E` |
| Sin suplentes | `directo.alineacion.textoSinSuplentes` | `Once inicial: A, B, C` |

Errores `400` (texto claro en el cuerpo):

| Causa | Mensaje |
|---|---|
| `titulares` vacío o ausente | `La alineación necesita al menos un titular` |
| Un jugador repetido (también entre titulares y suplentes) | `Un jugador no puede aparecer dos veces en la alineación` |
| Un id nulo | `Hay un jugador no válido en la alineación` |
| Un jugador que no está en la lista elegible del partido | `Algún jugador no está entre los jugadores del partido` |
| Más titulares de los de la modalidad del equipo (F11 11, F8 8, F5 5) | `Hay más titulares de los permitidos (máximo N)` |
| Partido que no existe / no es del Primer Equipo / no es de hoy / cancelado | el mensaje correspondiente |

`403` si la cuenta no tiene el rol `RETRANSMISION`.

### `POST /api/app/partido-en-vivo/alineacion` (antiguo, se mantiene)

`{"onceInicial": "...", "suplentes": "..."}` (texto libre, ambos obligatorios). Es para las versiones de la app que aún
no eligen jugadores; se quitará cuando la versión mínima obligue a actualizar.

## 3. Portada: `GET /api/public/resultados/primer-equipo`

`ResultadoDTO` gana estos campos (los que ya había no cambian):

| Campo | Tipo | Significado |
|---|---|---|
| `enJuego` | boolean | `true` desde el evento de inicio (no anulado) hasta el final |
| `fase` | string o `null` | `PRIMERA_PARTE`, `DESCANSO`, `SEGUNDA_PARTE` o `FINALIZADO`; `null` si el partido no se ha retransmitido |
| `golesFavor`, `golesContra` | número o `null` | Marcador calculado con los eventos de la retransmisión; `null` si no se ha retransmitido |
| `goleadores` | lista de texto | Goles del Mutxamel, en orden. Solo con el partido retransmitido y `FINALIZADO`; vacía en otro caso |

Detalles:

- Mientras el partido está en juego, **`resultado` sigue vacío** (no se rellena hasta el final, como hasta ahora): el
  marcador en directo va en `golesFavor` y `golesContra`. Con `enJuego` a `true`, `pendienteResultado` es `false`.
- `goleadores`: el nombre de retransmisión de cada gol, agrupando repetidos como `"Pepe (2)"` (en orden de primera
  aparición); el texto libre de "Otro" tal cual; `"Gol en propia puerta del rival"` para `p.p.`; un gol sin autor no se
  añade. Ejemplo: `["Pepe (2)", "Luis Lopez", "Gol en propia puerta del rival", "Rubén G."]`.
- Es un endpoint público sin idioma: los textos van en **castellano**. Si la app manda la cabecera `Accept-Language`
  (`es`, `ca` o `en`; se usa el primer idioma admitido por orden de preferencia), solo cambia el texto del gol en propia
  puerta del rival.
- Deshacer el inicio, un gol o el final se refleja igual en la portada: se calcula siempre con los eventos no anulados.

### Qué partido devuelve

Antes, en cuanto se guardaba el resultado al finalizar, la portada pasaba al próximo partido y los goleadores no se
veían. Ahora (solo en este endpoint):

1. Un partido de **hoy sin resultado** (por empezar o en juego) tiene prioridad.
2. Si el último partido jugado es de **hoy o de ayer** y tiene resultado, se devuelve ese (con sus goleadores) aunque
   haya uno próximo.
3. A partir del día siguiente (dos días después del partido), el próximo, como antes.

El resto de usos del "partido más relevante" (listado de resultados por equipo, `GET /api/public/resultados`) no
cambian.

### Cuántas veces se puede pedir

Es una consulta ligera: una sola lectura de los eventos del partido elegido (con el autor ya unido, sin una consulta por
jugador) y ninguna si el partido es futuro. La app puede pedirla a menudo durante el partido.

## 4. Compatibilidad con versiones antiguas de la app

- `GET /convocados`: sigue siendo una lista y conserva `jugadorId`, `dorsal` y `nombre`; los campos nuevos se ignoran.
  Lo que sí cambia, para las versiones antiguas también, es **quién aparece**: ya no salen los invitados pendientes ni
  rechazados, ni los ausentes justificados.
- `POST /alineacion` (sin `partidoId`): igual que antes.
- `ResultadoDTO`: campos nuevos aditivos.
