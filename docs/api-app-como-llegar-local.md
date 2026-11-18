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
