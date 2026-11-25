| `PUBLICADA` | entrenador | hay convocatoria: `convocatoriaId` (para abrirla y editarla), `numeroConvocados` y la citación |# Inicio y Pendientes de la app (contrato para la app 1.0.16)

Dos rutas de **solo lectura** que reutilizan lo que ya existe (calendario del jugador, convocatorias, partidos, sesiones
de entrenamiento, panel de Fin de semana, solicitudes de familia, invitaciones, moderación, cuentas pendientes e imágenes
para redes). **No hay SQL nuevo.** Las dos exigen sesión (`Authorization: Bearer ...`) y cualquier cuenta con algún rol
puede llamarlas; sin sesión, `401`. (Una cuenta sin ningún rol, la de una familia aún sin aprobar, recibe `403` como en el
resto de `/api/app/**`: ver `api-app-solicitudes-familia.md`.)

## 1. `GET /api/app/inicio/proximos-partidos` ("Tu próximo partido")

Por cada **persona y equipo** de la cuenta, su próximo partido que **aún no ha empezado** en los **próximos 14 días**
(hoy incluido). Un partido cancelado o ya empezado no cuenta.

| Entrada | Quién | Equipo |
| --- | --- | --- |
| `JUGADOR` | el propio jugador (rol `JUGADOR`) | el suyo |
| `FAMILIAR` | una por **hijo activo** (rol `FAMILIAR`), con el nombre del hijo | el del hijo |
| `ENTRENADOR` | una por **equipo que entrena** (rol `ENTRENADOR`) | ese equipo |

- **Invitaciones**: el partido de otro equipo al que el jugador o el hijo está invitado y **autorizado** cuenta como su
  próximo partido si es antes que el de su equipo. La entrada sigue siendo la de su equipo (`equipoId` y `nombreEquipo`
  son los suyos); el equipo que invita va en `convocatoria.equipoInvita`.
- Si un jugador es a la vez familiar (o hijo de sí mismo), **no se repite**: manda la entrada `JUGADOR`.
- **Coordinación y administración no reciben todos los equipos**: solo los suyos como entrenador, si los tienen.
- Como mucho **6 entradas**, por fecha y hora del partido (las de descanso, al final).
- Una cuenta sin ninguna recibe `{ "entradas": [] }`.

```json
{
  "entradas": [
    {
      "tipo": "FAMILIAR",
      "jugadorId": 101,
      "nombreJugador": "Leo García",
      "equipoId": 10,
      "nombreEquipo": "Alevín A",
      "descansa": false,
      "partido": {
        "id": 30, "equipoId": 10, "equipo": "Alevín A", "rival": "CD Rival",
        "escudoRivalUrl": "/api/public/escudos-rivales/4?v=2", "rivalEsPropio": false,
        "local": true, "enCasaElsOms": true, "dia": "2026-11-28", "diaFormateado": "28/11/2026",
        "hora": "11:00", "campo": "Els Oms", "tipo": "LIGA", "cancelado": false,
        "convocado": true, "invitado": false, "ausenciaJustificada": false, "puedeJustificar": true
      },
      "convocatoria": {
        "estado": "CONVOCADO",
        "horaCitacion": "10:15",
        "lugarCitacion": "Puerta del campo",
        "equipaje": { "id": 1, "nombre": "Verde", "camiseta": "verde", "pantalon": "negro", "medias": "verde", "colorHex": "#00AA00" },
        "equipajeMochila": { "id": 2, "nombre": "Blanca", "camiseta": "blanca", "pantalon": "blanco", "medias": "blanca", "colorHex": "#FFFFFF" },
        "equipoInvita": null,
        "numeroConvocados": null,
        "convocatoriaId": null
      }
    },
    {
      "tipo": "ENTRENADOR", "jugadorId": null, "nombreJugador": null, "equipoId": 12, "nombreEquipo": "Benjamín",
      "descansa": false,
      "partido": { "id": 31, "equipo": "Benjamín", "rival": "UD Otro", "hora": "10:00" },
      "convocatoria": { "estado": "PUBLICADA", "horaCitacion": "09:15", "lugarCitacion": "Els Oms", "equipaje": null,
                        "equipajeMochila": null, "equipoInvita": null, "numeroConvocados": 14, "convocatoriaId": 5 }
    },
    {
      "tipo": "JUGADOR", "jugadorId": 100, "nombreJugador": "Ana Pérez", "equipoId": 11, "nombreEquipo": "Infantil",
      "descansa": true, "partido": null, "convocatoria": null
    }
  ]
}
```

