# Solicitudes de acceso de las familias: "Soy familiar de un jugador" (contrato para la app)

Una familia puede pedir desde la app el acceso a la información de su hijo o hija, y el club lo aprueba. El script de
base de datos es `sql/2026-11-18_solicitudes_familia.sql` (ya aplicado en DEV y PRO). Todo lo nuevo es aditivo.

## 0. Resumen del flujo

1. **Sin cuenta**: `POST /api/public/solicitudes-familia` crea una cuenta **sin roles** y manda un código de 6 dígitos por
   email. `POST .../verificar` (código + contraseña) confirma el email, la solicitud pasa a `PENDIENTE` y la persona ya
   entra en la app (la respuesta es un login normal).
2. **Con cuenta**: `POST /api/app/solicitudes-familia` envía la misma solicitud sin crear cuenta ni verificar email.
3. **Revisión**: coordinación, administración de la app y los entrenadores de los equipos coincidentes aprueban o
   rechazan **cada hijo** (app o panel web). Al aprobar, la cuenta recibe el rol `FAMILIAR` y queda vinculada.
4. **Caducidad**: a los 7 días sin revisar (o tras un rechazo) se borran los datos.

## 1. Lo que ve una cuenta sin roles

Una cuenta **sin ningún rol** (la de una familia que aún no está aprobada) solo puede llamar a:

| Ruta | Para qué |
| --- | --- |
| `GET /api/app/auth/me`, `DELETE /api/app/auth/cuenta` | Quién soy; eliminar mi cuenta |
| `GET /api/app/perfil` | Mi perfil (vacío: sin vínculos) |
| `/api/app/solicitudes-familia/**` | Pedir acceso y ver mi solicitud (las rutas de revisión devuelven 403) |
| `/api/app/dispositivos`, `/api/app/notificaciones`, `/api/app/preferencias-notificacion`, `/api/app/preferencias/idioma` | Recibir el aviso de la respuesta y elegir idioma y avisos |

**Todo lo demás de `/api/app/**` devuelve 403** (jugadores, equipos, calendario, convocatorias, comunicaciones,
partidos...). Lo aplica `AccesoCuentaSinRoles` en `SecurityConfig`, no cada endpoint. Lo público (`/api/public/**`) sigue
abierto como siempre. La app debe enseñar a esta cuenta solo lo público y el estado de su solicitud.

**Nunca** se devuelve si un jugador existe ni datos de jugadores en estos endpoints: las coincidencias solo salen en las
rutas de revisión (secciones 4 y 5).

## 2. Sin sesión (`/api/public/solicitudes-familia`)

### `POST /api/public/solicitudes-familia`

```json
{
  "nombre": "Ana", "apellidos": "Pérez Gil", "email": "ana@example.com", "telefono": "623 17 68 18",
  "parentesco": "MADRE",
  "hijos": [ { "nombre": "Luis", "apellidos": "Pérez", "equipo": "Alevín B" } ],
  "aceptaPrivacidad": true, "declaraRepresentante": true,
  "idioma": "es"
}
```

- `parentesco`: `MADRE`, `PADRE`, `TUTOR` u `OTRO`. `telefono` y `equipo` son opcionales; `idioma` (`es`, `ca`, `en`) es el
  de la cuenta y el del email.
- Entre **1 y 5 hijos**. Solo nombre, apellidos y equipo o categoría tal como los escribe la familia: ni fecha de
  nacimiento ni otros datos del menor, y no se comprueba contra los jugadores del club.
- Las dos casillas son obligatorias.
- Máximos: nombre 100, apellidos 150, email 150, teléfono 20, equipo 100.

Respuestas (siempre un JSON con `mensaje`):

| HTTP | `estado` | Qué hacer |
| --- | --- | --- |
| 200 | `CODIGO_ENVIADO` | Cuenta nueva (o la misma persona repite antes de confirmar: se sustituyen los datos y va un código nuevo). Pedir el código |
| 200 | `CUENTA_EXISTENTE` | El email ya tiene una cuenta (o el club la desactivó): iniciar sesión y pedir el acceso desde dentro (sección 3) |
| 200 | `INVITACION_PENDIENTE` | El club ya invitó a ese email y no se ha canjeado: usar "Activar cuenta" con el código recibido |
| 400 | — | `mensaje` dice qué falta o no vale |
| 429 | — | `codigo: DEMASIADOS_INTENTOS` |
| 503 | `ENVIO_FALLIDO` | La cuenta se creó pero el email no salió: pedir `reenviar-codigo` |

