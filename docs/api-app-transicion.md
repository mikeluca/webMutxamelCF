# API de la app para la transición de temporada

Contrato de los endpoints de reparto (commit `2b2c2b3`), continuidad y pruebas, y del `data` de los push.
Todos son `/api/app/**`: llevan `Authorization: Bearer <JWT>` y devuelven JSON. Para el resto de la transición, ver `transicion-temporada.md`.

Errores comunes (cuerpo = texto plano con el motivo, salvo el 409 de pruebas):

| Código | Cuándo |
|---|---|
| 401 | Sin token o usuario no válido |
| 403 | El usuario no tiene permiso (no es coordinador, o no está vinculado con el jugador) |
| 400 | Datos no válidos (mensaje en el cuerpo) |
| 409 | No hay temporada activa o siguiente; en `/reparto/asignar`, pruebas sin confirmar |

## 1. Reparto (rol COORDINADOR o ADMIN_APP)

### GET `/api/app/reparto/categorias`
Categorías formativas con equipos en la temporada activa, en orden de edad.
```json
["Escuelita", "Prebenjamín", "Benjamín", "Alevín", "Infantil", "Cadete", "Juvenil"]
```

### GET `/api/app/reparto?categoria=Alevín`
Un lado, los jugadores sin equipo definitivo (pretemporada y pruebas); el otro, los equipos definitivos con sus jugadores. 400 si la categoría no es formativa.
```json
{
  "categoria": "Alevín",
  "pretemporada": { "equipoId": 10, "nombre": "Alevín (Pretemporada)", "grupo": "P", "modalidad": "F8", "activo": true, "jugadores": [] },
  "porAsignar": [
    { "jugadorId": 1, "nombre": "Ana Pérez", "anioNacimiento": 2015, "dorsal": null, "estado": "CONFIRMADO", "equipoDefinitivo": false },
    { "jugadorId": 2, "nombre": "Biel Gil", "anioNacimiento": 2015, "dorsal": null, "estado": "PRUEBA", "equipoDefinitivo": false }
  ],
  "equipos": [
    { "equipoId": 11, "nombre": "Alevín A", "grupo": "A", "modalidad": "F8", "activo": false,
      "jugadores": [ { "jugadorId": 3, "nombre": "Eva Ruiz", "anioNacimiento": 2014, "dorsal": 9, "estado": "CONFIRMADO", "equipoDefinitivo": true } ] }
  ]
}
```
`pretemporada` es `null` si la categoría no tiene equipo de pretemporada. `estado`: `CONFIRMADO` o `PRUEBA`.

### POST `/api/app/reparto/asignar`
```json
{ "equipoId": 11, "jugadores": [ { "jugadorId": 1, "dorsal": 7 }, { "jugadorId": 2, "dorsal": null } ], "confirmarPruebas": false }
```
- `dorsal`: 0 a 99 o `null`.
- Si hay jugadores en estado `PRUEBA` y `confirmarPruebas` no es `true`, no se asigna nada y responde **409**:
  ```json
  { "codigo": "PRUEBAS_SIN_CONFIRMAR", "mensaje": "Hay jugadores de prueba…", "pruebas": ["Biel Gil"] }
  ```
  La app lo muestra ("pasarán a CONFIRMADO") y repite la petición con `confirmarPruebas: true`.
- 200:
  ```json
  { "asignados": 2, "avisos": ["El dorsal 7 está repetido en Alevín A: Ana Pérez, Eva Ruiz"], "equipoActivado": true, "pretemporadaVaciada": false, "pruebasConfirmadas": ["Biel Gil"] }
  ```
  Los dorsales repetidos solo avisan: la asignación se hace.
- 400: equipo que no es definitivo de esa categoría, jugador de otra categoría o de baja, dorsal fuera de rango, jugador repetido.

### POST `/api/app/reparto/devolver`
Devuelve a la pretemporada de su categoría a jugadores asignados por error (`EQUIPO_DEFINITIVO = 0`, sin dorsal). La pretemporada se reactiva si estaba vacía; el equipo definitivo que se queda vacío **no** se desactiva solo.
```json
{ "jugadorIds": [1, 3] }
```
200: `{ "devueltos": 2, "pretemporadaReactivada": false, "avisos": [] }`. Se borran las cuotas de la temporada activa de cada jugador que no tengan ningún pago; si alguna tiene pagos se conserva y `avisos` lo cuenta (p. ej. `"Ana Pérez tiene 1 cuota con pagos que no se ha borrado: revísalas en Pagos."`). 400 (sin tocar nada): jugador sin equipo definitivo, repetido, sin ficha, o categoría sin pretemporada.

### POST `/api/app/reparto/equipos?categoria=Alevín`
Crea el siguiente equipo de la categoría (letra siguiente, saltando la P; misma modalidad). Nace inactivo hasta que se le asigna el primer jugador.
201: `{ "equipoId": 40, "nombre": "Alevín C", "grupo": "C", "modalidad": "F8", "activo": false, "jugadores": [] }`

## 2. Continuidad (cualquier usuario con jugadores vinculados)

La pregunta se hace sobre la **temporada siguiente** (la primera que empieza después de la activa). Solo aparecen los jugadores a los que ya se les ha preguntado.

