# Avisos automáticos: textos, idiomas y cuándo se envían

Todos los avisos automáticos (bandeja, push y emails a usuarios) salen en el idioma de la cuenta que los recibe:
castellano (`es`), valenciano (`ca`) o inglés (`en`). **Para cambiar un texto solo hay que tocar los `.properties`**;
no hace falta tocar Java.

## Dónde están los textos

| Qué | Fichero |
| --- | --- |
| Bandeja y push | `service/src/main/resources/i18n/avisos_es.properties`, `avisos_ca.properties`, `avisos_en.properties` |
| Emails a usuarios de la app | `mvc/src/main/resources/emails/emails_es.properties`, `emails_ca.properties`, `emails_en.properties` |

Los emails internos del club (pedido de la tienda, contacto, recordatorio de importar de la FFCV, reporte de contenido)
van en castellano y siguen en el código de `ComunicacionesService`.

### Reglas de los ficheros

- UTF-8. Un texto por clave; los tres ficheros tienen **las mismas claves y los mismos marcadores en cada clave**
  (lo comprueba `TextosAvisosTest`: si añades una clave en un idioma y no en los otros, o cambias un marcador, el test
  falla).
- Los marcadores son con nombre, `{jugador}`, `{rival}`..., y **no son `MessageFormat`**: se sustituyen tal cual, así
  que los apóstrofos del valenciano van sin duplicar.
- `\n` es un salto de línea. Los títulos no llevan saltos de línea.
- Lo que escribe la gente (motivos, nombres de equipos, rivales, equipajes, nombres de jugadores, título de una
  noticia) **no se traduce**: entra como valor del marcador.
- Si falta una clave en `ca` o `en`, se usa la de `es` y se deja un `WARN` en el log (`TextosAvisos`).
- Las claves `*.push` son el texto del push cuando es distinto del de la bandeja. Sin clave `.push`, el push lleva el
  mismo texto que la bandeja.
- Si el partido (o la sesión) no tiene hora, se usa la variante sin hora: `recordatorioConvocatoria.textoSinHora`,
  `entrenamientoCancelado.textoSinHora`, `convocatoria.nueva.{jugador,familia}SinHora`,
  `invitacion.convocado.{jugador,familia}SinHora` y `cambio.partidoSinHora`.

### Formatos de los marcadores

| Marcador | Formato |
| --- | --- |
| `{fecha}` (y `{fechaAntigua}`, `{fechaNueva}`) | día de la semana + d/M: `sábado 25/10`, `dissabte 25/10`, `Saturday 25/10` |
| `{fechaCompleta}` | d/M/aaaa: `10/6/2027` |
| `{hora}`, `{horaCitacion}` | HH:mm, sin "h" detrás: `09:30` |
| `{nombres}` | lista con la palabra de `lista.y`: `Ana, Luis y Marta` / `Ana, Luis i Marta` / `Ana, Luis and Marta` |

### Cómo se montan los avisos de convocatoria

Nueva, cambio y "convocado con otro equipo":

1. El texto base (`convocatoria.nueva.*`, `convocatoria.cambio.*` o `invitacion.convocado.*`).
2. Si hay equipaje, un párrafo con `equipaje.principal` y, si hay mochila, `equipaje.mochila` en la misma línea.
3. Al final, en párrafo aparte, `ducha`.

En un cambio, `{cambios}` son las piezas `cambio.*` que apliquen separadas por `; `. Si cambia el equipaje, el párrafo del
equipaje nuevo (o `equipaje.ninguno`). El título es `convocatoria.cambioEquipaje.titulo` si solo cambia el equipaje y
`convocatoria.cambio.titulo` en cualquier otro caso.

### El motivo

El motivo que escribe una familia o coordinación va **siempre como párrafo aparte** (clave `motivo`) y **solo en la
bandeja, nunca en el push**. La excepción es el motivo de `entrenamientoCancelado`, que lo escribe el entrenador y va en
la bandeja y en el push (sin espacios ni puntos finales sobrantes; si queda vacío no se añade el párrafo).

### Versión jugador / familia

`.jugador` para la cuenta del propio jugador y `.familia` para las familias. Una cuenta que es a la vez la del
jugador y de familia recibe la del jugador.

## Cuándo se envía cada aviso

"Cuenta" = bandeja + push. Cuando se avisa a varios destinatarios se crea **una comunicación por idioma**.

### Piezas comunes (no son avisos por sí solas)

`motivo`, `ducha`, `equipaje.principal`, `equipaje.mochila`, `equipaje.ninguno`, `lista.y`, `directo.sinGoleadores`,
`directo.golSinAutor`, `directo.golPropiaPuerta`.