Las respuestas `CUENTA_EXISTENTE` e `INVITACION_PENDIENTE` no revelan nada más de la cuenta.

### `POST /api/public/solicitudes-familia/verificar`

```json
{ "email": "ana@example.com", "codigo": "123456", "password": "...", "idioma": "es" }
```

La contraseña sigue las mismas reglas que "Activar cuenta" (mínimo 8 caracteres...). **200**: `LoginAppResponse` igual que
`/api/app/auth/login` (`token`, `usuarioId`, `email`, `roles: []`); la solicitud pasa a `PENDIENTE` y se avisa a quien
revisa. **400**: "El código no es válido o ha caducado." (incorrecto, caducado, sin solicitud por confirmar...: un solo
mensaje; cada fallo gasta un intento y a los 5 el código se invalida). **409**: la cuenta ya estaba activa.
Solo sirve para una solicitud sin confirmar: no es otra forma de activar invitaciones del club.

### `POST /api/public/solicitudes-familia/reenviar-codigo`

```json
{ "email": "ana@example.com" }
```

Responde **siempre 204** (salvo 429), exista o no el email y tenga o no una solicitud por confirmar.

### Límites

`PublicFormRateLimiter`: **5 peticiones cada 10 minutos por IP y por email**, con contador propio para solicitud,
verificación y reenvío. 429 al superarlo. Además el código tiene sus 5 intentos.

### Validez del código

El código de confirmación (como el de las invitaciones del club y sus reenvíos) caduca a los **5 días (120 horas)** de
enviarse el email. El de recuperar la contraseña sigue en 30 minutos.

## 3. Con sesión (`/api/app/solicitudes-familia`)

### `POST /api/app/solicitudes-familia`

El mismo cuerpo, pero `email` se ignora (es el de la cuenta) y `nombre`/`apellidos` son opcionales si la cuenta ya los
tiene. **201** con la solicitud (formato de la sección siguiente), ya en `PENDIENTE`. **409** si ya tiene una solicitud
pendiente de revisar. 400 si algo no vale, 429 por el límite.

### `GET /api/app/solicitudes-familia/mias`

Las solicitudes propias que no han caducado, la más reciente primero. **Nunca lleva jugadores ni coincidencias**.

```json
[ { "id": 12, "estado": "PENDIENTE", "parentesco": "MADRE", "fechaSolicitud": "2026-11-18T10:15:00",
    "fechaRevision": null, "motivoRechazo": null,
    "hijos": [ { "id": 31, "nombre": "Luis", "apellidos": "Pérez", "equipo": "Alevín B", "estado": "PENDIENTE" } ] } ]
```

## 4. Estados

| Solicitud | Significado |
| --- | --- |
| `PENDIENTE_EMAIL` | Falta confirmar el email con el código (solo en el registro sin sesión) |
| `PENDIENTE` | La revisa el club |
| `APROBADA` | Revisados todos los hijos y **alguno** aprobado |
| `RECHAZADA` | Revisados todos los hijos y **ninguno** aprobado (`motivoRechazo`: los motivos escritos) |
| `CADUCADA` | Existe en la tabla pero el backend nunca deja una fila así: al caducar se **borra entera** |

| Hijo | Significado |
| --- | --- |
| `PENDIENTE` / `APROBADO` / `RECHAZADO` | Cada hijo se revisa por separado; la solicitud se cierra cuando ninguno está `PENDIENTE` |

El motivo de un rechazo se guarda **en la solicitud** (los hijos no tienen el suyo); si se rechazan varios hijos con
motivo, se unen con " / ".

## 5. Revisión (con sesión)

Quién revisa: **COORDINADOR y ADMIN_APP** todas; un **ENTRENADOR** solo las de hijos con alguna coincidencia en un equipo
que puede gestionar (`puedeGestionarEquipo`), y al aprobar solo puede elegir jugadores de esos equipos. En el panel web,
**SUPER y ADMIN** (todas). Cualquier otra cuenta: **403**.

- `GET /api/app/solicitudes-familia/pendientes`: las pendientes que puede revisar, con las coincidencias. Un entrenador
  solo ve los hijos con coincidencias en sus equipos, y solo esas coincidencias.
