# API de la app: importar los datos de la FFCV (solo ADMIN_APP)

Lo que el panel web hace en `/admin/ffcv` (partidos) y `/admin/ffcv/clasificaciones`, desde la app. Las rutas **solo
envuelven los mismos servicios** que el panel (`ImportacionFfcvService` e `ImportacionClasificacionesService`): mismos
tipos de fichero, tamaños máximos, mensajes y efectos al confirmar (avisos, enlace de clubes rivales, fecha de la última
importación que sale en Fin de semana).

Código: `AppAdminFfcvController`. Pruebas: `AppAdminFfcvUsuariosControllerTest`.

## Permisos y errores comunes

Solo `ADMIN_APP`. Cualquier otra cuenta (COORDINADOR, ENTRENADOR, RRSS, RETRANSMISION, familias...) recibe **403**; sin
sesión, **401**. La comprobación es `UsuarioAppService.exigirAdminApp` (el rol se mira en la base de datos, no en el token).

Todos los errores van como JSON `{"error": "mensaje"}`:

| Código | Cuándo |
| --- | --- |
| 400 | fichero vacío, demasiado grande, de otro tipo o ilegible; nada que confirmar; vista previa caducada |
| 401 | sin sesión |
| 403 | la cuenta no es ADMIN_APP |
| 500 | error inesperado ("Error al procesar el archivo" / "Error al guardar la importación") |

## Partidos (LstPartidos.csv)

### `POST /api/app/admin/ffcv/partidos/previsualizar`

`multipart/form-data`, campo `archivo` (el `LstPartidos.csv`). No escribe nada. Respuesta `200`: la misma
`PrevisualizacionFfcv` que el panel (`desde`, `hasta`, `filas`, `descansan`, `desaparecidos`, `errores`).

| 400 | Mensaje |
| --- | --- |
| Vacío | `Selecciona el archivo LstPartidos.csv` |
| Más de 1 MB | `El archivo es demasiado grande para ser un listado de partidos` |
| No es un listado válido | el mensaje del servicio (p. ej. cabecera que no es la de la FFCV) |
| Ilegible | `No se pudo leer el archivo` |

### `POST /api/app/admin/ffcv/partidos/confirmar`

Mismo cuerpo que el panel (`ConfirmarFfcvRequest`): la previsualización que devolvió el servidor más las relaciones que
se eligieron para las filas `SIN_EQUIPO`.

```json
{ "previsualizacion": { "desde": "...", "hasta": "...", "filas": [], "descansan": [], "desaparecidos": [], "errores": [] },
  "asignaciones": [] }
```

Respuesta `200`: `ResultadoImportacionFfcv` (`creados`, `actualizados`, `sinCambios`, `conflictosManuales`,
`sinEquipo`, `descansosCreados`, `descansosEliminados`, `avisos`). `400` con `No hay nada que confirmar` si falta la
previsualización, o con el mensaje del servicio si la selección no es válida.

## Clasificaciones (ListadoClasificaciones.xlsx)

El panel guarda la vista previa en la sesión HTTP y confirma esa, sin aceptar datos del navegador. La API de la app no
tiene sesión, así que **el servidor guarda la vista previa en memoria** (`PrevisualizacionesClasificacionesApp`): una por
administrador, **30 minutos**; una nueva sustituye a la anterior y se consume al confirmar (no se puede confirmar dos
veces). Es por instancia (un solo contenedor), como los limitadores.

### `POST /api/app/admin/ffcv/clasificaciones/previsualizar`

`multipart/form-data`, campo `archivo` (`.xlsx`). Respuesta `200`: la misma `PrevisualizacionClasificaciones` que el
panel (`filasLeidas`, `competiciones`, `equipos`, `sinMapeo`, `errores`).

| 400 | Mensaje |
| --- | --- |
| Vacío | `Selecciona el archivo ListadoClasificaciones.xlsx` |
| No acaba en `.xlsx` | `El archivo debe ser un Excel .xlsx (ListadoClasificaciones.xlsx)` |
| Más de 3 MB | `El archivo es demasiado grande para ser un listado de clasificaciones` |
| No es un listado válido | el mensaje del servicio |

### `POST /api/app/admin/ffcv/clasificaciones/confirmar`

```json
{ "equipoIds": [12, 15] }
```

Los equipos marcados (los `equipoId` del panel). Confirma la vista previa guardada. Respuesta `200`:
`ResultadoImportacionClasificaciones` (`importados`, `filas`, `equipos`, `avisos`).

| 400 | Mensaje |
| --- | --- |
| No hay vista previa o ha pasado media hora (o ya se confirmó) | `La vista previa ha caducado: vuelve a subir el archivo` |
| Ningún equipo marcado, o alguno no está en la vista previa | el mensaje del servicio; la vista previa **se conserva** para corregir la selección |

## Clasificación de veteranos (LVCA)

