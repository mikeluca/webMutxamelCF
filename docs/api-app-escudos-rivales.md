# Escudos de los clubes rivales (todas las categorías)

Un registro por **club** rival (no por equipo ni por liga): el Alicante A, el Alicante B y el Alicante infantil son el mismo
club y comparten escudo. Los registros se crean solos a partir de los datos ya importados (clasificaciones de la FFCV y
partidos); los escudos los sube a mano el panel. **Nunca se accede a ninguna web de la FFCV.**

Script: `sql/2026-11-17_escudos_rivales.sql` (tablas `CLUBES_RIVALES` y `CLUBES_RIVALES_ALIAS`, `PARTIDOS.CLUB_RIVAL_ID` y
`CLASIFICACIONES.CODIGO_CLUB`). Solo añade tablas y columnas nulas: el código anterior sigue funcionando.

## 1. Contrato para la app

### Endpoint del escudo

```
GET /api/public/escudos-rivales/{id}
```

- Público, sin sesión y sin token (como el resto de `/api/public/**`; sin CSRF). La app es nativa: no hay CORS que configurar.
- `200`: el PNG (`Content-Type: image/png`), ya reducido a 256 px por el lado largo y con la transparencia conservada.
- `404`: el club no existe o no tiene escudo (se le quitó).
- `Cache-Control: max-age=31536000, public, immutable`. Es seguro porque la URL que da la API lleva la versión del escudo
  (`?v=N`), que sube con cada cambio: un escudo nuevo es otra URL.

### URL en las respuestas

Todas las URL de escudo son **rutas relativas a la URL base del servidor** (la misma que la app ya usa para la API):

```
/api/public/escudos-rivales/{id}?v={version}
```

`null` si el club no tiene escudo (o el partido no tiene club rival enlazado). La app la cachea por URL completa: no hace
falta pedir nada más para saber si el escudo cambió.

### `escudoRivalUrl` y `rivalEsPropio` (partidos)

| Campo | Tipo | Significado |
|---|---|---|
| `escudoRivalUrl` | texto o `null` | Escudo del club rival del partido (ruta relativa) |
| `rivalEsPropio` | boolean | `true` si el rival es **un equipo de nuestro club** (un filial: "Mutxamel C.F. B", "C.F. Mutxamel"...). La app pone entonces **nuestro** escudo; en ese caso `escudoRivalUrl` es `null` |

Un partido contra un equipo nuestro nunca tiene club rival. Se reconoce por el nombre del rival normalizado
(`MUTXAMEL CF` o `CF MUTXAMEL`, solos o seguidos de más texto): "Atlético Mutxamel", "C.F. Monnegre de Mutxamel" o
"Club Mutxamel Benifutsal" **no** son nuestros.

Aparecen en:

| Respuesta | Dónde |
|---|---|
| Portada del Primer Equipo `GET /api/public/resultados/primer-equipo` | en el objeto (`ResultadoDTO`) |
| Resultados de cualquier equipo `GET /api/public/resultados` | en cada elemento (`ResultadoDTO`) |
| Partidos de un equipo `GET /api/public/partidos?equipoId=` y el calendario de la app (`PartidoDTO`) | en cada partido |
| Alta y edición de partidos `POST/PUT /api/app/partidos` (`PartidoDTO`) | en el partido devuelto (ya enlazado con su club) |
| Ficha `GET /api/public/partidos/{id}/ficha` | `cabecera.escudoRivalUrl`, `cabecera.rivalEsPropio` y los mismos dos campos en cada elemento de `forma` |

No llevan estos campos (solo el nombre del rival, como antes): el estado de la retransmisión, las convocatorias, las
invitaciones y el panel de fin de semana de coordinación.

### `escudoUrl` (clasificaciones)

`GET /api/public/equipos/{id}/clasificacion` y la clasificación de cada temporada del historial
(`HistorialTemporadaDTO`): cada fila de la tabla lleva `escudoUrl` (ruta relativa o `null`) y `esPropio` (boolean).

- Es `null` en la fila **nuestra** (`esNuestro: true`) y en las de nuestro club: la app pone nuestro escudo con `esNuestro`.
- `esPropio` es `true` si la fila es de nuestro club: la marcada `esNuestro` (`ES_NUESTRO = 1`), cualquiera con nuestro
  código de club de la FFCV (`ffcv.club.codigo`, 3010: los **filiales**) o cuyo nombre normalizado es el de nuestro club
  ("Mutxamel C.F. B", "C.F. Mutxamel"; "Atlético Mutxamel" o "Club Mutxamel Benifutsal" no lo son). La app pone entonces
  **nuestro escudo** también en las filas de los filiales, cuyo `escudoUrl` sigue siendo `null`. En una fila rival es
  `false`.
- El club de la fila se busca por el **código de club de la FFCV** (`CLASIFICACIONES.CODIGO_CLUB`, que se rellena al
  importar el Excel) y, en las filas antiguas sin código, por el nombre normalizado.
- En la app no existe una pantalla "Mis clasificaciones" en este backend: lo que se sirve es la clasificación pública por
  equipo y la del historial, que son las dos de arriba.