`partido` lleva los mismos campos que `PartidoDTO` (el de `GET /api/app/calendario`): `id`, `rival`, `escudoRivalUrl`,
`rivalEsPropio`, `local`, `enCasaElsOms`, `dia`, `hora`, `campo`, `tipo`, `cancelado`... Los campos que `PartidoDTO` aún no
tiene (`rivalVisible`, `tieneFicha`) no salen: se añadirán aquí cuando existan en el DTO.

### Estados de `convocatoria.estado`

| Estado | Para | Qué lleva |
| --- | --- | --- |
| `SIN_CONVOCATORIA` | todos | el partido aún no tiene convocatoria publicada |
| `CONVOCADO` | jugador / familiar | `horaCitacion`, `lugarCitacion`, `equipaje` y `equipajeMochila` (nombre y color; null si no se indicó) |
| `NO_CONVOCADO` | jugador / familiar | hay convocatoria y el jugador no está en ella |
| `AUSENCIA_JUSTIFICADA` | jugador / familiar | la familia ya justificó la ausencia a ese partido (manda sobre los demás estados) |
| `INVITADO` | jugador / familiar | partido de otro equipo con invitación autorizada: `equipoInvita` y los mismos datos de citación que `CONVOCADO` |
| `PUBLICADA` | entrenador | hay convocatoria: `numeroConvocados` y la citación |

### `descansa`

`true` si el equipo **no tiene partido en los 14 días** y la FFCV le da **jornada de descanso** en ese periodo
(`DESCANSOS_EQUIPO`). En ese caso `partido` y `convocatoria` son `null`. Si no hay partido ni descanso, no hay entrada.

### Privacidad

Solo salen los datos de la propia cuenta y de sus hijos (el vínculo lo comprueba `ConvocatoriaPartidoService`, como en el
calendario). **Nunca** sale la lista de convocados ni datos de otros jugadores; al entrenador, solo cuántos van
convocados.

## 2. `GET /api/app/pendientes`

Lo que la cuenta tiene por hacer según sus roles. Una lista de **grupos**; los grupos con 0 **no se devuelven**; las cuentas
solo de familia o jugador reciben `{ "grupos": [] }`. Cada grupo lleva como mucho **5 elementos**; `cantidad` es el
total. `titulo` y `texto` van en el idioma de la cuenta (`es`, `ca` o `en`).

```json
{
  "grupos": [
    {
      "codigo": "LISTAS_SIN_PASAR",
      "titulo": "Listas sin pasar",
      "cantidad": 7,
      "elementos": [
        { "id": 12, "texto": "Alevín A: entrenamiento del lunes 23/11 a las 18:00", "fecha": "2026-11-23",
          "destino": "PASAR_LISTA:12", "diasRetraso": null }
      ]
    },
    {
      "codigo": "RESULTADOS_PENDIENTES",
      "titulo": "Resultados pendientes",
      "cantidad": 1,
      "elementos": [
        { "id": 34, "texto": "Alevín A - CD Rival, sábado 21/11", "fecha": "2026-11-21",
          "destino": "RESULTADO:34", "diasRetraso": 4 }
      ]
    }
  ]
}
```

### Grupos por rol

