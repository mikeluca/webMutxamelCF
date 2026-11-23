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