### Rendimiento

Ninguna consulta de la app hace consultas extra por fila: el club se une en la propia consulta del partido o de la
clasificación y solo se leen `ID`, `ESCUDO_VERSION` y si hay escudo (`ESCUDO IS NOT NULL`). **El BLOB nunca se lee** fuera
de `GET /api/public/escudos-rivales/{id}`.

## 2. Cómo se crean los clubes

Clave del club: el **código de club de la FFCV** (`CODIGO_FFCV`, columna "Club" del Excel de clasificaciones) cuando se
conoce; si no, el **nombre normalizado** (tabla de alias).

**Normalización** (`NombreClub.normalizar`): mayúsculas, sin acentos (`Ñ` y `Ç` como `N` y `C`), sin puntos, comillas,
guiones ni paréntesis, espacios colapsados y sin el sufijo de equipo: una letra suelta al final, entre comillas o no.

| Nombre | Normalizado |
|---|---|
| `C.D. Alicante 'A'` | `CD ALICANTE` |
| `CD Alicante B` | `CD ALICANTE` |
| `Atlético Mutxamel` | `ATLETICO MUTXAMEL` |

El club se crea con el nombre del Excel sin la letra del equipo (`C.D. Alicante`). Si ese nombre ya existe en otro club,
se le añade ` (código)` para que sea único.

**Nunca se crea un club rival para nuestro propio club**: filas con código `3010` (`ffcv.club.codigo`), filas
`ES_NUESTRO = 1` y rivales cuyo nombre normalizado es el de nuestro club o empieza por él.

| Cuándo | Qué hace |
|---|---|
| Importar clasificaciones (Excel de la FFCV) | Guarda el código de club de cada fila en `CLASIFICACIONES.CODIGO_CLUB`. Para cada fila que no es nuestra: si hay club con ese código, añade el alias si falta; si no, busca por alias (y le pone el código si no lo tenía); si tampoco, lo crea con su código y su alias |
| Crear un partido (panel, app o importación del calendario FFCV) o cambiar su rival | `CLUB_RIVAL_ID` = el club de su alias; si no existe, se crea (sin código) con su alias |
| "Generar clubes" (panel) | Hace lo anterior con las clasificaciones de la temporada activa y con todos los partidos sin club de la temporada activa y las anteriores. Idempotente |

**Unión automática.** Si al importar con código resulta que un club creado antes por nombre (sin código) es el mismo que
otro que ya tenía ese código, se **unen** (alias y partidos pasan al que tiene el código; se conserva el escudo del que lo
tenga). Si el club creado por nombre es el único, simplemente recibe el código.

## 3. Pantalla del panel: Escudos de rivales

`/admin/escudos-rivales` (menú de administración, solo SUPER y ADMIN, como el resto de `/admin/**` sensible).

- Arriba: **"X de Y clubes con escudo"** y el botón **Generar clubes** (idempotente; muestra cuántos clubes ha creado, cuántos
  alias ha añadido y cuántos partidos ha enlazado).
- Lista: miniatura (o un escudo genérico), nombre, código FFCV, ligas y equipos nuestros en los que aparece (por las
  clasificaciones de la temporada activa y los partidos), nº de partidos y alias. Filtros **Sin escudo** (el de por defecto) y
  **Todos**, y buscador por nombre (sin acentos ni mayúsculas; también por alias).
- Por club:
  - **Subir / cambiar escudo**: PNG o JPG, máximo 2 MB. Se valida que sea una imagen de verdad, se reduce a 256 px por el
    lado largo (sin ampliar las pequeñas) conservando la transparencia y se guarda como PNG. Sube `ESCUDO_VERSION`.
  - **Quitar escudo**: también sube `ESCUDO_VERSION`.
  - **Renombrar**: el nombre es único.
  - **Unir con otro club**: sus alias y sus partidos pasan al destino y el origen se borra. El código FFCV pasa al destino
    si este no tenía. Si los dos tienen escudo **se conserva el del destino** (la confirmación lo avisa). No se pueden unir
    dos clubes con códigos FFCV distintos.
  - **Borrar**: solo si no tiene partidos ni aparece en clasificaciones.

Borrar un partido desde el panel (en cascada) **no** borra su club rival.

## 4. Pasos tras desplegar

1. El script `sql/2026-11-17_escudos_rivales.sql` ya está aplicado (con la ampliación `CODIGO_FFCV` / `CODIGO_CLUB`).
2. Desplegar el backend.
3. **Volver a importar el Excel de clasificaciones** (`/admin/ffcv/clasificaciones`) para rellenar `CODIGO_CLUB` de las filas
   ya guardadas; con la importación se crean además los clubes de todas las ligas importadas.
4. En **Escudos de rivales**, pulsar **Generar clubes**: crea los que falten (filas antiguas sin código y partidos) y enlaza
   los partidos existentes.
5. Revisar el filtro **Sin escudo**, unir los duplicados que haya (mismo club con nombres muy distintos) y subir los escudos.
6. La app solo tiene que leer `escudoRivalUrl` / `escudoUrl` / `rivalEsPropio`: no hay nada más que configurar.
