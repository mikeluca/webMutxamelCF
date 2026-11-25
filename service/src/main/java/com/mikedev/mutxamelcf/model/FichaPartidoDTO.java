package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Ficha pública de un partido del Primer Equipo (GET /api/public/partidos/{id}/ficha): cabecera, eventos de la
 * retransmisión (lo último primero), alineación y forma del equipo. Nunca lleva el año de nacimiento de nadie ni la
 * foto de un menor.
 */
public record FichaPartidoDTO(
        Cabecera cabecera,
        List<EventoFicha> eventos,
        AlineacionFicha alineacion,
        List<FormaFicha> forma) {

    public static final String PROXIMO = "PROXIMO";
    public static final String EN_JUEGO = "EN_JUEGO";
    public static final String DESCANSO = "DESCANSO";
    public static final String FINALIZADO = "FINALIZADO";
    public static final String CANCELADO = "CANCELADO";

    /**
     * @param estado PROXIMO, EN_JUEGO, DESCANSO, FINALIZADO o CANCELADO
     * @param golesFavor goles del Mutxamel (en directo, o los del resultado guardado); null si no hay ninguno todavía
     * @param competicion texto ya redactado en el idioma pedido ("Liga ... · Jornada 5", "Copa", "Amistoso")
     * @param reloj reloj en directo; null salvo en la primera parte, el descanso y la segunda parte
     * @param escudoRivalUrl ruta relativa a la URL base del escudo del club rival (/api/public/escudos-rivales/{id}?v=N); null si no tiene
     * @param rivalEsPropio true si el rival es un equipo de nuestro club (la app pone nuestro escudo)
     * @param mutxamelLocal true si el Mutxamel juega en casa. La app nueva decide el lado con este campo y no con el
     *                      texto de equipoLocal
     * @param enCasaElsOms true si es local y se juega en Els Oms (CampoCartel.esElsOms)
     * @param goleadores nuestros goleadores, en orden del primer gol (retransmisión) o de más a menos goles
     *                   (estadísticas guardadas); vacía sin datos
     * @param campo el campo del partido tal cual; null si no hay
     * @param jornada número de jornada; null si no la tiene
     */
    public record Cabecera(
            Long partidoId, String equipoLocal, String equipoVisitante, LocalDate dia, String hora,
            Integer golesFavor, Integer golesContra, String estado, String competicion, RelojPartido reloj,
            int minutosParte, String escudoRivalUrl, boolean rivalEsPropio, boolean mutxamelLocal,
            List<Goleador> goleadores, String campo, Integer jornada) {
    }

    /**
     * @param nombre nombre en directo del goleador (o el autor libre / "Gol en propia puerta del rival" en el idioma
     *               pedido)
     * @param minutos minutos de sus goles; vacía si el dato viene de las estadísticas guardadas
     */
    public record Goleador(String nombre, int goles, List<Integer> minutos) {
    }

    /**
     * Un hecho del partido o un separador.
     *
     * @param tipo GOL_FAVOR, GOL_CONTRA, CAMBIO, TARJETA_AMARILLA, TARJETA_ROJA (también la doble amarilla), o los
     *             separadores DESCANSO y FINAL
     * @param jugador nombre en directo del goleador, del amonestado o del que sale en un cambio; null en un gol en
     *                contra y en los separadores. En una tarjeta del rival, un texto para las versiones de la app que
     *                solo conocen este campo: "Dorsal 9" o, sin dorsal, "Rival" (en el idioma pedido)
     * @param jugadorEntra el que entra en un cambio
     * @param golesFavor marcador tras el gol, o al descanso / al final en los separadores; null en el resto
     * @param texto solo en los separadores, en el idioma pedido
     * @param dobleAmarilla true en la tarjeta roja que es una expulsión por doble amarilla (también la del rival)
     * @param rival true en una tarjeta a un jugador del rival
     * @param dorsalRival dorsal del rival en una tarjeta del rival o en un gol en contra; null si no se indicó
     */
    public record EventoFicha(
            String tipo, Integer minuto, String jugador, String jugadorEntra, Integer golesFavor,
            Integer golesContra, String texto, boolean dobleAmarilla, boolean rival, Integer dorsalRival) {
    }

    /** formacion null y listas vacías si no se ha enviado alineación. */
    public record AlineacionFicha(String formacion, List<Titular> titulares, List<Suplente> suplentes) {
    }

    /**
     * @param fila 1 portero, después las líneas de la formación de atrás adelante; null sin formación
     * @param columna de izquierda a derecha dentro de la fila; null sin formación
     * @param fotoBase64 miniatura JPEG solo de mayores de edad; null en los menores (la app pone el avatar)
     */
    public record Titular(
            Integer dorsal, String nombre, Integer fila, Integer columna, boolean sustituido,
            Integer minutoSustitucion, List<Tarjeta> tarjetas, String fotoBase64) {
    }

    public record Suplente(
            Integer dorsal, String nombre, boolean entro, Integer minutoEntrada, List<Tarjeta> tarjetas,
            String fotoBase64) {
    }

    /** color AMARILLA o ROJA (la segunda amarilla sale como ROJA con dobleAmarilla). */
    public record Tarjeta(String color, Integer minuto, boolean dobleAmarilla) {
    }

    /**
     * resultado G, E o P desde el punto de vista del Mutxamel; escudoRivalUrl y rivalEsPropio como en la cabecera.
     */
    public record FormaFicha(
            Long partidoId, LocalDate dia, boolean local, String rival, int golesFavor, int golesContra,
            String resultado, String escudoRivalUrl, boolean rivalEsPropio) {
    }
}
