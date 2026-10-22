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
        Integer minutoActual) {

    public static final String SIN_EMPEZAR = "SIN_EMPEZAR";
    public static final String PRIMERA_PARTE = "PRIMERA_PARTE";
    public static final String DESCANSO = "DESCANSO";
    public static final String SEGUNDA_PARTE = "SEGUNDA_PARTE";
    public static final String FINAL = "FINAL";

    /** autor: nombre del jugador, el texto libre o "p.p."; null en eventos sin autor. */
    public record EventoEnVivo(
            Long id, String tipo, Long jugadorId, String autor, LocalDateTime fecha, Integer minuto) {
    }

    /** segundosRestantesDeshacer: lo que queda según el reloj del servidor. */
    public record UltimoEventoDeshacible(Long id, LocalDateTime expira, int segundosRestantesDeshacer) {
    }

    /** Un partido de hoy del Primer Equipo que se puede retransmitir. */
    public record PartidoDisponible(Long id, String rival, String hora, String campo, boolean local, LocalDate dia) {
    }

    /** Un jugador entre los que se puede elegir al autor de un gol. */
    public record Convocado(Long jugadorId, Integer dorsal, String nombre) {
    }
}
