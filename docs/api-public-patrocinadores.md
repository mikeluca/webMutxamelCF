# API pública: patrocinadores del club

Una sola lista de patrocinadores para la web (pie de página) y la app (home). Vive en el backend, en
`mvc/src/main/java/com/mikedev/mutxamelcf/mvc/web/PatrocinadoresClub.java`.

## `GET /api/public/patrocinadores`

- Público: sin sesión ni token (queda cubierto por `/api/public/**` en `SecurityConfig`).
- Sin datos personales.
- Cabecera `Cache-Control: public, max-age=3600` (1 hora).
- Devuelve JSON, en el orden en que se muestran:

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

## Cómo añadir, quitar o reordenar un patrocinador

1. Copia la imagen en `mvc/src/main/resources/static/images/`.
2. Edita `PatrocinadoresClub.LISTA` (es el ÚNICO sitio): añade `new Patrocinador(nombre, fichero, url | null, circular)`
   en la posición deseada; para quitar o reordenar, borra o mueve la línea.
3. Nada más: el pie de la web (`footer.html`) y la app lo leen de ahí. No hay script SQL ni tabla.

Un test comprueba que cada imagen de la lista existe en `static/images`.
