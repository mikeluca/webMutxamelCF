# Juego de Mutxigol: ranking, perfil y puntuaciones

La app (1.0.16) tiene una pestaña con tres minijuegos: **Penaltis** (`PENALTIS`), **Toques** (`TOQUES`) y **Atrapa balones**
(`ATRAPA`).

- **Cualquiera puede jugar**, con o sin cuenta.
- **Solo las cuentas de la app guardan puntuación y salen en el ranking**, siempre con un **alias**, nunca con su nombre
  real (hay menores). El nombre y el email de la cuenta solo los ven los administradores en la moderación.
- Cualquier rol de la app puede jugar y guardar (una cuenta **sin ningún rol**, la de una familia pendiente de aprobar, no:
  como en el resto de `/api/app/**`, ver [permisos-roles-app.md](permisos-roles-app.md)).

SQL: `sql/2026-11-24_juego_mutxigol.sql` (`JUEGO_PERFILES`, `JUEGO_PUNTUACIONES`).

## Resumen de rutas

| Ruta | Acceso | Qué hace |
| --- | --- | --- |
| `GET /api/public/juego/ranking?juego=&periodo=` | Sin sesión | Los 50 mejores de un juego |
| `GET /api/app/juego/perfil` | Con sesión | Alias, equipación, mejores marcas y posiciones |
| `PUT /api/app/juego/perfil` | Con sesión | Cambiar alias y/o equipación |
| `POST /api/app/juego/puntuacion` | Con sesión | Guardar una partida |
| `GET /api/app/admin/juego/perfiles?alias=` | ADMIN_APP | Listar perfiles (con la cuenta) |
| `GET /api/app/admin/juego/perfiles/{usuarioId}/puntuaciones` | ADMIN_APP | Últimas 100 partidas del perfil |
| `PUT /api/app/admin/juego/perfiles/{usuarioId}/alias` | ADMIN_APP | Cambiar el alias |
| `PUT /api/app/admin/juego/perfiles/{usuarioId}/oculto` | ADMIN_APP | Ocultar o mostrar en el ranking |
| `DELETE /api/app/admin/juego/puntuaciones/{id}` | ADMIN_APP | Borrar una puntuación |
| `DELETE /api/app/admin/juego/perfiles/{usuarioId}/puntuaciones` | ADMIN_APP | Borrar todas las del perfil |

Los errores llevan `{"error": "mensaje"}` y, en los conflictos del juego, también `"codigo"`.

## La semana

El periodo `SEMANA` va de **lunes 00:00 a domingo 23:59:59, en hora de Madrid** (`Europe/Madrid`). Las fechas de las
puntuaciones se guardan en hora de Madrid (como el resto de fechas del backend). `HISTORICO` no tiene límite.

## Ranking público

`GET /api/public/juego/ranking?juego=PENALTIS&periodo=SEMANA`

- `juego`: `PENALTIS`, `TOQUES` o `ATRAPA` (obligatorio). `periodo`: `SEMANA` (por defecto) o `HISTORICO`.
- **Una fila por cuenta**, con su mejor marca del periodo (si repite puntos, la más antigua). Orden: más puntos primero;
  a igualdad, la marca más antigua y luego el id de cuenta.
- **Excluye** los perfiles ocultos, los que no tienen alias y las cuentas desactivadas o eliminadas.

```json
[
  { "posicion": 1, "alias": "Mutxigol10", "puntos": 14, "fecha": "2026-11-24T18:03:12" },
  { "posicion": 2, "alias": "Pepe-Gol", "puntos": 12, "fecha": "2026-11-23T10:41:55" }
]
```

400 si el juego o el periodo no valen.

## Perfil

`GET /api/app/juego/perfil`

```json
{
  "alias": "Mutxigol10",
  "equipacion": "AZUL",
  "oculto": false,
  "juegos": {
    "PENALTIS": {
      "semana":    { "puntos": 12, "fecha": "2026-11-24T18:03:12", "posicion": 3 },
      "historico": { "puntos": 15, "fecha": "2026-11-10T09:00:00", "posicion": 7 }
    },
    "TOQUES": { "semana": null, "historico": null },
    "ATRAPA": { "semana": null, "historico": null }
  }
}
```

- Sin perfil todavía: `alias: null`, `equipacion: "AZUL"` y todas las marcas a `null`.
- `posicion` es la que ocupa su mejor marca en el ranking (**también si está fuera del top 50**). Es `null` si el perfil
  está oculto (no sale en el ranking).

`PUT /api/app/juego/perfil` con `{ "alias": "Mutxigol10", "equipacion": "ROSA" }` (al menos uno; el otro se queda como estaba).
Crea el perfil si no existía. Devuelve el perfil como el `GET`.

| Código | Cuándo |
| --- | --- |
| 400 | Falta alias y equipación, alias no válido (ver abajo) o equipación distinta de `AZUL` / `ROSA` |
| 409 `ALIAS_EN_USO` | "Ese alias ya está en uso" |

### Reglas del alias

