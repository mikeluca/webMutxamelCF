# Cartel "Próximo partido del Filial": guía del diseño

Es el equivalente del cartel del Primer Equipo (`docs/diseno-cartel-primer-equipo/`) con otro diseño. Contrato de las rutas
y permisos en `docs/api-app-imagenes-redes.md`.

Referencias en esta carpeta (no van en `resources`):

- `referencia_publicacion_1080x1350.png`, `referencia_historia_1080x1920.png` y `referencia_publicacion_ca.png`: el diseño
  a imitar.
- `medidas.md`: posición y tamaño de cada elemento en los dos formatos. Están copiadas como constantes `PUBLICACION` e
  `HISTORIA` en `CartelFilialRenderer`; si cambia una medida, se cambia en los dos sitios.
- `ejemplos/`: PNG generados por `CartelFilialServiceImplTest.generaLosEjemplosParaRevisarElDiseno` (publicación en casa
  en castellano, publicación fuera en catalán con un rival de nombre largo, historia en casa, publicación sin opcionales y
  copa), con datos inventados y un escudo de rival de ejemplo. Se regeneran al pasar los tests.

## Dónde está el código

| Qué | Clase |
| --- | --- |
| Dibujo (Java2D) y disposición calculable | `CartelFilialRenderer` |
| Equipo "Filial", textos y datos | `CartelFilialServiceImpl` |
| Lo común con el Primer Equipo (partido, campo, racha, fondo, texto...) | `CartelPartidoServiceBase` |
| Helpers de dibujo compartidos (escudos con sombra, QR, ajuste de texto...) | `CartelPrimerEquipoRenderer` |
| Fondo (tabla o por defecto) | `FondosCartelesServiceImpl`, `CartelRedes.FILIAL` |

## Recursos (`service/src/main/resources/redes`)

- `cartel-filial/fondo.jpg`: foto por defecto (1079×1350) con el degradado azul ya pintado.
- `fonts/Inter-Bold.otf` (OFL, licencia en `fonts/licencias`), y las demás que ya usan los otros carteles:
  `BarlowCondensed-ExtraBoldItalic.ttf` (nombres, VS y texto de competición) e `Inter-ExtraBold.otf` (letras de la racha).
- `logos/competiciones/*.png`, y `escudo.png` (el del Mutxamel).

## Cómo se dibuja

1. **Fondo.** Azul `#004AAB` liso en todo el lienzo. La foto se dibuja arriba a lo ancho (escalada para cubrir y recortada
   al centro a 1080×1350; en historia llega hasta y=1350). Encima, el servidor pinta su propio degradado: transparente
   hasta el 55 % de la altura de la foto (y=742) y azul opaco desde el 66 % (y=891). Así cualquier foto que suba el club
   desde el panel queda igual sin retocarla; con la foto por defecto, que ya lleva el azul, no se nota.
2. **Arriba, centrado:** el logo de la competición (`LIGA`: Segona si contiene "SEGONA" o "SEGUNDA", Primera si contiene
   "PRIMERA"; `COPA`: La Nostra Copa) o, si no encaja, el texto de la competición en Barlow. Sin jornada, sin Ajuntament y
   sin patrocinadores.
3. **Escudos y VS.** En casa, el Mutxamel a la izquierda; fuera, a la derecha. "VS" en Barlow ExtraBoldItalic y sombra
   suave en los escudos. Rival sin escudo: su nombre en grande en su hueco (hasta 3 líneas).
4. **Línea de nombres.** `MUTXAMEL C.F. “B” - {RIVAL}` (fuera, al revés), en Barlow ExtraBoldItalic, mayúsculas y
   centrada. Se reduce el tamaño hasta que quepa en 1000 px; si con el 70 % del tamaño aún no cabe, se parte por el guion
   en dos líneas: el local en la primera y "- visitante" en la segunda.
5. **Debajo, centrados, en Inter Bold y mayúsculas:** `{DÍA} dd/MM/yyyy` (mismos días en es y ca que el Primer Equipo),
   `HH:mmH` y el campo en una línea (en casa y en Els Oms "CAMP NOU LOS OLMOS"; si no, recortado con `CampoCartel` y reduciendo la
   letra si no cabe).
6. **Opcionales.**
   - *Publicación:* racha abajo a la izquierda, QR con "Descarga la app" abajo a la derecha y los hashtags en una línea
     debajo del campo. Como la racha y el QR están a la altura de la hora, el campo y los hashtags, el texto central no
     puede ocupar más de lo que queda entre ellos (con margen): un campo largo reduce su letra. Un test lo comprueba con
     el campo más largo y todas las combinaciones.
   - *Historia:* racha y hashtags en columna, centrados, con el QR a su derecha; el conjunto se centra en el lienzo.
   - Si se desmarcan, no queda hueco: lo que quede se centra.
7. **Racha.** La del Filial (sus últimos 5 partidos con resultado, del más antiguo al más reciente), con los colores de la
   app (G verde, E gris, P rojo).

Todos los textos son blancos. No hay nombres ni fotos de jugadores.

## Texto para copiar

El mismo que el del Primer Equipo con "Mutxamel C.F. “B”" como nombre del equipo:
`Próximo partido: Mutxamel C.F. “B” – {rival}. Domingo 22/11 a las 19:00, {campo}. ¡Os esperamos! #somMTX #mareaAzul #MutxamelCF`.

## Fondo editable

En el panel Administración deportiva → Fondos de carteles, el Filial funciona igual que el Primer Equipo (subir un JPG o
PNG de hasta 5 MB y al menos 1080×1350, vista previa y "Restaurar el de por defecto", que es `cartel-filial/fondo.jpg`).
