package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.EstadoPartidoEnVivoResponse;
import com.mikedev.mutxamelcf.model.EstadoPartidoEnVivoResponse.Convocado;
import com.mikedev.mutxamelcf.model.EstadoPartidoEnVivoResponse.PartidoDisponible;

/**
 * Retransmisión en directo de los partidos del Primer Equipo, reservada al rol
 * RETRANSMISION. Cada hecho (inicio, gol, descanso, segunda parte, final) es un
 * evento de un partido concreto; el marcador, la fase y los goleadores se
 * calculan con los eventos no anulados. Los eventos se difunden por push a
 * todas las cuentas y al topic de resultados. Todas las acciones devuelven el
 * estado resultante.
 *
 * Todo evento exige que el partido sea del Primer Equipo, de hoy y no esté
 * cancelado ({@link IllegalArgumentException} si no), y que respete el orden
 * (sin gol antes del inicio, nada después del final).
 */
public interface PartidoEnVivoService {

    void enviarAlineacion(Long usuarioId, String onceInicial, String suplentes);

    /** Partidos del Primer Equipo de hoy, no cancelados. Vacía si no hay ninguno. */
    List<PartidoDisponible> obtenerPartidosDisponibles(Long usuarioId);

    EstadoPartidoEnVivoResponse obtenerEstado(Long usuarioId, Long partidoId);

    /** Jugadores de la convocatoria del partido o, sin convocatoria, la plantilla del Primer Equipo. */
    List<Convocado> obtenerConvocados(Long usuarioId, Long partidoId);

    EstadoPartidoEnVivoResponse enviarInicioPartido(Long usuarioId, Long partidoId);

    /** Se indica jugadorId (convocado) o autorTexto ("Otro" o "p.p."), nunca los dos. */
    EstadoPartidoEnVivoResponse enviarGolFavor(Long usuarioId, Long partidoId, Long jugadorId, String autorTexto);

    EstadoPartidoEnVivoResponse enviarGolContra(Long usuarioId, Long partidoId);

    EstadoPartidoEnVivoResponse enviarDescanso(Long usuarioId, Long partidoId);

    EstadoPartidoEnVivoResponse enviarSegundaParte(Long usuarioId, Long partidoId);

    /**
     * Cierra el partido y guarda el resultado en PARTIDOS (y los goles por
     * jugador, solo si el partido no tenía estadísticas).
     */
    EstadoPartidoEnVivoResponse enviarFinalPartido(Long usuarioId, Long partidoId);

    /**
     * Anula el ÚLTIMO evento no anulado del partido, solo durante los 2 minutos
     * siguientes a su fecha. El inicio no se puede deshacer.
     */
    EstadoPartidoEnVivoResponse deshacer(Long usuarioId, Long partidoId, Long eventoId);
}