### 1. Convocatorias

| Clave | Cuándo | A quién | Push distinto |
| --- | --- | --- | --- |
| `convocatoria.nueva.*` | El entrenador crea la convocatoria, o añade a alguien al editarla | Cada jugador convocado y su familia (no a quien ha justificado su ausencia) | No |
| `convocatoria.cambio.*`, `cambio.*` | Cambia la hora de citación, el lugar, el partido o el equipaje | Convocados que siguen e invitados autorizados que siguen | No |
| `convocatoria.cambioEquipaje.titulo` | Solo cambia el equipaje | Igual que el anterior | No |
| `convocatoria.quitado.*` | Quitan a un jugador de la convocatoria al editarla | El jugador y su familia | No |
| `convocatoria.anulada.*` | Se borra la convocatoria | Jugadores convocados y familias | No |
| `convocatoria.quitadosAusencia.*` | Al cambiar de partido, quitados por tener ausencia justificada en el nuevo | Cuerpo técnico del equipo (menos quien edita) | No |

### 2. Ausencias a partidos (familia o jugador justifica)

| Clave | Cuándo | A quién | Push distinto |
| --- | --- | --- | --- |
| `ausencia.justificada.*` | Justifican una ausencia de un jugador no convocado | Cuerpo técnico (o coordinación si no hay) | Sí: sin el motivo |
| `ausencia.convocado.*` | Justifican una ausencia de un convocado y hay otros disponibles | Ídem | Sí: sin el motivo |
| `ausencia.quitado.*` | Justifican una ausencia de un convocado y no quedan disponibles: se le quita | Ídem | Sí: sin el motivo |
| `ausencia.retirada.*` | Retiran la justificación | Ídem | No (no lleva motivo) |

### 3. Jugadores invitados de otros equipos

| Clave | Cuándo | A quién | Push distinto |
| --- | --- | --- | --- |
| `invitacion.pendiente.*` | Un entrenador invita a un jugador de otro equipo | Coordinación | Sí (`.push`) |
| `invitacion.rechazada.*` | Coordinación rechaza la invitación | Quien invitó | Sí: sin el motivo |
| `invitacion.liberar.*` | El jugador está convocado con su equipo ese día: se pide al entrenador que lo libere | Cuerpo técnico del equipo de origen | Sí (`.push`) |
| `invitacion.plazo.*` | Pasan 12 horas sin liberar | Coordinación | Sí (`.push`) |
| `invitacion.convocado.*` | El invitado queda autorizado | El jugador y su familia | No |
| `invitacion.autorizadaOrigen.*` | El invitado queda autorizado y su equipo no juega ese día | Cuerpo técnico del equipo de origen | No |
| `invitacion.informativa.*` | Queda autorizado, su equipo juega ese día y no lo tenía convocado | Cuerpo técnico del equipo de origen | No |
| `invitacion.liberadaCoordinacion.*` | Coordinación libera al jugador | Cuerpo técnico del equipo de origen | No |
| `invitacion.liberado.*` | Lo quitan de la convocatoria de su equipo porque juega con el otro | El jugador y su familia | No |
| `invitacion.anulada.{quitado,borrada,cancelado,aplazado}.titulo` + `invitacion.anulada.*` | Se anula lo que ya estaba autorizado (lo quitan, se borra la convocatoria, se cancela o aplaza el partido) | El jugador y su familia; el cuerpo técnico del equipo de origen recibe el texto `.familia` | No |
| `invitacion.aplazadas.*` | Cambia la fecha del partido de destino y se anulan las invitaciones | Quien invitó (uno por persona) | Sí (`.push`) |
| `invitacion.enEspera.*` | El equipo de origen mueve un partido a ese día y el invitado vuelve a esperar a su entrenador | El jugador y su familia | No |
| `invitacion.desestimada.*` | La familia desestima a un invitado autorizado | Quien lo convocó | Sí: sin el motivo |

### 4. Entrenamientos

| Clave | Cuándo | A quién | Push distinto |
| --- | --- | --- | --- |
| `falta.*` | Se pasa lista y el jugador figura con falta | El jugador (`.jugador`) y su familia (`.familia`) | No |
| `malComportamiento.*` | Se marca mal comportamiento | Coordinadores | No |
| `entrenamientoCancelado.*` | El entrenador cancela una sesión | Jugadores y familias del equipo | No (el motivo va en los dos) |
| `faltaJustificada.*` | Una familia justifica una falta a un entrenamiento | Entrenadores del equipo | Sí: sin el motivo |
| `justificacionRetirada.*` | Retiran la justificación | Entrenadores del equipo (menos quien la retira) | No (no lleva motivo) |
| `pasarLista.hoy` / `pasarLista.otroDia` | 2 horas después del entrenamiento sin lista revisada | Entrenadores del equipo | No |

