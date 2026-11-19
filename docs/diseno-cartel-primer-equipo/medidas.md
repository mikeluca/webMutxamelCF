### publicacion 1080x1350
| Elemento | x | y | ancho | alto |
|---|---|---|---|---|
| logo ayuntamiento | 40 | 34 | 185 | 92 |
| jornada | 384 | 53 | 236 | 54 |
| logo competición | 779 | 37 | 261 | 86 |
| título | 55 | 146 | 971 | 155 |
| fecha (día y fecha) | 80 | 339 | 202 | 90 |
| hora | 451 | 362 | 124 | 45 |
| campo (en casa; fuera llega hasta x=1040, anclado a la línea de la fecha) | 745 | 317 | 255 | 135 |
| escudo izquierda | 174 | 474 | 262 | 262 |
| VS | 485 | 558 | 110 | 95 |
| escudo derecha | 644 | 474 | 262 | 262 |
| nombre izquierda | 60 | 748 | 460 | 98 |
| nombre derecha | 560 | 748 | 460 | 98 |
| bloque racha+hashtags | 167 | *calculada* | 572 | 135 |
| QR + texto | 773 | *calculada* | 140 | 139 |
| bloque patrocinadores | *centrado (filas de hasta 1000)* | *calculada, anclado abajo* | ≤ 1000 | *calculado* |

### historia 1080x1920
| Elemento | x | y | ancho | alto |
|---|---|---|---|---|
| logo ayuntamiento | 40 | 70 | 221 | 110 |
| jornada | 381 | 98 | 236 | 54 |
| logo competición | 736 | 75 | 304 | 100 |
| título | 21 | 265 | 1037 | 166 |
| fecha (día y fecha) | 80 | 537 | 233 | 102 |
| hora | 438 | 563 | 143 | 51 |
| campo (en casa; fuera llega hasta x=1040, anclado a la línea de la fecha) | 707 | 512 | 293 | 153 |
| escudo izquierda | 135 | 745 | 310 | 310 |
| VS | 485 | 853 | 110 | 95 |
| escudo derecha | 635 | 745 | 310 | 310 |
| nombre izquierda | 60 | 1067 | 460 | 108 |
| nombre derecha | 560 | 1067 | 460 | 108 |
| bloque racha+hashtags | 130 | *calculada* | 646 | 140 |
| QR + texto | 810 | *calculada* | 140 | 157 |
| bloque patrocinadores | *centrado (filas de hasta 1000)* | *calculada, anclado abajo* | ≤ 1000 | *calculado* |

Los escudos pasan de 300 a 262 (publicación) y de 330 a 310 (historia) para hacer sitio; el VS y sus diagonales se
mueven con ellos.

## Posición vertical calculada

La fila de **racha + hashtags + QR** no tiene una `y` fija: se centra en vertical entre el final de los nombres y el
principio de los patrocinadores (que se anclan abajo), y su alto es el del más alto de los que salen. Entre bloques
seguidos (escudos → nombres → fila de racha/QR → patrocinadores) queda siempre un hueco de **8 px** como mínimo (lo
comprueba `CartelPatrocinadoresTamanoTest`, en los dos formatos y con y sin opcionales).

## Patrocinadores

El tamaño de cada logo sale de su **proporción** `a = ancho/alto`, medida sobre su contenido no transparente (se recorta
antes el margen transparente del PNG), para igualar el peso visual:

- ancho = K·√a y alto = K/√a;
- si el alto pasa de HMAX: alto = HMAX y ancho = HMAX·a;
- si el ancho pasa de WMAX: ancho = WMAX y alto = WMAX/a.

| Formato | K | HMAX | WMAX | Separación horizontal | Separación vertical | Borde inferior a |
| --- | --- | --- | --- | --- | --- | --- |
| Publicación | 150 | 112 | 300 | 40 | 18 | 26 del final |
| Historia | 200 | 150 | 330 | 50 | 34 | 50 del final |

Los logos van en filas que se llenan de izquierda a derecha en su `ordenCartel`. Cada fila mide como mucho 1000 px (de
x=40 a x=1040) y está centrada en el lienzo; los logos de una fila se centran en vertical entre ellos. Con los 7
actuales salen **3 + 4** en publicación y **2 + 3 + 2** en historia. El bloque se ancla abajo.
