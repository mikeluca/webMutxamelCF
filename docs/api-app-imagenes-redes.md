# API de la app: imágenes para redes sociales

Todo cuelga de `/api/app/imagenes-redes` (JWT de la app). Hay tres cosas: las imágenes de la **previa** y de los
**resultados** del fin de semana (se generan a mano y se guardan), el cartel del **próximo partido del Primer Equipo**
(se genera en el servidor al momento y no se guarda) y los **fondos** de los carteles (se cambian desde el panel web).

## Permisos

**Todas** las rutas de `/api/app/imagenes-redes/**` (las de previa y resultados y las del cartel) son solo para los roles
`RRSS` y `ADMIN_APP`. Cualquier otra cuenta, **también `RETRANSMISION` y `COORDINADOR`**, recibe **403**; sin sesión, 401. Lo
comprueban `ImagenesRedesServiceImpl` y `CartelPrimerEquipoServiceImpl` con `EquipoGestionDao.esRrssOAdmin`.

## Previa y resultados

| Ruta | Qué hace |
| --- | --- |
| `GET /api/app/imagenes-redes?fecha=yyyy-MM-dd` | Las imágenes del fin de semana de `fecha` (por defecto el actual) y los textos sugeridos |
| `GET /api/app/imagenes-redes/{id}/png` | El PNG de una imagen |
| `POST /api/app/imagenes-redes/regenerar?tipo=PREVIA\|RESULTADOS&fecha=yyyy-MM-dd` | "Generar ahora" / "Volver a generar": sustituye las imágenes de ese tipo y fin de semana con los datos que haya |

Ya **no se generan solas**: se generan a mano y **hacerlo no avisa a nadie**.

### Recordatorios

El `ImagenesRedesScheduler` solo recuerda generarlas (Europe/Madrid):

| Cuándo | Qué fin de semana | Se recuerda si… |
| --- | --- | --- |
| Viernes 12:00 | El que empieza ese viernes (PREVIA) | no hay imágenes de PREVIA generadas para ese fin de semana |
| Lunes 09:00 | El anterior (RESULTADOS) | no hay imágenes de RESULTADOS generadas para ese fin de semana |

El recordatorio es bandeja + push, en el idioma de cada cuenta, para las cuentas activas con rol `RRSS` (ya no a `RETRANSMISION`); si no
hay ninguna, para las de `ADMIN_APP`. Si ya están generadas no se manda nada. Textos y datos del push (`tipo`,
`pantalla`, `tipoImagen`, `desde`) en `docs/avisos.md`, sección 9.

## Cartel "Próximo partido del Primer Equipo"

Se genera en el servidor (Java2D) cada vez que se pide y **no se guarda**. Diseño y medidas:
`docs/diseno-cartel-primer-equipo/`.

### Qué partido

El **próximo partido del Primer Equipo** (el primero por fecha y hora). Se saltan los cancelados y los **ya terminados**: con resultado (goles o resultado guardado), con el directo en FINAL o empezados hace más de 3 horas.

- Si su fecha y hora de inicio aún no han llegado → `disponible: true`.
- Si el primero que queda **está en juego** (empezó hace menos de 3 horas y no tiene resultado) → `YA_EMPEZADO`. Un partido sin hora cuenta como empezado desde las
  00:00 de su día.
- Si no hay ningún partido futuro (o solo cancelados) → `SIN_PARTIDO`.

### `GET /api/app/imagenes-redes/partido-primer-equipo`

```json
{
  "disponible": true,
  "motivo": null,
  "partidoId": 30,
  "local": true,
  "rival": "C.D. Salesianos Alicante",
  "escudoRivalUrl": "/api/public/escudos-rivales/40?v=2",
  "dia": "2026-11-21",
  "hora": "17:30",
  "campo": "Camp Nou Los Olmos (Mutxamel)",
  "competicion": "Primera FFCV, Grup 4",
  "jornada": 11,
  "tipo": "LIGA",
  "textoPublicacion": {
    "es": "Próximo partido: Mutxamel C.F. – C.D. Salesianos Alicante. Sábado 21/11 a las 17:30, Camp Nou Los Olmos (Mutxamel). ¡Os esperamos! #somMTX #mareaAzul #MutxamelCF",
    "ca": "Pròxim partit: Mutxamel C.F. – C.D. Salesianos Alicante. Dissabte 21/11 a les 17:30, Camp Nou Los Olmos (Mutxamel). Us esperem! #somMTX #mareaAzul #MutxamelCF"
  }
}
```

| Campo | Significado |
| --- | --- |
| `disponible` | `false` si no se puede generar el cartel; entonces solo vienen `disponible` y `motivo` (el resto, `null`) |
| `motivo` | `SIN_PARTIDO` o `YA_EMPEZADO` (`null` si está disponible) |
| `local` | `true` si el Mutxamel juega en casa |
| `escudoRivalUrl` | Ruta relativa del escudo del rival (como en la ficha), o `null` si no tiene (o el rival es un filial nuestro) |
| `hora` | `HH:mm` tal como está en el partido; puede ser `null` |
| `campo` | En casa y jugando en Els Oms (`CampoCartel.esElsOms`), `Camp Nou Los Olmos (Mutxamel)`; si no (fuera o en casa en otro campo, p. ej. `Hogar Provincial`), el campo del partido recortado (`Les Costeres Gata`) |
| `competicion` | `La Nostra Copa`, `Amistoso`, `Torneo` o, en liga, la competición del Primer Equipo en la FFCV (`null` si no se conoce) |
| `jornada` | Solo si el partido tiene jornada (importada de la FFCV) |
| `tipo` | `LIGA`, `COPA`, `AMISTOSO` o `TORNEO` |
| `textoPublicacion` | Para "Copiar texto", en castellano y catalán. **La app quita los hashtags** si están desmarcados. Sin hora o sin campo se omiten esas partes |