La clasificación de la **Liga Veteranos Ciudad de Alicante** para el equipo **Veteranos** del club. La fuente es la página
https://ligaveteranosalicante.es/scoretable/ **guardada desde el navegador** ("Guardar como… → Página web", un `.html`): la
sube siempre una persona, el servidor **nunca** descarga la página. Es el mismo flujo que las clasificaciones de la FFCV
(vista previa guardada en el servidor, una por administrador, 30 minutos) y el panel web tiene su bloque aparte
"Clasificación de veteranos (LVCA)" en la pantalla de importación de clasificaciones (mismos roles: SUPER y ADMIN).
Código: `ImportacionVeteranosServiceImpl`, `ClasificacionLvcaParser` (Jsoup) y, en la app, `AppAdminFfcvController`.

Qué se importa:

- Solo las tablas en las que aparece el Mutxamel (el nombre del equipo, normalizado sin acentos, puntos ni espacios de más y
  en mayúsculas, empieza por `MUTXAMEL`: "MUTXAMEL C.F.V"). Hoy son la de la Liga y la de "Copa grupo C"; el resto se ignora
  (y ni se valida).
- Una clasificación por competición, con el nombre de la pestaña como `COMPETICION`, asignada al equipo `Veteranos`
  (se busca por nombre, como "Primer Equipo"; si no existe, error claro).
- Al confirmar **sustituye solo esa competición** del equipo en la temporada activa: las demás no se tocan. No hay que
  cambiar el esquema: `CLASIFICACIONES` no tiene ninguna restricción por equipo, así que un equipo puede tener varias.
- Por fila: posición, nombre del equipo tal cual, PJ, PG, PE, PP, GF, GC y puntos; `esNuestro` en la del Mutxamel;
  `JORNADA` = el **máximo de PJ de la tabla** (la web no da la jornada); `fechaImportacion` = ahora.
- Valida que cada fila tiene todos los números y que hay al menos 2 equipos. Si falta algo o el fichero no es esa página:
  `No parece la clasificación de la Liga Veteranos Ciudad de Alicante` (con el detalle si es una tabla incompleta).

Escudos: por cada rival se busca su club en `CLUBES_RIVALES` por nombre normalizado y alias; si no existe se **crea sin
escudo** (para subirlo desde "Escudos de rivales") y se le anota el alias, que es lo que enlaza la fila con el club. Las
imágenes de la página no se leen. El Mutxamel nunca se crea como rival.

### `POST /api/app/admin/ffcv/veteranos/previsualizar`

`multipart/form-data`, campo `archivo` (`.html`). No escribe nada. Respuesta `200`:

```json
{ "equipoId": 9, "equipoNombre": "Veteranos",
  "competiciones": [
    { "nombre": "Liga Veteranos Ciudad de Alicante", "jornada": 5, "posicionNuestra": 8,
      "tabla": [ { "posicion": 1, "equipo": "MARISTAS CAFÉ TAVIRA F.V.", "pj": 5, "pg": 5, "pe": 0, "pp": 0,
                   "gf": 24, "gc": 6, "dif": 18, "puntos": 15, "esNuestro": false } ],
      "clubesACrear": ["AS OKTA"] } ],
  "clubesACrear": ["AS OKTA"] }
```

| 400 | Mensaje |
| --- | --- |
| Vacío | `Selecciona la página guardada de la clasificación de la Liga Veteranos` |
| No acaba en `.html` / `.htm` | `El archivo debe ser la página guardada (.html): en el navegador, Guardar como… → Página web` |
| Más de 3 MB | `El archivo es demasiado grande para ser la clasificación de la Liga Veteranos` |
| No es esa página / tabla incompleta | `No parece la clasificación de la Liga Veteranos Ciudad de Alicante` (+ detalle) |
| El Mutxamel no sale en ninguna tabla | `El Mutxamel no aparece en ninguna clasificación de la página` |
| No existe el equipo | `No existe el equipo "Veteranos": créalo en Equipos antes de importar…` |

### `POST /api/app/admin/ffcv/veteranos/confirmar`

Sin cuerpo: confirma la vista previa guardada. Respuesta `200`:
`{"competiciones": 2, "filas": 15, "nombres": ["Liga Veteranos…", "Copa grupo C"], "clubesCreados": ["AS OKTA", …]}`.
`400` con `La vista previa ha caducado: vuelve a subir el archivo` si no hay o ya se confirmó.

Solo `ADMIN_APP` (403 al resto), como las demás rutas de `/api/app/admin/ffcv`.

### La clasificación pública con varias competiciones

`GET /api/public/equipos/{id}/clasificacion` mantiene sus campos de siempre (`competicion`, `jornada`,
`fechaImportacion`, `clasificacion`), que son los de la **competición principal** (la de más equipos; si empatan, la
primera por nombre), y añade `competiciones`: la lista de todas las del equipo, cada una con esos mismos cuatro campos. Los
equipos de la FFCV tienen una sola; Veteranos, la Liga y la Copa. Las versiones antiguas de la app siguen viendo la
principal.