- `GET /api/app/solicitudes-familia/pendientes/contador`: `{ "total": 3 }`.
- `GET /api/app/solicitudes-familia/jugadores?q=garcia`: busca jugadores activos por nombre entre los que el revisor puede
  ver (mínimo 3 letras, máximo 20).
- `POST /api/app/solicitudes-familia/hijos/{hijoId}/aprobar` con `{ "jugadorId": 55 }`.
- `POST /api/app/solicitudes-familia/hijos/{hijoId}/rechazar` con `{ "motivo": "..." }` (opcional, máximo 500).

Cada una devuelve la solicitud tal como la ve el revisor:

```json
{ "id": 12, "nombre": "Ana", "apellidos": "Pérez Gil", "email": "ana@example.com", "telefono": "623 17 68 18",
  "parentesco": "MADRE", "estado": "PENDIENTE", "motivoRechazo": null,
  "fechaSolicitud": "2026-11-18T10:15:00", "fechaRevision": null,
  "hijos": [ { "id": 31, "nombre": "Luis", "apellidos": "Pérez", "equipo": "Alevín B", "estado": "PENDIENTE",
               "jugador": null,
               "coincidencias": [ { "jugadorId": 55, "nombre": "Luis Pérez Gómez", "equipoId": 8, "equipo": "Alevín B",
                                    "coincideEquipo": true } ] } ] }
```

**Coincidencias**: jugadores activos cuyo nombre y apellidos coinciden normalizados (sin acentos, mayúsculas ni espacios de
más); los apellidos coinciden si son iguales o uno es el principio del otro ("Pérez" encuentra a "Pérez Gómez"). El
equipo o categoría escrito no excluye a nadie (se escribe a menudo mal): marca `coincideEquipo` y esas van primero.

**Aprobar** un hijo: crea o reutiliza la ficha de familiar (si ya hay una con ese email o, si no, con ese teléfono, se usa
esa), la vincula al jugador (`FAMILIARES_JUGADOR`, con el parentesco: Madre, Padre, Tutor Legal u Otro), vincula la cuenta
con la ficha y le da el rol `FAMILIAR`, con la misma lógica que las invitaciones del club
(`UsuarioAppService.agregarVinculosAUsuarioExistente`). No se avisa a los familiares ya vinculados con ese jugador.

Errores: **400** petición no válida o jugador inexistente, **403** sin permiso (o jugador de un equipo que no gestiona),
**409** la solicitud o el hijo ya no están pendientes, o la ficha de familiar ya está vinculada a otra cuenta (se resuelve
en el panel, Familiares).

## 6. Avisos

Ver `docs/avisos.md` ("Solicitudes de acceso de las familias"). En el push a quien revisa **no hay datos del menor**
(`"Nueva solicitud de acceso de una familia"`); `data.tipo` = `SOLICITUD_FAMILIA`, `data.pantalla` = `SOLICITUDES_FAMILIA`.
Al familiar: `SOLICITUD_FAMILIA_RESPUESTA` / `MI_SOLICITUD_FAMILIA`.

## 7. Caducidad y limpieza (diarias, hora de Madrid)

- **04:00**: las solicitudes `PENDIENTE_EMAIL` o `PENDIENTE` de más de **7 días** sin revisar, y las `RECHAZADA` revisadas hace
  más de 7 días, se **borran** (datos del familiar y de los hijos escritos). Si la cuenta no tiene ningún rol ni vínculo
  se elimina como en "Eliminar cuenta" (queda anonimizada y el email vuelve a estar libre). Las cuentas con roles
  nunca se tocan. Se avisa por email de que ha caducado y puede volver a pedirla, solo si caducó sin revisarse (a una rechazada no se le envía nada: ya recibió el email del rechazo). Las `APROBADA` no se borran.
- **10:00**: recordatorio a coordinación y administradores si hay solicitudes `PENDIENTE` de más de 2 días.

La tabla tiene una columna `IP_ORIGEN` que el backend **no rellena**: ningún otro formulario público guarda la IP y el
límite de intentos va en memoria.

## 8. Panel web

`/admin/solicitudes-familia` (menú de administración, SUPER y ADMIN): pendientes con sus coincidencias, aprobar
(eligiendo jugador, con buscador por nombre), rechazar con motivo y el histórico de los últimos 7 días.