- De **3 a 15 caracteres**: letras (con acentos), números, espacio, guion (`-`) y guion bajo (`_`). Se quitan los
  espacios de los extremos y los dobles.
- **No puede parecer un email** (`@`, "arroba", gmail, hotmail, outlook, yahoo, icloud, "punto com"...) **ni un teléfono**
  (6 o más cifras).
- **No puede llevar palabras no permitidas**: lista básica en castellano y valenciano en
  `service/src/main/resources/juego/palabras-no-permitidas.txt`. La comprobación ignora mayúsculas, acentos, espacios y
  guiones, y cambia las cifras parecidas a letras (`0=o 1=i 3=e 4=a 5=s 7=t`). Las palabras de 6 o más letras no pueden ir
  ni dentro de otra; las más cortas, solo como palabra entera del alias.
- **Ampliar la lista sin recompilar**: pon más palabras (una por línea, `#` para comentarios) en un fichero y apunta a él con
  `juego.alias.palabras-extra=/ruta/palabras.txt` (o la variable de entorno `JUEGO_ALIAS_PALABRAS_EXTRA`). Se vuelve a leer
  solo cuando el fichero cambia, sin reiniciar.
- **Único** sin distinguir mayúsculas ni acentos ("Mútxi" y "MUTXI" son el mismo): 409 `ALIAS_EN_USO`. Lo garantiza
  `JUEGO_PERFILES.ALIAS_NORMALIZADO` (única).

## Guardar una puntuación

`POST /api/app/juego/puntuacion` con `{ "juego": "PENALTIS", "puntos": 12, "duracionMs": 45000 }` → **201**

```json
{ "juego": "PENALTIS", "puntos": 12, "recordSemana": true, "recordHistorico": false,
  "posicionSemana": 3, "posicionHistorico": 7 }
```

- `recordSemana` / `recordHistorico`: la partida supera la mejor marca anterior de la cuenta en ese periodo (la primera
  con puntos también lo es). Las posiciones son las de la mejor marca de la cuenta **después** de guardar (`null` si el
  perfil está oculto).

| Código | Cuándo |
| --- | --- |
| 400 | Juego desconocido, faltan datos o la puntuación no es plausible (**no se guarda**) |
| 409 `ALIAS_REQUERIDO` | La cuenta no tiene alias: "Elige un alias para guardar tu puntuación…". La app lo pide y reintenta |
| 429 | Demasiados envíos (ver abajo) |

### Plausibilidad

Constantes en `JuegoServiceImpl`. Si no se cumple algo, 400 y no se guarda:

- `puntos` entre **0 y 999**.
- `duracionMs` entre **3 000 (3 s)** y **7 200 000 (2 h)**.
- **Ritmo máximo** por juego, con un **margen del 20 %**: `puntos ≤ floor(ritmo × segundos × 1,2)`.

| Juego | Ritmo máximo | Ejemplo (45 s) |
| --- | --- | --- |
| `PENALTIS` | 1 cada 1,5 s | 36 puntos |
| `TOQUES` | 5 por segundo | 270 puntos |
| `ATRAPA` | 3 por segundo | 162 puntos |

### Límite de envíos

Por cuenta, contando las partidas guardadas: **como mucho 1 cada 5 segundos y 300 al día** (día natural en hora de Madrid).
Si se pasa: **429**. Los envíos rechazados por 400 o 409 no cuentan.

## Moderación

Panel web: **Administración de la App › Juego Mutxigol** (`/admin/juego-mutxigol`), para `SUPER` y `ADMIN` como el resto del
menú. Lista los perfiles (alias, cuenta con nombre y email, mejores marcas, partidas y estado) con búsqueda por alias, y
permite cambiar el alias, ocultar o mostrar en el ranking y borrar una puntuación o todas las de un perfil (con confirmación).

La app lo hace con `/api/app/admin/juego/**` (solo `ADMIN_APP`, 403 al resto):

- `GET /perfiles?alias=` → lista de `{ usuarioId, alias, equipacion, oculto, nombre, email, fechaAlta, partidas,
  mejorPenaltis, mejorToques, mejorAtrapa }` (hasta 500, los más recientes primero).
- `PUT /perfiles/{usuarioId}/alias` con `{ "alias": "Nuevo" }`: las mismas reglas del alias (400, 409 `ALIAS_EN_USO`).
- `PUT /perfiles/{usuarioId}/oculto` con `{ "oculto": true }`: oculta (o muestra con `false`) sin borrar nada.
- `DELETE /puntuaciones/{id}` → 204 (404 si no existe). `DELETE /perfiles/{usuarioId}/puntuaciones` → `{ "borradas": n }`.
- 404 si el perfil no existe.

## Borrado de cuenta

Al eliminar la cuenta (por su titular o por el sistema) se **borran su perfil y sus puntuaciones**
(`CuentaAppServiceImpl`). Desaparece del ranking al instante.

## Privacidad

La [política de privacidad](/politicaPrivacidad) (apartado de la aplicación móvil) explica que el juego guarda un alias
elegido por el usuario y sus puntuaciones, que se muestran en el ranking solo con ese alias y se borran al eliminar la cuenta.