### `GET /api/app/imagenes-redes/partido-primer-equipo/png`

`?formato=PUBLICACION|HISTORIA&idioma=es|ca&racha=true&qr=true&hashtags=true` → `image/png`, `Cache-Control: no-store`.

| Parámetro | Valores | Por defecto |
| --- | --- | --- |
| `formato` | `PUBLICACION` (1080×1350) o `HISTORIA` (1080×1920) | `PUBLICACION` |
| `idioma` | `es` o `ca` | `es` |
| `racha` | `true`/`false`: los últimos 5 resultados (G/E/P) | `true` |
| `qr` | `true`/`false`: QR a https://mutxamelcf.es con "Descarga la app" | `true` |
| `hashtags` | `true`/`false`: `#somMTX #mareaAzul #MutxamelCF` | `true` |

Respuestas de error:

- **409** si no está disponible, con `{"codigo": "SIN_PARTIDO" | "YA_EMPEZADO", "mensaje": "..."}`.
- **400** si un parámetro no es válido (formato o idioma desconocido, o un booleano que no lo es).
- **403** / **401** como el resto.

Lo que cambia según los parámetros:

- **Racha**: los mismos datos que la forma de la ficha del partido (`forma`, últimos 5 con resultado) y los colores de la
  imagen de compartir de la app (G `#2E9E4F`, E `#8A949B`, P `#D64545`), del más antiguo al más reciente. Con menos de 5
  partidos salen los que haya; sin ninguno, no hay racha.
- **Opcionales**: si se desmarcan no queda hueco. La fila de abajo (racha + hashtags a la izquierda, QR a la derecha) se
  centra con lo que quede y, si no queda nada, no se dibuja.
- **Sin escudo del rival**: su nombre en grande en su hueco (hasta 3 líneas) y nada debajo.
- No sale ningún nombre ni foto de jugadores.
- El cartel del **Filial** tiene sus propias rutas (abajo), con el mismo contrato.


## Cartel "Próximo partido del Filial"

Mismo contrato, permisos (solo `RRSS` y `ADMIN_APP`; RETRANSMISION y COORDINADOR 403), parámetros, respuestas (`200`, `409` con
código `SIN_PARTIDO` | `YA_EMPEZADO`, `400`) y regla de qué partido toca que el del Primer Equipo; solo cambia el equipo
(el de nombre `Filial`), el diseño y el fondo:

- `GET /api/app/imagenes-redes/partido-filial` → el mismo JSON que `partido-primer-equipo`. El `textoPublicacion` usa
  "Mutxamel C.F. “B”" como nombre del equipo.
- `GET /api/app/imagenes-redes/partido-filial/png?formato=PUBLICACION|HISTORIA&idioma=es|ca&racha=&qr=&hashtags=` →
  `image/png`, `Cache-Control: no-store`.

La racha es la de los últimos 5 partidos del Filial. Diseño, medidas y fondo en `docs/diseno-cartel-filial/`.

Lo común a los dos carteles (elección del partido, campo, lado local/visitante, racha, escudo del rival, fondo de la
tabla o de resources, texto para publicar y validación) está en `CartelPartidoServiceBase`; cada cartel solo aporta su
equipo, su renderizador y su fondo por defecto.
## Patrocinadores en los carteles

Salen de `PatrocinadoresClub` (la única lista). Ver `docs/api-public-patrocinadores.md`.

## Fondos de los carteles (panel web)

Administración deportiva → **Fondos de carteles** (`/admin/fondos-carteles`, roles `SUPER` y `ADMIN`, como Equipajes).
Por cada cartel muestra el fondo actual, deja subir uno nuevo y "Restaurar el de por defecto".

- **Primer Equipo** y **Filial**: los dos editables, cada uno con su fondo por defecto (`redes/cartel-primer-equipo/fondo.jpg` y `redes/cartel-filial/fondo.jpg`).
- Se sube un **JPG o PNG** de **hasta 5 MB** y de **al menos 1080×1350**; se guarda **tal cual** (con su ancho y alto) en
  `IMAGENES_REDES_FONDOS`. El cartel lo escala y recorta al centro ("cover") para cubrir el lienzo.
- Errores claros: "La imagen pesa más de 5 MB", "La imagen es demasiado pequeña: mide 800×1000 y el mínimo es
  1080×1350 píxeles", "Solo se admiten imágenes JPG o PNG", "El archivo no es una imagen válida (usa JPG o PNG)".
- "Restaurar" borra la fila y vuelve el fondo de por defecto
  (`service/src/main/resources/redes/cartel-primer-equipo/fondo.jpg`, 1075×1345).
- El generador usa el fondo de la tabla si existe y, si no, el de resources.
- Si el fondo es muy claro arriba o abajo, el cartel añade un degradado negro suave (como mucho el 35 %) para que se
  lea el texto blanco.

Script SQL (ejecutar a mano en DEV y PRO antes de desplegar): `sql/2026-11-19_imagenes_redes_fondos.sql`.

El límite de subida de Spring pasa de 5 MB a 6 MB (`spring.servlet.multipart.max-file-size`) para que el panel pueda
dar su propio mensaje con los archivos de entre 5 y 6 MB; por encima de eso el panel también vuelve a la pantalla con el
mismo mensaje.
