# API de la app: gestionar los usuarios de la app (solo ADMIN_APP)

Lo que el panel web hace en `/admin/usuarios-app`, desde la app. Las rutas **solo envuelven** `UsuarioAppService`,
`InvitacionUsuario` y `ReenvioActivacion` (los mismos que usa el panel): mismo comportamiento y mismos mensajes. Código:
`AppAdminUsuariosController`; las reglas de seguridad están **en el servicio** (`UsuarioAppServiceImpl`, métodos
`*DesdeApp`), no solo en la ruta. Pruebas: `AppAdminFfcvUsuariosControllerTest` y `UsuarioAppServiceAdminDesdeAppTest`.

## Permisos, errores y reglas de seguridad

Solo `ADMIN_APP`; el resto (COORDINADOR, ENTRENADOR, RRSS, RETRANSMISION, familias...) recibe **403** y sin sesión **401**.
Las respuestas llevan `{"mensaje": "..."}` o `{"error": "..."}`.

| Código | Cuándo |
| --- | --- |
| 400 | dato no válido o una regla de seguridad lo impide (el mensaje dice cuál) |
| 403 | la cuenta no es ADMIN_APP |
| 409 | `invitar` con un email que ya tiene cuenta |
| 429 | `reenviar` antes de que pasen 10 minutos desde el último reenvío a esa cuenta |
| 500 | error inesperado (o el email no pudo salir al reenviar) |

Reglas de un ADMIN_APP (todas dan **400**):

- No puede **desactivarse** a sí mismo (`No puedes desactivar tu propia cuenta`).
- No puede tocar la cuenta de **otro ADMIN_APP**: ni desactivarla (`No se puede desactivar a un administrador`), ni
  eliminarla, ni quitarle roles (`No se pueden quitar roles a otro administrador`), igual que en Moderación.
- No puede **quitarse a sí mismo el rol** de administrador (`No puedes quitarte a ti mismo el rol de administrador`).
- No puede **asignar ADMIN_APP** a nadie (`El rol ADMIN_APP solo se puede asignar desde el panel web`): ese rol solo se
  concede desde el panel web (SUPER).

## Lectura

### `GET /api/app/admin/usuarios?q=&rol=&estado=`

El mismo listado que el panel (sin las cuentas que borró su titular). Filtros opcionales y combinables:

- `q`: nombre o email, sin distinguir mayúsculas ni acentos (no busca en roles ni en el resto de la fila).
- `rol`: código de un rol (`RRSS`, `JUGADOR`...) o `SIN_ROLES` (cuentas sin ningún rol, como las familias pendientes de
  aprobar).
- `estado`: `ACTIVA` | `PENDIENTE` | `CADUCADA` | `DESACTIVADA`. Cualquier otro valor da 400. Es la misma regla que las
  insignias del panel: activa; si no, `CADUCADA` si el código de la invitación caducó; si no, `PENDIENTE` si tiene código;
  si no, `DESACTIVADA`.

```json
[ { "id": 12, "email": "ana@example.com", "nombre": "Ana Pérez", "estado": "ACTIVA", "activo": true,
    "roles": [ { "codigo": "FAMILIAR", "nombre": "Familiar" } ],
    "vinculos": [ { "tipo": "FAMILIAR", "personaId": 5, "nombre": "Ana Pérez", "detalle": null } ],
    "ultimoAcceso": "2026-11-20T09:15:00.000+00:00" } ]
```

`nombre` es el que muestra el panel: el de la primera ficha vinculada o, si no tiene, el nombre propio de la cuenta (null si
no hay).

### `GET /api/app/admin/usuarios/opciones`

Lo que necesita la app para invitar y editar:

```json
{ "roles": [ { "codigo": "COORDINADOR", "nombre": "Coordinador" }, { "codigo": "RETRANSMISION", "nombre": "Retransmisión" },
             { "codigo": "RRSS", "nombre": "Redes sociales" } ],
  "personas": { "jugadores": [], "familiares": [], "cuerpoTecnico": [] } }
```

