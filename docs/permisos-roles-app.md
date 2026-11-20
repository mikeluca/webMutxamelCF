# Roles de la app y permisos

Roles de `ROLES_APP`: `JUGADOR`, `FAMILIAR`, `ENTRENADOR`, `COORDINADOR`, `RETRANSMISION`, `RRSS` y `ADMIN_APP`.
Una cuenta puede tener varios. `COORDINADOR`, `RETRANSMISION` y `RRSS` son roles **sin persona** (no atan la cuenta a una
ficha); `ADMIN_APP` solo lo concede el panel web (SUPER).

- **RETRANSMISION**: solo el partido en directo del Primer Equipo.
- **RRSS**: solo las imágenes para redes (previa, resultados y carteles del Primer Equipo y del Filial) y sus
  recordatorios (viernes 12:00 y lunes 09:00, si no están generadas; si no hay ninguna cuenta con RRSS, van a `ADMIN_APP`).
- **ADMIN_APP**: acceso a **toda** la API de la app (`/api/app/**`). Lo que depende de tener jugadores o familiares
  vinculados (Mis jugadores, justificar ausencias, convocatoria de "mi jugador", Mi ficha) no aplica si la cuenta no los tiene:
  la comprobación es el vínculo, no el rol.

Toda ruta de `/api/app/**` (salvo las de `AccesoCuentaSinRoles.RUTAS_SIN_ROL`) exige tener **algún** rol; `RRSS` cuenta.

## Tabla función × rol

`✔` acceso · `—` 403 · `(v)` solo con el jugador/familiar vinculado (el rol no basta) · `(eq)` solo equipos que gestiona

| Función (rutas) | JUGADOR | FAMILIAR | ENTRENADOR | COORDINADOR | RETRANSMISION | RRSS | ADMIN_APP | Comprobación |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Partido en directo (`/partido-en-vivo/**`) | — | — | — | — | ✔ | — | ✔ | `PartidoEnVivoServiceImpl.validarRolRetransmision` |
| Imágenes para redes: previa y resultados, carteles Primer Equipo y Filial (`/imagenes-redes/**`) | — | — | — | — | — | ✔ | ✔ | `EquipoGestionDao.esRrssOAdmin` |
| Recordatorios de imágenes (push + bandeja) | — | — | — | — | — | ✔ (recibe) | ✔ (recibe si no hay RRSS) | `EquipoGestionDao.obtenerRrss` / `obtenerAdministradoresApp` |
| Calendario, partidos, convocatorias, entrenamientos, horarios, estadísticas, dorsal/posición, resumen de temporada, equipaciones | — | — | ✔ (eq) | ✔ | — | — | ✔ | `puedeGestionarEquipo`, `gestionaAlgunEquipo` |
| Coordinación / fin de semana, recordatorio a entrenadores | — | — | — | ✔ | — | — | ✔ | `esCoordinadorOAdmin` |
| Invitados y aprobación de invitaciones a otros equipos | — | — | ✔ (eq) | ✔ | — | — | ✔ | `esCoordinadorOAdmin` + `puedeGestionarEquipo` |
| Reparto de jugadores en equipos (nueva temporada) | — | — | — | ✔ | — | — | ✔ | `esCoordinadorOAdmin` |
| Continuidad: altas de prueba | — | — | — | ✔ | — | — | ✔ | `esCoordinadorOAdmin` |
| Solicitudes de acceso de familias: pedir | — | ✔ (cualquier cuenta) | | | | | | `SolicitudFamiliaServiceImpl` |
| Solicitudes de acceso de familias: revisar | — | — | ✔ (eq) | ✔ | — | — | ✔ | `esCoordinadorOAdmin` / `gestionaAlgunEquipo` |
| Comunicaciones: ver, crear, avisos a equipos y a categorías, mensajes a cualquiera | — | — | ✔ (a sus equipos, sin categorías) | ✔ | — | — | ✔ | `ComunicacionServiceImpl` (`esAdmin`, `esCoordinador`) |
| Moderación (reportes de mensajes) | — | — | — | — | — | — | ✔ | `ModeracionServiceImpl` |
| Estado de la app, cuentas pendientes, invitar usuarios desde la app | — | — | — | — | — | — | ✔ | `UsuarioAppService.exigirAdminApp` |
| Mis jugadores, justificar ausencias, responder convocatoria, Mi ficha | (v) | (v) | — | — | — | — | (v) | `UsuarioAppVinculoDao.tieneVinculoConJugador` |
| Perfil, notificaciones, preferencias, idioma, dispositivos | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | cualquier cuenta con sesión |

## Dónde se trata RRSS como rol "con acceso" o "que mantiene la cuenta"

- `CuentaSinJugadoresDaoImpl`: una cuenta con `RRSS` (como con `RETRANSMISION`) no es una "cuenta sin jugadores" que se desactive.
- `AccesoCuentaSinRoles`: cuenta como rol (la `authority` `ROLE_RRSS` basta).
- `ComunicacionesService.SECCIONES_DE_ROL`: email de cuenta activada con la sección `rol.RRSS`.
- `UsuarioAppServiceImpl`: `RRSS` es un rol sin persona (invitar, editar y quitar vínculos; panel y app).
- Panel web (`admin/usuarios-app.html`): check "Redes sociales (imágenes para redes)" al invitar y al editar.
- Estado de la app y listado del panel: salen los códigos de rol tal cual (`RRSS`), sin lista cerrada.

## SQL

`sql/2026-11-20_rol_rrss.sql`: inserta `ROLES_APP` (`CODIGO = 'RRSS'`, `NOMBRE = 'Redes sociales'`). Ejecutado en DEV y PRO el 20/11/2026.
