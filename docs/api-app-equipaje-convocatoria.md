# API de la app: equipaje de la convocatoria

Contrato para la app. El entrenador indica con qué equipaje van vestidos al partido y, si hace falta, otro que llevan en la mochila por si acaso. Todo es opcional: una convocatoria sin equipaje sigue siendo válida y no muestra nada.

Todos los endpoints son `/api/app/**`: llevan `Authorization: Bearer <JWT>` y devuelven JSON. Los errores llevan el motivo como texto plano en el cuerpo.

## 1. Alta de los equipajes (panel web, no es API)

Los da de alta el club en el panel (**Administración deportiva → Equipajes**, roles SUPER y ADMIN). El entrenador **elige de esa lista**; no hay texto libre. Un equipaje usado en alguna convocatoria no se borra, solo se desactiva (deja de aparecer en la lista de §2).

## 2. GET `/api/app/equipaciones`

Los equipajes **activos**, ordenados por `orden` y después por nombre. Para cualquier usuario que gestione convocatorias (cuerpo técnico de algún equipo, coordinador o ADMIN_APP); si no, **403**.

```json
[
  { "id": 1, "nombre": "Primera", "camiseta": "Blanca", "pantalon": "Blanco", "medias": "Blancas", "colorHex": "#FFFFFF" },
  { "id": 2, "nombre": "Segunda", "camiseta": "Azul",   "pantalon": null,     "medias": null,      "colorHex": "#1E40AF" }
]
```

- `nombre`: siempre viene. `camiseta`, `pantalon`, `medias` (el color en texto) y `colorHex` (`#RRGGBB`, para la muestra de color) pueden ser `null`.
- Lista vacía `[]` si el club aún no ha dado de alta ninguno: la app no debe mostrar el selector.

## 3. POST `/api/app/convocatorias` y PUT `/api/app/convocatorias/{id}`

Cuerpo como hasta ahora, más tres campos nuevos:

```json
{ "partidoId": 10, "horaConvocatoria": "10:00", "lugarConvocatoria": "Vestuarios",
  "jugadoresIds": [6, 8, 9], "invitadosIds": null,
  "equipacionId": 2, "equipacionMochilaId": 1, "equipajeIndicado": true }
```

| Campo | Significado |
|---|---|
| `equipacionId` | Equipaje con el que van vestidos al partido (`id` de §2). `null` = sin indicar. |
| `equipacionMochilaId` | Equipaje de reserva, en la mochila. `null` = sin mochila. Solo se puede indicar si hay `equipacionId`, y tiene que ser distinto de él. |
| `equipajeIndicado` | Solo cuenta en el **PUT**. La **app nueva lo envía siempre a `true`**. |

**Compatibilidad con versiones antiguas (PUT).** El equipaje solo se modifica si el cuerpo trae `"equipajeIndicado": true`. Si falta (o es `false`) se **conserva el que tenía la convocatoria**, aunque el cuerpo traiga `equipacionId`/`equipacionMochilaId`. Así una app antigua que edita la convocatoria no borra el equipaje. Con `equipajeIndicado: true`, un `null` **quita** ese equipaje.
En el **POST** se usan `equipacionId` y `equipacionMochilaId` tal cual (la marca no hace falta; `null` o ausente = sin equipaje).

**Errores 400** (texto exacto en el cuerpo):

| Caso | Mensaje |
|---|---|
| El `id` no existe | `El equipaje indicado no existe` |
| Equipaje desactivado | `El equipaje <nombre> está desactivado y no se puede elegir` |
| Mochila sin principal | `No se puede indicar el equipaje de la mochila sin indicar el equipaje con el que van vestidos` |
| Mochila igual a la principal | `El equipaje de la mochila tiene que ser distinto del equipaje con el que van vestidos` |

Un equipaje desactivado **sí se admite** si la convocatoria ya lo tenía (principal o mochila): desactivar un equipaje no impide editar el resto de la convocatoria. La app debe mostrar el que ya tiene aunque no salga en la lista de §2.