`roles`: los roles **sin persona** que se pueden asignar (los configurados en `ROLES_APP`; **nunca ADMIN_APP**).
`personas`: las personas sin cuenta por tipo (`PersonasVinculablesResponse`, como en el panel).

## Acciones

| Ruta | Cuerpo | Respuesta `200` / `201` |
| --- | --- | --- |
| `POST /api/app/admin/usuarios/invitar` | `{email, nombre, apellidos, vinculos: [{tipo, personaId}], roles: ["RRSS"]}` | `201` `{email, emailEnviado, mensaje}` |
| `POST /api/app/admin/usuarios/{id}/nombre` | `{nombre, apellidos}` | `{mensaje: "Nombre actualizado correctamente."}` |
| `POST /api/app/admin/usuarios/{id}/vinculos` | `{vinculos: [{tipo, personaId}]}` | `{mensaje: "Vínculo/rol añadido correctamente."}` |
| `POST /api/app/admin/usuarios/{id}/vinculos/quitar` | `{tipo, personaId}` | `{mensaje: "Vínculo/rol quitado correctamente."}` |
| `POST /api/app/admin/usuarios/{id}/reenviar` | — | `{mensaje: "Invitación reenviada a {email}."}` |
| `POST /api/app/admin/usuarios/{id}/activar` | — | `{mensaje: "Cuenta activada."}` (y se manda el email de cuenta activada) |
| `POST /api/app/admin/usuarios/{id}/desactivar` | — | `{mensaje: "Cuenta desactivada."}` |
| `POST /api/app/admin/usuarios/{id}/eliminar` | — | `{mensaje: "Invitación eliminada."}` |

- `invitar` es la misma invitación que `POST /api/app/admin/invitaciones` (ambas llaman a `InvitacionUsuario.desdeApp`);
  `tipo` es `JUGADOR`, `FAMILIAR`, `ENTRENADOR` (con `personaId`) o un rol sin persona (`COORDINADOR`, `RETRANSMISION`,
  `RRSS`). Un rol sin persona sin ficha exige `nombre`. Si el email existe: `409` con
  `Ya existe una cuenta con ese email. Revisa Cuentas pendientes.` Si la cuenta se crea pero el email no sale,
  `emailEnviado` es `false` y el mensaje lo dice.
- `reenviar`: como en el panel, vale para cualquier cuenta que no esté activa (también una desactivada); una activa da 400
  (`La cuenta ya está activa`). Límite de **un reenvío cada 10 minutos por cuenta** (`LimitadorReenvioActivacion`); si falla
  la validación o el email, no gasta el intento.
- `eliminar`: solo cuentas **sin activar**; una activa da 400 (`No se puede eliminar una cuenta ya activa. Desactívala si
  quieres revocarle el acceso.`). Quita también sus vínculos y roles.

## Cuentas pendientes y la versión 1.0.15

Las rutas de "Cuentas pendientes" siguen en el servidor **solo para las versiones antiguas de la app**:

| Antigua | Nueva (la que usa la app **1.0.15**) |
| --- | --- |
| `GET /api/app/admin/usuarios-pendientes` | `GET /api/app/admin/usuarios?estado=PENDIENTE` (y `CADUCADA`) |
| `POST /api/app/admin/usuarios/{id}/reenviar-activacion` | `POST /api/app/admin/usuarios/{id}/reenviar` |
| `GET /api/app/admin/vinculos-disponibles` | `GET /api/app/admin/usuarios/opciones` |
| `POST /api/app/admin/invitaciones` | `POST /api/app/admin/usuarios/invitar` (hacen lo mismo) |

La antigua `reenviar-activacion` solo admite cuentas pendientes (nunca activadas); la nueva `reenviar` sigue el
comportamiento del panel.
