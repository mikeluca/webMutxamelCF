# API pública: patrocinadores del club

Una sola lista de patrocinadores para la web (pie de página), la app (home) y los carteles para redes sociales. Vive en
el backend, en `service/src/main/java/com/mikedev/mutxamelcf/model/PatrocinadoresClub.java` (record `Patrocinador`).

## `GET /api/public/patrocinadores`

- Público: sin sesión ni token (queda cubierto por `/api/public/**` en `SecurityConfig`).
- Sin datos personales.
- Cabecera `Cache-Control: public, max-age=3600` (1 hora).
- Devuelve **solo los patrocinadores con `enWeb = true`** (Mercury no sale), en el orden de la web, en JSON:

```json
[
  { "nombre": "Frutas y verduras Juanete", "imagenUrl": "/images/frutasJuanete.jpg",
    "url": "https://www.instagram.com/frutasjuanete/", "circular": false },
  { "nombre": "Aresala", "imagenUrl": "/images/aresala.jpg", "url": null, "circular": false }
]
```

| Campo       | Tipo           | Significado                                                                        |
|-------------|----------------|------------------------------------------------------------------------------------|
| `nombre`    | string         | Nombre del patrocinador (también el texto alternativo del logo).                   |
| `imagenUrl` | string         | Ruta del logo **relativa a la raíz de la web**; la app le antepone `mediaBaseUrl`. |
| `url`       | string \| null | Enlace al pulsar el logo; `null` si no tiene (no se pinta como enlace).            |
| `circular`  | boolean        | Si el logo se muestra redondo.                                                     |

La respuesta no cambia de forma: los campos nuevos (`logoCartel`, `destacadoCartel`, `enWeb`, `ordenCartel`) son internos.

## Los campos de `Patrocinador`

`new Patrocinador(nombre, imagen, url, circular, logoCartel, destacadoCartel, enWeb, ordenCartel)`

| Campo             | Significado                                                                                                              |
|-------------------|--------------------------------------------------------------------------------------------------------------------------|
| `nombre`          | Nombre.                                                                                                                  |
| `imagen`          | Fichero en `mvc/src/main/resources/static/images` (logo de la web); `null` si no tiene imagen web.                       |
| `url`             | Enlace; `null` si no tiene.                                                                                              |
| `circular`        | Logo redondo en la web.                                                                                                  |
| `logoCartel`      | Nombre del PNG blanco con fondo transparente para los carteles; `null` si no sale en carteles.                           |
| `destacadoCartel` | `true`: va **arriba a la izquierda** del cartel (el Ayuntamiento) en vez de en el bloque de abajo.                        |
| `enWeb`           | `true`: sale en el pie de la web y en `GET /api/public/patrocinadores`. `false`: solo en carteles (Mercury).             |
| `ordenCartel`     | Posición en el bloque de abajo del cartel, **independiente del orden de la web** (que es el de `LISTA`).                 |

Dónde está el PNG del cartel: los normales en `service/src/main/resources/redes/logos/patrocinadores-cartel/`; el
**destacado** en `service/src/main/resources/redes/logos/` (el Ayuntamiento es `ayuntamiento-blanco.png`).

El bloque de abajo del cartel lleva los patrocinadores con `logoCartel` y no destacados, por `ordenCartel`, en filas de 4
como máximo centradas; cada logo se encaja (sin deformarlo) en su celda. Orden actual: La Bodega, Frutas Juanete,
Mercury, Unlimited, Aresala, AA Energy, Dental Company.

## Cómo añadir, quitar o reordenar un patrocinador

1. Copia la imagen web en `mvc/src/main/resources/static/images/` (si sale en la web) y/o el PNG blanco sobre
   transparente en `service/src/main/resources/redes/logos/patrocinadores-cartel/` (si sale en carteles).
2. Edita `PatrocinadoresClub.LISTA` (es el ÚNICO sitio): añade `new Patrocinador(...)` con los campos de arriba.
   - Solo web: `logoCartel = null`, `enWeb = true`.
   - Solo carteles (como Mercury): `imagen = null`, `url = null`, `enWeb = false`, con `logoCartel` y un `ordenCartel`.
   - El orden en la web es la posición en `LISTA`; el del cartel es `ordenCartel` (que no se repita entre los del bloque
     de abajo).
   - Para quitar o reordenar en la web, borra o mueve la línea.
3. Nada más: el pie de la web (`footer.html`), la app y los carteles lo leen de ahí. No hay script SQL ni tabla.

Tests: cada imagen web existe en `static/images` (`PublicPatrocinadorControllerTest`), cada `logoCartel` existe en
resources y el orden del cartel (`PatrocinadoresClubTest`), y Mercury no sale ni en la API pública ni en el pie de la web.
