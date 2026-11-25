# Cómo llegar: el campo `local` en las respuestas (contrato para la app)

El botón "Cómo llegar" se muestra siempre que el partido tiene campo. El campo `local` (boolean, `true` si el Mutxamel
juega en casa) sirve para que, en los partidos en casa, la app envíe a los mapas "Poliesportiu Municipal de Mutxamel" en
lugar del texto del campo. Sale de `PARTIDOS.ES_LOCAL` (el mismo criterio que usa la ficha del partido: `ES_LOCAL` nulo
o 1 = local). No hace falta ningún script SQL. El campo mostrado (`campo`) no cambia.

## Endpoints que lo traen

| Pantalla de la app | Endpoint | DTO | Campo |
|---|---|---|---|
| Partidos públicos (últimos de un equipo) | `GET /api/public/partidos` | `PartidoDTO` | `local` |
| Resultados públicos | `GET /api/public/resultados`, `GET /api/public/resultados/primer-equipo` | `ResultadoDTO` | `local` |
| Partidos sin convocatoria (app) | `GET /api/app/partidos/sin-convocatoria` | `PartidoDTO` | `local` |
| Partido creado/editado desde la app | `POST /api/app/partidos`, `PUT /api/app/partidos/{id}` | `PartidoDTO` | `local` |
| Calendario del jugador, de la familia y de gestión | `GET /api/app/calendario` (`partidos[]`) | `CalendarioResponse` con `PartidoDTO` | `local` |
| Convocatoria, detalle (entrenador / gestión) | `GET /api/app/convocatorias/{id}` y `GET /api/app/convocatorias` | `ConvocatoriaResponse` | `local` (**nuevo**) |
| Convocatoria, vista de la familia | `GET /api/app/partidos/{id}/convocatoria` | `ConvocatoriaPartidoResponse` | `local` |
| Ficha del partido | `GET /api/public/partidos/{id}/ficha` | `FichaPartidoDTO` | `mutxamelLocal` (en la cabecera) |

## Qué cambió

Solo faltaba en `ConvocatoriaResponse` (el detalle y la lista de convocatorias del entrenador). Ahora lleva `local`,
tomado del partido vinculado a la convocatoria (`PARTIDO_ID`); si la convocatoria no tiene partido vinculado, `true`.
El resto de DTO ya lo traían. Es un cambio aditivo: las versiones antiguas de la app lo ignoran.

Si el partido no tiene `ES_LOCAL` informado (partidos antiguos), se considera local (`true`), igual que en la ficha.

## `enCasaElsOms`: no todos los partidos de casa se juegan en Els Oms

Algunos partidos en casa se juegan en otro campo (p. ej. el Hogar Provincial). Por eso las respuestas llevan, junto a
`local`, el campo `enCasaElsOms` (boolean): `true` solo si el partido es local **y** se juega en Els Oms. Es aditivo:
las apps antiguas lo ignoran. No hace falta SQL. Va en los mismos DTO de la tabla de arriba: `PartidoDTO`,
`ResultadoDTO`, `CalendarioResponse` (sus `PartidoDTO`), `ConvocatoriaResponse`, `ConvocatoriaPartidoResponse` y la
cabecera de la ficha (`FichaPartidoDTO.Cabecera.enCasaElsOms`, junto a `mutxamelLocal`).

### Regla "se juega en Els Oms" (`CampoCartel.esElsOms(campo)`)

La app debe usar la misma regla si necesita decidirlo a partir del texto del campo:

1. Campo nulo o vacío → `true` (partidos antiguos o sin campo: se mantiene el comportamiento de siempre).
2. Se normaliza el texto: minúsculas, sin acentos (`Òms` → `oms`), los puntos pasan a espacio y los espacios se
   simplifican.
3. `true` si contiene la **palabra completa** `olmos` o `oms` (no cuenta si va pegada a otras letras o cifras).
4. En cualquier otro caso → `false`.

| Campo | Resultado |
|---|---|
| `Camp Nou Los Olmos Mutxamel F-11`, `Camp Municipal Els Oms`, `C.F. Els Oms`, `CAMP NOU LOS ÓLMOS` | true |
| (vacío o nulo) | true |
| `Hogar Provincial`, `Campo Mpal. de Mutxamel`, `Romsa`, `Olmosa` | false |

`enCasaElsOms = local && esElsOms(campo)`. Si `ES_LOCAL` es nulo se considera local, igual que `local`.