### 5. Partidos: recordatorios y tarjetas

| Clave | Cuándo | A quién | Push distinto |
| --- | --- | --- | --- |
| `resultadoPendiente.*` | Job automático: el partido terminó y falta el resultado | Entrenadores del equipo | No |
| `recordatorioConvocatoria.*` | Coordinación pulsa "recordar convocatoria" | Entrenadores del equipo | No |
| `recordatorioResultado.*` | Coordinación pulsa "recordar resultado" | Entrenadores del equipo | No |
| `tarjetas.*` | Al guardar las estadísticas se cruza un umbral de amarillas o hay roja | Entrenadores (o coordinación) | No |

### 6. Partido en directo (todas las cuentas con la preferencia de resultados + topics)

`directo.alineacion.*`, `directo.inicio.*`, `directo.gol.*` (con o sin minuto), `directo.golContra.*`,
`directo.golAnulado.*`, `directo.descanso.*`, `directo.segundaParte.*`, `directo.final.*`. El marcador
(`Mutxamel CF 2 - 1 Rival`) no cambia con el idioma.

### 7. Temporada, continuidad y cuentas

| Clave | Cuándo | A quién | Push distinto |
| --- | --- | --- | --- |
| `continuidad.titulo`, `continuidad.uno` / `.varios` | Se pregunta la continuidad | Cada cuenta con jugadores preguntados (una por cuenta, con todos los nombres) | No |
| `continuidad.recordatorio.titulo`, `continuidad.recordatorio.uno` / `.varios` | Recordatorio semanal | Cuentas que no han contestado | No |
| `continuidad.noSigue.*` | La familia responde que no seguirá | Coordinadores | No |
| `continuidad.duda.*` | La familia responde que duda | Coordinadores | No |
| `temporadaNueva.*` | Se cierra la temporada y empieza la nueva | Todas las cuentas activas con la preferencia | No |
| `cuentaSinJugadores.*` | Job diario: cuenta sin jugadores activos, se avisa de su desactivación | La cuenta (push, bandeja y email) | No |

### 8. Noticias y moderación

| Clave | Cuándo | A quién | Push distinto |
| --- | --- | --- | --- |
| `noticia.titulo` | Se publica una noticia nueva (el cuerpo es el título de la noticia, sin traducir) | Todas las cuentas con la preferencia + topics de noticias | No |
| `reporte.*` | Un usuario reporta un mensaje | Administradores de la app | No |

## Topics de FCM (gente sin cuenta)

| Topic | Contenido |
| --- | --- |
| `resultados_es`, `resultados_ca`, `resultados_en` | Avisos del directo, cada uno en su idioma |
| `noticias_es`, `noticias_ca`, `noticias_en` | `noticia.titulo` + título de la noticia |
| `resultados`, `noticias` (antiguos) | Lo mismo, en castellano, para las versiones de la app que aún no se suscriben por idioma. Marcados con `TODO` en el código (`PartidoEnVivoServiceImpl`, `NoticiaAdminController`) para quitarlos cuando la versión mínima obligue a actualizar |

## Emails a usuarios

Un solo idioma (el de la cuenta), sin separador, salvo la invitación para activar la cuenta (la persona todavía no
tiene la app): castellano, valenciano e inglés con el separador de siempre.

| Plantilla (`emails_*.properties`) | Cuándo | Idioma |
| --- | --- | --- |
| `invitacion.*` | Se crea o se reenvía la invitación de la cuenta | Los tres, con separador |
| `cuentaActivada.*`, `rol.*` | La cuenta se activa. Las secciones `rol.*` se añaden según los roles | La cuenta (el que manda la app al activar) |
| `codigoRecuperacion.*` | "¿Has olvidado tu contraseña?" | El que manda la app en la petición; si no, el de la cuenta |
| `contrasenaCambiada.*` | Se cambia la contraseña | La cuenta |
| `cuentaSinJugadores.*` | Job diario (ver arriba) | La cuenta |

## Cómo añadir o cambiar un aviso

1. Cambiar el texto: editar la misma clave en los tres ficheros de `i18n/` (o `emails/`), conservando los marcadores.
2. Aviso nuevo: añadir la clave en los tres ficheros y usarla desde el servicio con `TextosAvisos.texto(idioma, clave, ...)`.
   Para varios destinatarios, `IdiomasUsuarios.crearPrivadaPorIdioma(...)` crea una comunicación por idioma.
3. Ejecutar `mvn test`: `TextosAvisosTest` comprueba que las tres versiones tienen las mismas claves y marcadores.
