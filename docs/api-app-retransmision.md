# Retransmisión del Primer Equipo: nombre de retransmisión, alineación y portada (contrato para la app)

Tres cambios sobre la retransmisión en directo. Todo lo nuevo es aditivo salvo lo indicado en el apartado 4.
Textos de los avisos: [avisos.md](avisos.md) (sección 6).

Scripts de base de datos (se ejecutan **antes** de desplegar): `sql/2026-11-08_nombre_retransmision.sql` y, para el
apartado 5 (formaciÃ³n, cambios, tarjetas y ficha pÃºblica), `sql/2026-11-12_ficha_partido.sql`.

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
{ "titulares": [12, 10, 15], "suplentes": [11, 20], "formacion": "4-4-2", "avisar": true }
```

Cada lista va **en el orden en que se eligieron** (y en el que sale en el aviso). `suplentes` es opcional (ausente,
`null` o `[]`). `formacion` es opcional y `avisar` solo manda el push si es `true` (**ausente = no avisar**; la app lo
marca por defecto la primera vez). Se guarda la alineaciÃ³n (titulares y suplentes con su orden, y la formaciÃ³n) y, si
se vuelve a enviar, **reemplaza** a la anterior. Respuesta: `200` con el estado del partido (el mismo de
`GET /{partidoId}`, con `alineacion`; ver apartado 5).

**FormaciÃ³n.** Lista cerrada segÃºn la modalidad del equipo (sin modalidad se trata como F11):

| Modalidad | Formaciones |
|---|---|
| F11 | `4-4-2`, `4-3-3`, `4-2-3-1`, `4-5-1`, `4-1-4-1`, `3-5-2`, `3-4-3`, `5-3-2`, `5-4-1` |
| F8 | `3-3-1`, `3-2-2`, `2-3-2`, `2-4-1`, `3-1-3`, `4-2-1` |
| F5 | `2-2`, `1-2-1`, `2-1-1`, `1-1-2`, `3-1` |

Con formaciÃ³n, los titulares deben ser **1 + la suma de la formaciÃ³n** (el portero y los de campo). **PosiciÃ³n en el
campo = orden de elecciÃ³n**: el 1.Âº es el portero; despuÃ©s, las lÃ­neas de la formaciÃ³n de atrÃ¡s adelante y, dentro de
cada lÃ­nea, de izquierda a derecha. Con `4-2-3-1`: 2.Âº-5.Âº defensas (fila 2, columnas 1-4), 6.Âº-7.Âº (fila 3), 8.Âº-10.Âº
(fila 4) y 11.Âº (fila 5). Sin formaciÃ³n no hay posiciones.

No se puede reenviar la alineaciÃ³n cuando ya hay algÃºn cambio registrado (`400`).

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

- `POST /{partidoId}/alineacion` ahora devuelve el estado (antes `200` sin cuerpo) y acepta `formacion` y `avisar`. Una
  versión antigua que no manda `avisar` deja de generar el push de la alineación (solo avisa con `avisar: true`).
- El estado del partido (`GET /{partidoId}` y todas las respuestas de eventos) gana el campo `alineacion` y los eventos
  los campos `jugadorEntraId`, `jugadorEntra` y `dobleAmarilla`; los tipos de evento `CAMBIO`, `TARJETA_AMARILLA` y
  `TARJETA_ROJA` son nuevos.

## 5. Formación, cambios, tarjetas y ficha pública

### Estado con la alineación (`GET /{partidoId}`)

Devuelve la alineación guardada y quién está en el campo, para que la app ofrezca solo cambios válidos
(`GET /convocados` no cambia: sigue siendo la lista elegible).

```json
"alineacion": {
  "formacion": "4-4-2",
  "titulares": [100, 101, 102],
  "suplentes": [111, 112],
  "enElCampo": [101, 102, 111],
  "suplentesDisponibles": [112],
  "expulsados": []
}
```

`titulares` y `suplentes` son ids por orden de elección; `enElCampo`, los que juegan ahora (titulares no sustituidos y
suplentes que entraron y no han salido, sin los expulsados); `suplentesDisponibles`, los suplentes que aún no han
entrado. `alineacion` es `null` si no se ha enviado ninguna. En `eventos[]`, un cambio lleva `jugadorId`/`autor` (el que
sale) y `jugadorEntraId`/`jugadorEntra` (el que entra); una tarjeta, el amonestado en `jugadorId`/`autor`.

### `POST /api/app/partido-en-vivo/{partidoId}/cambio`

Rol `RETRANSMISION`; partido de hoy, no cancelado. Sin push. Respuesta: el estado.

```json
{ "jugadorSaleId": 100, "jugadorEntraId": 111, "minuto": 60 }
```

Válido en la primera parte, el descanso (con el minuto del descanso; sin `minuto`, la duración de la parte) y la segunda
parte. `minuto` entre 0 y 150; sin él, lo calcula el servidor (como en los goles).

- **Con alineación guardada:** sale alguien que está en el campo (titular no sustituido, o que entró y no ha salido, y
  sin roja) y entra un suplente que no ha entrado.
- **Sin alineación guardada:** los dos de la lista elegible y distintos.

Un cambio idéntico reintentado en menos de 15 segundos no se registra dos veces.

Errores `400`: `Indica quién sale y quién entra`, `El jugador que sale y el que entra deben ser distintos`,
`Algún jugador no está entre los jugadores del partido`, `El jugador que sale no está en el campo`,
`El jugador que sale ya ha sido expulsado: no está en el campo`,
`El jugador que entra debe ser un suplente que no haya entrado ya`, `No se puede hacer un cambio en este momento del
partido` (antes del inicio o tras el final).

### `POST /api/app/partido-en-vivo/{partidoId}/tarjeta`

Mismo rol, partido y respuesta. Sin push.

```json
{ "jugadorId": 100, "color": "AMARILLA", "minuto": 35 }
```

`color`: `AMARILLA` o `ROJA`. El jugador debe estar en la lista elegible. Vale en cualquier momento entre el inicio y el
final (también en el descanso). **La segunda amarilla del mismo jugador es una expulsión por doble amarilla**: el evento
sigue siendo `TARJETA_AMARILLA` con `dobleAmarilla: true` en el estado de la retransmisión, y en la ficha pública sale
como roja. Un jugador expulsado no admite más tarjetas ni puede salir en un cambio. Una tarjeta idéntica reintentada en
menos de 15 segundos no se registra (evita una doble amarilla falsa).

Errores `400`: `Indica el jugador de la tarjeta`, `El color de la tarjeta debe ser AMARILLA o ROJA`,
`El jugador no está entre los jugadores del partido`, `El jugador ya ha sido expulsado`, `No se puede mostrar una tarjeta
en este momento del partido`.

### Corregir el minuto y deshacer

`PATCH /{partidoId}/eventos/{eventoId}/minuto` corrige ahora el minuto de un gol, un cambio o una tarjeta (el resto,
`400`). `POST /{partidoId}/eventos/{eventoId}/deshacer` también deshace cambios y tarjetas, con la regla de siempre
(solo el último evento y dentro de los 2 minutos). Deshacer la segunda amarilla quita la expulsión. **No se puede
deshacer un cambio si el que entró ya ha salido o tiene un evento posterior** (`No se puede deshacer el cambio: el
jugador que entró ya ha salido del campo` / `... ya tiene un evento posterior`); con la regla de "solo el último" esta
comprobación solo sale cuando el evento posterior existe, y entonces el mensaje es el específico del cambio.

### Al FINAL: tarjetas en las estadísticas

Igual que con los goles, el `FINAL` guarda las tarjetas en las estadísticas de jugador **si el partido no tenía
ninguna** (y el equipo admite estadísticas), con la misma marca `estadisticasGuardadas` y el mismo cuidado al deshacer
el final (se borran solo las que creó el final). Con la regla de la doble amarilla de la tabla (el resto, como las mete el formulario: contador de amarillas y
casilla de roja, independientes):

| Qué pasó | Se guarda |
|---|---|
| Una amarilla | `tarjetasAmarillas` +1 |
| Roja directa | `tarjetaRoja = true` (las amarillas que ya tuviera siguen contando) |
| Doble amarilla | `tarjetaRoja = true` y `tarjetasAmarillas` +0: las dos amarillas que la causan no computan para el ciclo de acumulación |

Una doble amarilla es 1 roja y 0 amarillas **solo en las estadísticas** (y por tanto en el ciclo de acumulación y los avisos
de sanción): en la ficha pública y en el estado de la retransmisión se sigue viendo la amarilla y la expulsión por la
segunda. El formulario de estadísticas del entrenador (app) lo explica en una ayuda bajo el título de estadísticas por jugador.

Y se disparan los avisos de tarjetas / posible sanción al cuerpo técnico exactamente como al guardar las estadísticas
(ver [avisos.md](avisos.md): `tarjetas.*`; se envían después de confirmar el final). Deshacer el final borra las
estadísticas pero no puede retirar un aviso ya enviado.

### Ficha pública: `GET /api/public/partidos/{partidoId}/ficha`

Sin autenticación. Solo partidos del **Primer Equipo** (`404` en otro equipo o si no existe). `Accept-Language`
(`es`, `ca`, `en`; sin él, castellano) afecta a los textos fijos. Es ligera: una lectura del partido, de los eventos, de
la alineación, de los jugadores (con la miniatura solo de los mayores de edad), de la temporada, de la competición y de
los últimos resultados; sin consultas por jugador.

```json
{
  "cabecera": {
    "partidoId": 30, "equipoLocal": "Mutxamel CF", "equipoVisitante": "Pego C.F.",
    "dia": "2026-11-08", "hora": "18:00", "golesFavor": 2, "golesContra": 1,
    "estado": "EN_JUEGO", "competicion": "Preferente Valenciana · Jornada 9"
  },
  "eventos": [
    { "tipo": "TARJETA_ROJA", "minuto": 80, "jugador": "Ana Ruiz", "jugadorEntra": null,
      "golesFavor": null, "golesContra": null, "texto": null, "dobleAmarilla": true },
    { "tipo": "DESCANSO", "minuto": null, "jugador": null, "jugadorEntra": null,
      "golesFavor": 1, "golesContra": 0, "texto": "Marcador al descanso", "dobleAmarilla": false }
  ],
  "alineacion": {
    "formacion": "4-4-2",
    "titulares": [
      { "dorsal": 1, "nombre": "Pablito", "fila": 1, "columna": 1, "sustituido": true, "minutoSustitucion": 60,
        "tarjetas": [ { "color": "AMARILLA", "minuto": 20, "dobleAmarilla": false } ], "fotoBase64": "..." }
    ],
    "suplentes": [
      { "dorsal": 20, "nombre": "Mayorcito", "entro": true, "minutoEntrada": 60, "tarjetas": [], "fotoBase64": null }
    ]
  },
  "forma": [
    { "partidoId": 44, "dia": "2026-11-06", "local": false, "rival": "R5", "golesFavor": 1, "golesContra": 2, "resultado": "P" }
  ]
}
```

**Cabecera.**
- `equipoLocal` / `equipoVisitante`: "Mutxamel CF" y el rival, según sea local o visitante.
- `golesFavor` / `golesContra`: los del directo si hay retransmisión; si no, los del resultado guardado (`null` si no hay).
- `estado`: `CANCELADO` si el partido está cancelado; si no, por el directo: `EN_JUEGO` (primera o segunda parte),
  `DESCANSO` o `FINALIZADO`; sin retransmisión, `FINALIZADO` si tiene resultado guardado y `PROXIMO` si no.
- `competicion`: **liga** = la competición de `EQUIPOS_FFCV` del equipo en la temporada del partido (la temporada cuyas
  fechas contienen el día; si ninguna, la activa) y `" · Jornada N"` si la tiene (`Jornada N` sola si no hay
  competición; `Liga` si no hay ninguna de las dos). **Copa** = `Copa` y `" · competición"` si la hay. **Amistoso** =
  `Amistoso`. En catalán y en inglés: `Amistós`/`Lliga`/`Jornada N`, `Friendly`/`League`/`Cup`/`Matchday N`.

**Eventos.** Los no anulados, **lo último primero**. `tipo`: `GOL_FAVOR`, `GOL_CONTRA`, `CAMBIO`, `TARJETA_AMARILLA`,
`TARJETA_ROJA` (también la doble amarilla, con `dobleAmarilla: true`) y los separadores `DESCANSO` y `FINAL`
(`texto` en el idioma pedido: "Marcador al descanso" / "Marcador final"; el marcador en `golesFavor`/`golesContra`).
`INICIO` y `SEGUNDA_PARTE` no salen. `jugador` es el nombre en directo (el de retransmisión si lo tiene); un gol de
`Otro` lleva ese texto, `p.p.` sale como "Gol en propia puerta del rival" en el idioma, y un gol en contra no tiene
autor. En un gol, `golesFavor`/`golesContra` es el marcador **tras** el gol. En un cambio, `jugador` es el que sale y
`jugadorEntra` el que entra.

**Alineación.** `formacion` y las listas vacías si no se envió alineación. Titulares: `fila` (1 = portero, después las
líneas de atrás adelante) y `columna` (de izquierda a derecha) según la formación (`null` sin ella), si fue sustituido y
en qué minuto, y sus tarjetas (la segunda amarilla como `ROJA` con `dobleAmarilla`). Suplentes: si entraron y en qué
minuto, y sus tarjetas.

**Fotos y privacidad.** `fotoBase64` es la misma miniatura JPEG que la del cuerpo técnico público, **solo** de mayores de
edad: `ANIO_NACIMIENTO <= año del partido - 18`, o sin año pero de la plantilla del Primer Equipo (`JUGADORES.EQUIPO_ID`).
Para un menor es `null` (la app pone el avatar) y la consulta ni siquiera lee la foto. **Nunca se devuelve el año de
nacimiento.**

**Forma.** Los últimos 5 partidos del Primer Equipo con resultado (cualquier tipo, también amistosos; sin cancelados) con
día anterior o igual al del partido (incluido él si ya tiene resultado), del más reciente al más antiguo. `resultado`: `G`,
`E` o `P` desde el punto de vista del Mutxamel; `local` indica si jugó en casa.

**Sin retransmisión** (ni eventos ni alineación): `eventos` y las listas de la alineación vacías y la cabecera con el
resultado guardado.