| Rol | Grupo (`codigo`) | Qué cuenta | `destino` |
| --- | --- | --- | --- |
| `ENTRENADOR` (equipos que entrena) | `LISTAS_SIN_PASAR` | entrenamientos de los **últimos 14 días** que ya han empezado, no cancelados, con la lista sin revisar (`id` = entrenamiento, el mismo id que el push de pasar lista) | `PASAR_LISTA:entrenamientoId` |
| | `CONVOCATORIAS_SIN_PUBLICAR` | partidos de los **próximos 7 días**, sin empezar, no cancelados y sin convocatoria (`id` = partido) | `CONVOCATORIA_NUEVA:partidoId` |
| | `RESULTADOS_PENDIENTES` | partidos de hasta 10 días atrás, terminados (2 h tras el inicio) y sin resultado; con `diasRetraso` | `RESULTADO:partidoId` |
| | `SOLICITUDES_FAMILIA` | solicitudes de familia sin revisar de sus equipos | `SOLICITUDES_FAMILIA` |
| | `JUGADORES_A_LIBERAR` | jugadores suyos que otro equipo invita el mismo día y debe liberar (`id` = invitación) | `INVITACIONES` |
| `COORDINADOR` / `ADMIN_APP` | `CONVOCATORIAS_SIN_PUBLICAR`, `RESULTADOS_PENDIENTES` | **lo mismo que Fin de semana** (`GET /api/app/coordinacion/fin-de-semana`): todos los equipos, con su contador. Sustituyen a los del entrenador | los mismos |
| | `INVITACIONES_POR_APROBAR` | invitaciones pendientes del visto bueno de coordinación | `INVITACIONES` |
| | `INVITACIONES_SIN_LIBERAR` | invitaciones pendientes de su entrenador desde hace **más de 12 h** | `INVITACIONES` |
| | `SOLICITUDES_FAMILIA` | solicitudes de familia de **todos** los equipos | `SOLICITUDES_FAMILIA` |
| `ADMIN_APP` | `MODERACION` | reportes de moderación pendientes | `MODERACION` |
| | `USUARIOS_CADUCADAS` | invitaciones de cuenta caducadas sin activar (`id` = cuenta) | `USUARIOS_CADUCADAS` |
| `RRSS` (o `ADMIN_APP` si **nadie** tiene RRSS) | `IMAGENES_PREVIA` | del **viernes 12:00 al domingo**: la previa del fin de semana sin generar | `IMAGENES_PREVIA:2026-11-27` (el viernes) |
| | `IMAGENES_RESULTADOS` | del **lunes 9:00 al martes**: los resultados del fin de semana anterior sin generar | `IMAGENES_RESULTADOS:2026-11-27` (el viernes) |

Una cuenta con varios roles recibe la unión de sus grupos y un mismo código no se repite: un coordinador que también
entrena recibe los grupos de Fin de semana (todos los equipos), y además sus listas sin pasar y los jugadores a liberar.
Las horas son de Madrid y la regla de RRSS es la de los recordatorios (`ImagenesRedesScheduler`).

Los elementos de `LISTAS_SIN_PASAR`, `CONVOCATORIAS_SIN_PUBLICAR` y `RESULTADOS_PENDIENTES` llevan además `equipoId`,
`equipo`, `rival` (no en las listas) y `hora`: lo que la app necesita para abrir el formulario sin pedir nada más (en los
demás grupos van a `null`).

El `id` de los elementos de imágenes es `null` (el destino lleva la fecha). `diasRetraso` solo viene en
`RESULTADOS_PENDIENTES` (en el resto, `null`).

### Rendimiento

Como mucho una consulta por grupo y ninguna por equipo ni por elemento: los entrenamientos sin revisar, los partidos y las
invitaciones se piden **una vez para todos los equipos** y se filtran por los equipos del entrenador.

## 3. Errores

| Código | Cuándo |
| --- | --- |
| `401` | sin sesión o token que no es un id |
| `403` | cuenta sin ningún rol, o usuario no válido |
| `500` | fallo inesperado (mensaje genérico, sin detalle interno) |