## 4. Respuestas con el equipaje

Estas respuestas llevan `equipacion` y `equipacionMochila`: un objeto con la forma de §2 o `null` si no se ha indicado. Con `equipacion = null`, `equipacionMochila` es siempre `null`.

- **Entrenador:** `POST` / `PUT` / `GET /api/app/convocatorias/{id}` y `GET /api/app/convocatorias?equipoId=` (cada elemento).
- **Jugador, familia e invitado autorizado:** `GET /api/app/partidos/{partidoId}/convocatoria?jugadorId=`. Es la misma respuesta para los invitados autorizados de otro equipo.

```json
{ "convocatoriaId": 5, "rival": "Pego", "horaCitacion": "09:30", "lugarCitacion": "Vestuarios", "convocado": true, "convocados": [],
  "equipacion":        { "id": 2, "nombre": "Segunda", "camiseta": "Azul", "pantalon": null, "medias": null, "colorHex": "#1E40AF" },
  "equipacionMochila": { "id": 1, "nombre": "Primera", "camiseta": "Blanca", "pantalon": "Blanco", "medias": "Blancas", "colorHex": "#FFFFFF" } }
```

(Solo se muestran los campos relevantes; el resto no cambia.)

## 5. Avisos (push y bandeja)

Mismo `tipo` (`CONVOCATORIA`), `data` y pantalla que el resto de avisos de convocatoria (`convocatoriaId`, `partidoId`, `fechaPartido`). No hay claves nuevas en `data`.

- **Nueva convocatoria** (también para quien entra al editar): si tiene equipaje, el mensaje añade, tras «La convocatoria es a las 10:00h en Vestuarios.», la frase `Equipaje: Segunda.` y, si hay mochila, `En la mochila: Primera.`. Sin equipaje no añade nada. Como mucho unos 130 caracteres más (cada nombre tiene un máximo de 50).
- **Cambio de equipaje:** al editar una convocatoria, si cambia el equipaje principal o el de la mochila, aviso con título **`Cambio de equipaje`** y texto  
  `Cambio en la convocatoria del partido contra Pego: cambio de equipaje. Equipaje: Segunda. En la mochila: Primera.`  
  (o `… cambio de equipaje. Ya no hay equipaje indicado.` si se quita). Va a los mismos destinatarios que el aviso de cambio de hora o lugar de citación: los convocados del propio equipo que siguen en la convocatoria, sus familias, **sin quien ha justificado su ausencia**.
- **Si en la misma edición cambia también la hora, el lugar o el partido**, sale **un solo aviso** («Cambio en la convocatoria») que lo dice todo: `…: nueva hora de citación 10:00 / cambio de equipaje. Equipaje: Segunda.`
- Si no cambia el equipaje no se avisa por él (un PUT sin `equipajeIndicado`, o con los mismos valores, no genera aviso).

> Los invitados de otros equipos ya autorizados **no** reciben hoy los avisos de cambio de hora o lugar (solo los reciben los convocados del propio equipo), y por coherencia tampoco este. Sí ven el equipaje en el detalle de §4.

### Ducha recomendable

Todos los avisos que convocan al jugador terminan, en un párrafo aparte, con  
`🚿 Ducha recomendable después del partido`  
Son: «Nueva convocatoria», «Cambio en la convocatoria», «Cambio de equipaje» y «Convocado con otro equipo» (invitado autorizado). **No** la llevan «Ya no estás convocado» ni «Convocatoria anulada». También va en el texto de la bandeja. La app no tiene que hacer nada: es parte del mensaje.

## 6. Base de datos

`sql/2026-11-03_equipajes_convocatoria.sql`: tabla `EQUIPACIONES` y columnas `EQUIPACION_ID` y `EQUIPACION_MOCHILA_ID` en `CONVOCATORIAS`. Se ejecuta **antes** de desplegar el backend.
