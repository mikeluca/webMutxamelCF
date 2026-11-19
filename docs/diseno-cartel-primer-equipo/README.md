# Cartel "Próximo partido del Primer Equipo": guía del diseño

Referencias en esta carpeta (no van en `resources`):

- `referencia_publicacion_1080x1350.png` y `referencia_historia_1080x1920.png`: el diseño a imitar.
- `medidas.md`: posición y tamaño en píxeles de cada elemento en los dos formatos. Están copiadas tal cual como
  constantes `PUBLICACION` y `HISTORIA` en `CartelPrimerEquipoRenderer`; si cambia una medida, se cambia en los dos sitios.
- `ejemplos/`: cinco PNG generados por `CartelPrimerEquipoServiceImplTest.generaLosEjemplosParaRevisarElDiseno`
  (publicación e historia, en casa y fuera, en castellano y catalán) con datos inventados y un escudo de rival de
  ejemplo. Se regeneran al pasar los tests.

Contrato de las rutas, permisos y fondos del panel: `docs/api-app-imagenes-redes.md`. Patrocinadores:
`docs/api-public-patrocinadores.md`.

## Dónde está el código

| Qué | Clase |
| --- | --- |
| Dibujo (Java2D, como `ImagenesRedesRenderer`: antialias y render de calidad) | `CartelPrimerEquipoRenderer` |
| Qué partido, datos, textos y idioma | `CartelPrimerEquipoServiceImpl` |
| Recorte del campo | `CampoCartel` |
| Fondo (tabla o por defecto) y su validación | `FondosCartelesServiceImpl`, `CartelRedes` |
| Patrocinadores | `PatrocinadoresClub` |

## Recursos (`service/src/main/resources/redes`)

- `cartel-primer-equipo/fondo.jpg`: fondo por defecto (1075×1345).
- `logos/ayuntamiento-blanco.png` (destacado), `logos/competiciones/*.png` y `logos/patrocinadores-cartel/*.png`.
- `fonts/`: `LMRoman10-Regular.otf` (título, licencia GUST) e `Inter-Light`, `Inter-Medium`, `Inter-ExtraBold` e
  `Inter-BlackItalic` (licencia OFL). Las licencias están en `fonts/licencias`; **no se modifican las fuentes**.
- `escudo.png`: el escudo del Mutxamel que ya usan las demás imágenes para redes.

## Cómo se dibuja

1. **Fondo**: la foto a pantalla completa, escalada para cubrir y recortada al centro ("cover"); en historia se amplía.
   Si la foto es muy clara arriba o abajo se añade un degradado negro suave (hasta el 35 %) para que el texto blanco se lea.
2. **Fila de arriba**: logo destacado (Ayuntamiento) a la izquierda; `JORNADA n` en el centro (Inter Light, mayúsculas,
   letras algo juntas; solo si el partido tiene jornada); a la derecha el logo de la competición o su texto:
   - `COPA` → La Nostra Copa.
   - `LIGA` → según la competición del Primer Equipo (`EquipoFfcv`): contiene `PRIMERA` → Primera FFCV; `SEGONA` o
     `SEGUNDA` → Segona FFCV; si no encaja, la competición escrita.
   - `AMISTOSO` → "PARTIDO AMISTOSO" / "PARTIT AMISTÓS"; `TORNEO` → "TORNEO" / "TORNEIG".
3. **Título**: "PRÓXIMO PARTIDO" / "PRÒXIM PARTIT" en Latin Modern Roman comprimido en horizontal (factor 0,56),
   centrado y con el mayor tamaño que ocupa el ancho de la caja sin salirse.
4. **Fila de datos** (Inter Light, mayúsculas): día de la semana y `dd/MM/yyyy` en dos líneas alineadas a la derecha;
   `HH:mmH` en el centro; el campo a la izquierda en hasta 3 líneas. En casa siempre `CAMP NOU / LOS OLMOS /
   (MUTXAMEL)`; fuera, el campo del partido sin prefijos ("Campo Mpal.", "Camp de Futbol"...) ni identificadores de
   terreno (F-11, F-8, C.A., césped...): `Campo Mpal. Les Costeres F-11 Gata` → `LES COSTERES GATA`. Se reparte por
   palabras y se reduce el tamaño si no cabe.
5. **Escudos**: en casa, Mutxamel a la izquierda y el rival a la derecha; fuera, al revés. "VS" en Inter BlackItalic con
   dos diagonales finas, y una sombra suave bajo cada escudo. Rival sin escudo: su nombre en grande en su hueco y nada
   debajo. Nombres bajo los escudos en Inter Light (hasta 2 líneas, mismo tamaño en los dos).
6. **Opcionales** (racha, hashtags y QR): ver `docs/api-app-imagenes-redes.md`. Se centran con lo que quede.
7. **Patrocinadores** de abajo, en filas de 4 como máximo, centradas, cada logo encajado en su celda.

Todos los textos son blancos. No hay nombres ni fotos de jugadores.

## Cartel de la Filial

Preparado, sin diseño: basta con el valor `FILIAL` del enum `CartelRedes` (ya está) y su fondo en `IMAGENES_REDES_FONDOS`.
Falta su fondo por defecto, su renderizador y su ruta.

`ejemplos/publicacion-fuera-es-escudo-real.png` usa un escudo de verdad en el lado del rival (el del Mutxamel, porque el
repositorio no tiene escudos de rivales) para comprobar el tamaño y la sombra.
