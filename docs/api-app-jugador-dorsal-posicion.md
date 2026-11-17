# Dorsal y posición de los jugadores desde la app (contrato para la app)

Los entrenadores (de su equipo), los coordinadores y los ADMIN_APP pueden editar el **dorsal** y la **posición** de un
jugador. No hace falta ningún script de base de datos (`JUGADORES.DORSAL` y `JUGADORES.POSICION` ya existen). Todo lo
nuevo es aditivo: las versiones antiguas de la app siguen funcionando.

## 1. Lista de posiciones

Lista cerrada (un único sitio en el backend: el enum `PosicionJugador`, usado por la API, el panel y las respuestas):

| Código (API)     | Texto guardado en `JUGADORES.POSICION` |
|------------------|----------------------------------------|
| `PORTERO`        | `Portero`                              |
| `DEFENSA`        | `Defensa`                              |
| `CENTROCAMPISTA` | `Centrocampista`                       |
| `EXTREMO`        | `Extremo`                              |
| `DELANTERO`      | `Delantero`                            |

La app trabaja con el **código** y traduce el nombre a su idioma. En la base de datos va el texto en castellano, que es
lo que ya hay en los datos y lo que muestra la web.

## 2. Editar: `PUT /api/app/jugadores/{jugadorId}/dorsal-posicion`

```json
{ "dorsal": 7, "posicion": "DEFENSA" }
```

Los **dos campos se envían siempre**; `null` = vacío (`{"dorsal": null, "posicion": null}` los quita). Si falta alguno de
los dos, 400.

- **Permiso:** el jugador debe estar en un equipo (`EQUIPO_ID`) que el usuario pueda gestionar: el entrenador de ese
  equipo; COORDINADOR y ADMIN_APP, cualquiera. Sin permiso, 403 sin dar detalles del jugador.
- **Dorsal:** entero entre 1 y 99, o `null`. No se puede repetir entre los jugadores **activos** (sin baja) del mismo
  equipo. Un jugador de baja o de otro equipo no cuenta. La comprobación se hace en la misma transacción y bloqueando las
  filas del equipo, así que dos cambios a la vez no dejan dorsales repetidos.
- **Posición:** uno de los códigos de la tabla o `null`. Cualquier otro valor, 400.
- Solo se guardan `DORSAL` y `POSICION`. Si el jugador tiene fila de `JUGADOR_TEMPORADA` en la temporada activa, su
  `DORSAL` se actualiza también (la plantilla de la temporada no queda desfasada; el panel hace lo mismo al editar).
- Queda constancia en el log: usuario, jugador, dorsal y posición anteriores y nuevos.

| Estado | Cuándo | Cuerpo |
|--------|--------|--------|
| 200 | guardado | el jugador actualizado (los campos de la plantilla, ver apartado 3, con `editable: true`) |
| 400 | falta un campo, dorsal fuera de 1-99 o no entero, posición que no es de la lista | texto del error |
| 401 | sin sesión | |
| 403 | no gestiona el equipo del jugador | `No tienes permiso para editar a este jugador` |
| 404 | el jugador no existe o está de baja | |
| 409 | el dorsal ya lo tiene otro jugador activo del equipo | `El dorsal 7 ya lo tiene Nombre Apellido` (nombre y primer apellido del otro; es para el entrenador de ese equipo) |

Ejemplo de respuesta 200:

```json
{
  "id": 21,
  "nombre": "Pablo",
  "apellidos": "Garcia Lopez",
  "dorsal": 7,
  "posicion": "Defensa",
  "posicionCodigo": "DEFENSA",
  "fotoBase64": "...",
  "editable": true,
  "ausenciaJustificada": false,
  "motivoAusencia": null
}
```

## 3. Campos nuevos en las plantillas

Se añaden a cada jugador de:

- `GET /api/public/equipos/{id}/jugadores`
- `GET /api/public/equipos/{id}/plantilla` (dentro de `jugadores`)
- `GET /api/app/equipos/jugadores?equipoId=...`

| Campo | Qué es |
|-------|--------|
| `posicionCodigo` | `PORTERO`, `DEFENSA`, `CENTROCAMPISTA`, `EXTREMO`, `DELANTERO` o `null` si el texto guardado no coincide con ninguno. Se compara sin mayúsculas ni acentos: `"portero"` y `"Centrocampista"` sí coinciden; `"DEL"` no. |
| `editable` | `true` si el usuario autenticado puede gestionar el equipo (entrenador de ese equipo, COORDINADOR, ADMIN_APP); `false` sin sesión o sin permiso. |

`posicion` **no cambia**: sigue siendo el texto guardado tal cual (para las versiones antiguas y para los valores que no
son de la lista). Un jugador con `posicionCodigo: null` y `posicion` con texto debe mostrarse con ese texto.

### Cómo llega `editable` en los endpoints públicos

Los endpoints `/api/public/equipos/...` siguen siendo públicos y anónimos. Para recibir `editable` la app solo tiene que
**enviar su cabecera `Authorization: Bearer ...`** también en estas llamadas: el backend la lee si viene y es válida; si
no viene (o no vale) la petición se trata como anónima y `editable` es `false`, sin ningún error. En
`/api/app/equipos/jugadores` hace falta sesión y permiso de todos modos, así que `editable` es siempre `true`.

`editable` es el mismo para todos los jugadores del equipo (depende del equipo, no del jugador).

## 4. Panel

En la ficha del jugador, "Posición" es un desplegable con la misma lista (más una opción vacía). Si el jugador tiene un
texto antiguo que no está en la lista (por ejemplo `DEL`), se ofrece como la opción seleccionada
`texto (texto actual)` para no perderlo hasta que se cambie. El dorsal aplica la misma regla de no repetición dentro del
equipo (409 con el mensaje `El dorsal 7 ya lo tiene Nombre Apellido`).