### GET `/api/app/continuidad`
```json
[
  { "jugadorId": 1, "nombre": "Pablo Pérez", "categoria": "Alevín", "temporadaId": 7, "temporada": "2027/2028",
    "respuesta": "PENDIENTE", "fechaRespuesta": null }
]
```
`respuesta`: `PENDIENTE` (sin contestar), `SIGUE`, `NO_SIGUE`, `DUDA`. `fechaRespuesta`: ISO local (`2027-05-03T09:30:00`) o `null`. Lista vacía si no hay borrador o no se ha preguntado.

### PUT `/api/app/continuidad/{jugadorId}`
```json
{ "respuesta": "SIGUE" }
```
`respuesta`: `SIGUE`, `NO_SIGUE` o `DUDA`. Se puede cambiar hasta el cierre de la temporada. 200 devuelve el mismo objeto del GET con el estado nuevo.
- `NO_SIGUE` propone la baja; queda pendiente de que la confirme el coordinador en el asistente. Cambiar a `SIGUE` o `DUDA` la deshace y vuelve a quedar pendiente de revisar.
- `NO_SIGUE` o `DUDA` (si cambian la respuesta anterior) avisan a los coordinadores (§4).
- 400: respuesta no válida o el jugador no tiene que confirmar. 403: sin vínculo con el jugador. 409: no hay temporada siguiente.

## 3. Pruebas (rol COORDINADOR o ADMIN_APP)

### POST `/api/app/pruebas`
```json
{ "nombre": "Luis", "apellidos": "Gil", "anioNacimiento": 2016 }
```
Mismas reglas que el asistente. 201: `{ "jugadorId": 99, "categoria": "Alevín", "temporadaId": 7, "estado": "PRUEBA" }`. 400: faltan datos o ninguna categoría cubre su edad. 409: no hay temporada siguiente.

## 4. Push: `data` para abrir la pantalla correcta

Todos los push incluyen, como siempre, `tipo` y `referenciaId` (texto, vacío si no hay), más estas claves. Todos los valores son texto. Cada push crea también una notificación en la bandeja (mismo `tipo`, `referenciaId`, título y mensaje). Se respetan las preferencias de avisos del usuario (interruptor general y comunicaciones).

### A las familias: `tipo = CONTINUIDAD`
Una notificación por cuenta (no por jugador). Título "Continuidad en 2027/2028" (o "Recordatorio: continuidad en 2027/2028"); mensaje "¿Seguirá Pablo en la temporada 2027/2028?" o "¿Seguirán Pablo y Luis en la temporada 2027/2028?" (con "Aún no nos has contestado." delante en el recordatorio).

| Clave | Valor |
|---|---|
| `tipo` | `CONTINUIDAD` |
| `referenciaId` | id de la temporada siguiente |
| `pantalla` | `CONTINUIDAD`: abrir la pantalla de continuidad (`GET /api/app/continuidad`) |
| `accion` | `PREGUNTA` (1 de mayo o botón del asistente) o `RECORDATORIO` (lunes) |
| `temporadaId` | id de la temporada siguiente |
| `jugadorIds` | ids de los jugadores a confirmar, separados por comas (`"1,2"`) |

### A los coordinadores: `tipo = CONTINUIDAD_RESPUESTA`
Cuando una familia responde `NO_SIGUE` ("No seguirá: Pablo Pérez (Alevín)") o `DUDA` ("Duda sobre la continuidad: …").

| Clave | Valor |
|---|---|
| `tipo` | `CONTINUIDAD_RESPUESTA` |
| `referenciaId` | id del jugador |
| `pantalla` | `CONTINUIDAD_COORDINADOR`: abrir la ficha del jugador / revisión de continuidad |
| `temporadaId` | id de la temporada siguiente |
| `jugadorId` | id del jugador |
| `respuesta` | `NO_SIGUE` o `DUDA` |

## 5. Cuándo se envían
- **1 de mayo, 09:00 (Madrid):** si existe la temporada siguiente, genera el borrador y pregunta a las familias de cada jugador formativo pendiente. También se puede lanzar con el botón "Pedir confirmación a las familias" del asistente (solo SUPER). Idempotente: solo se pregunta a quien aún no ha sido preguntado (`JUGADOR_TEMPORADA.FECHA_PREGUNTA_CONTINUIDAD`).
- **Cada lunes, 10:00 (Madrid):** recordatorio a las cuentas con jugadores preguntados que siguen en `PENDIENTE`, hasta el inicio de la temporada siguiente.

## 6. Cuotas
- **Al asignar** el equipo definitivo (`/api/app/reparto/asignar` o el panel) se crean las cuotas del jugador de cada concepto activo de la temporada activa. Las mensuales (conceptos con "cuota" en el nombre) van de septiembre a junio si se asigna en septiembre o antes, y desde el mes de la asignación si es posterior. No se duplican las que ya tiene. Nunca para jugadores de prueba ni equipos de pretemporada.
- **Al devolver** a la pretemporada se borran sus cuotas de la temporada activa sin pagos; las que tienen pagos se conservan y se avisa en `avisos`.
- `GET /api/app/cuotas` solo devuelve cuotas de la temporada activa: las de otros años no las ven las familias (se gestionan en el panel, Pagos → Morosidad).
