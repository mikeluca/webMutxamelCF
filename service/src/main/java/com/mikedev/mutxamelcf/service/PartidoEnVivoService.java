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

    /**
     * Alineación con el texto libre del once y los suplentes.
     *
     * @deprecated lo usan las versiones de la app que aún no eligen jugadores; usar
     *             {@link #enviarAlineacion(Long, Long, List, List)}.
     */
    // TODO: quitar cuando la versión mínima de la app obligue a actualizar.
    @Deprecated
    void enviarAlineacion(Long usuarioId, String onceInicial, String suplentes);

    /**
     * Alineación elegida entre los jugadores del partido ({@link #obtenerConvocados}), cada lista en el orden en que
     * debe salir en el aviso, con el nombre de retransmisión de cada jugador. Los suplentes son opcionales.
     *
     * @throws IllegalArgumentException si no hay titulares, hay un jugador repetido (también entre titulares y
     *                                  suplentes), alguno no está entre los jugadores del partido o hay más titulares
     *                                  de los de la modalidad (F11 11, F8 8, F5 5)
     */
    void enviarAlineacion(Long usuarioId, Long partidoId, List<Long> titulares, List<Long> suplentes);

    /** Partidos del Primer Equipo de hoy, no cancelados. Vacía si no hay ninguno. */
    List<PartidoDisponible> obtenerPartidosDisponibles(Long usuarioId);

    EstadoPartidoEnVivoResponse obtenerEstado(Long usuarioId, Long partidoId);

    /**
     * Jugadores entre los que elegir autor del gol o alineación. Con convocatoria, los convocados efectivos (los del
     * propio equipo y los invitados autorizados, sin ausencia justificada a ese partido); sin ella, toda la plantilla
     * activa del equipo.
     */
    List<Convocado> obtenerConvocados(Long usuarioId, Long partidoId);

    EstadoPartidoEnVivoResponse enviarInicioPartido(Long usuarioId, Long partidoId);

    /**
     * Se indica jugadorId (convocado) o autorTexto ("Otro" o "p.p."), nunca los dos. El minuto
     * (0-150) es opcional: sin él lo calcula el servidor.
     */
    EstadoPartidoEnVivoResponse enviarGolFavor(
            Long usuarioId, Long partidoId, Long jugadorId, String autorTexto, Integer minuto);

    default EstadoPartidoEnVivoResponse enviarGolFavor(
            Long usuarioId, Long partidoId, Long jugadorId, String autorTexto) {
        return enviarGolFavor(usuarioId, partidoId, jugadorId, autorTexto, null);
    }

    /** El minuto (0-150) es opcional: sin él lo calcula el servidor. */
    EstadoPartidoEnVivoResponse enviarGolContra(Long usuarioId, Long partidoId, Integer minuto);

    default EstadoPartidoEnVivoResponse enviarGolContra(Long usuarioId, Long partidoId) {
        return enviarGolContra(usuarioId, partidoId, null);
    }

    /**
     * Corrige el minuto (0-150) de un gol no anulado de un partido de hoy, sea cual sea su orden
     * y sin límite de tiempo. No se reenvía ningún push.
     */
    EstadoPartidoEnVivoResponse corregirMinuto(Long usuarioId, Long partidoId, Long eventoId, Integer minuto);

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
