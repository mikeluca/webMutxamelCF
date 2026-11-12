package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Estado del partido en directo, calculado a partir de los eventos no
 * anulados.
 *
 * @param fase SIN_EMPEZAR, PRIMERA_PARTE, DESCANSO, SEGUNDA_PARTE o FINAL
 * @param minutoActual el minuto del partido calculado ahora; null antes del inicio, en el descanso
 *                     y tras el final
 * @param ultimoEventoDeshacible el último evento si se puede deshacer
 *                               todavía (no es el inicio y no han pasado 2
 *                               minutos); null en otro caso
 */
public record EstadoPartidoEnVivoResponse(
        Long partidoId,
        String rival,
        boolean local,
        String fase,
        int golesFavor,
        int golesContra,
        List<String> goleadores,
        List<EventoEnVivo> eventos,
        UltimoEventoDeshacible ultimoEventoDeshacible,
        Integer minutoActual,
        AlineacionEnVivo alineacion) {

    public static final String SIN_EMPEZAR = "SIN_EMPEZAR";
    public static final String PRIMERA_PARTE = "PRIMERA_PARTE";
    public static final String DESCANSO = "DESCANSO";
    public static final String SEGUNDA_PARTE = "SEGUNDA_PARTE";
    public static final String FINAL = "FINAL";

    /**
     * autor: nombre del jugador, el texto libre o "p.p."; null en eventos sin autor. En un CAMBIO, jugadorId/autor son
     * el que sale y jugadorEntraId/jugadorEntra el que entra. En una tarjeta, jugadorId/autor son el amonestado;
     * dobleAmarilla es true en la segunda TARJETA_AMARILLA del jugador (expulsión).
     */
    public record EventoEnVivo(
            Long id, String tipo, Long jugadorId, String autor, LocalDateTime fecha, Integer minuto,
            Long jugadorEntraId, String jugadorEntra, boolean dobleAmarilla) {
    }

    /**
     * La alineación guardada (ids de jugador): titulares y suplentes por orden de elección, quién está ahora en el
     * campo, los suplentes que aún no han entrado y los expulsados. Con eso la app ofrece solo cambios válidos.
     */
    public record AlineacionEnVivo(
            String formacion, List<Long> titulares, List<Long> suplentes, List<Long> enElCampo,
            List<Long> suplentesDisponibles, List<Long> expulsados) {
    }

    /** segundosRestantesDeshacer: lo que queda según el reloj del servidor. */
    public record UltimoEventoDeshacible(Long id, LocalDateTime expira, int segundosRestantesDeshacer) {
    }

    /** Un partido de hoy del Primer Equipo que se puede retransmitir. */
    public record PartidoDisponible(Long id, String rival, String hora, String campo, boolean local, LocalDate dia) {
    }

    public static final String ORIGEN_CONVOCATORIA = "CONVOCATORIA";
    public static final String ORIGEN_PLANTILLA = "PLANTILLA";

    /**
     * Un jugador entre los que se puede elegir al autor de un gol o la alineación.
     *
     * @param nombre              nombre y apellidos (igual que nombreCompleto; se mantiene para las versiones de la app
     *                            que solo conocen este campo)
     * @param nombreCompleto      nombre y apellidos
     * @param nombreRetransmision cómo saldrá en los avisos y la portada; si el jugador no tiene uno propio, es igual
     *                            que nombreCompleto
     * @param origen              CONVOCATORIA (los convocados efectivos del partido) o PLANTILLA (el partido no tiene
     *                            convocatoria: toda la plantilla activa)
     */
    public record Convocado(Long jugadorId, Integer dorsal, String nombre, String nombreCompleto,
            String nombreRetransmision, String origen) {
    }
}
