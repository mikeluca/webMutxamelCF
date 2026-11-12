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
     * debe salir en el aviso, con el nombre de retransmisión de cada jugador. Los suplentes son opcionales. Equivale a
     * {@link #enviarAlineacion(Long, Long, List, List, String, boolean)} sin formación y avisando.
     *
     * @throws IllegalArgumentException si no hay titulares, hay un jugador repetido (también entre titulares y
     *                                  suplentes), alguno no está entre los jugadores del partido o hay más titulares
     *                                  de los de la modalidad (F11 11, F8 8, F5 5)
     */
    default EstadoPartidoEnVivoResponse enviarAlineacion(
            Long usuarioId, Long partidoId, List<Long> titulares, List<Long> suplentes) {
        return enviarAlineacion(usuarioId, partidoId, titulares, suplentes, null, true);
    }

    /**
     * Guarda la alineación (titulares y suplentes por orden de elección y la formación) y sustituye la anterior si
     * ya había una. Con formación (de la lista cerrada de la modalidad del equipo) los titulares deben ser 1 + la
     * suma de la formación. El push solo sale si {@code avisar}. No se puede reenviar si ya hay algún cambio
     * registrado. Devuelve el estado del partido (con la alineación).
     *
     * @throws IllegalArgumentException además de lo de {@link #enviarAlineacion(Long, Long, List, List)}, si la
     *                                  formación no está permitida o no cuadra con el número de titulares
     */
    EstadoPartidoEnVivoResponse enviarAlineacion(
            Long usuarioId, Long partidoId, List<Long> titulares, List<Long> suplentes, String formacion,
            boolean avisar);

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
     * Cambio en primera o segunda parte, o en el descanso (con el minuto del descanso; sin minuto, la duración de la
     * parte). Con alineación guardada sale alguien que está en el campo (titular no sustituido, o que entró y no ha
     * salido, y sin roja) y entra un suplente que no ha entrado; sin alineación, dos jugadores distintos de la lista
     * elegible. Sin push.
     */
    EstadoPartidoEnVivoResponse enviarCambio(
            Long usuarioId, Long partidoId, Long jugadorSaleId, Long jugadorEntraId, Integer minuto);

    /**
     * Tarjeta ({@code color} AMARILLA o ROJA) a un jugador de la lista elegible. La segunda amarilla del mismo
     * jugador es una expulsión por doble amarilla (en la ficha sale como roja). Sin push. El minuto es opcional.
     */
    EstadoPartidoEnVivoResponse enviarTarjeta(
            Long usuarioId, Long partidoId, Long jugadorId, String color, Integer minuto);

    /**
     * Corrige el minuto (0-150) de un gol, cambio o tarjeta no anulado de un partido de hoy, sea cual sea su orden
     * y sin límite de tiempo. No se reenvía ningún push.
     */
    EstadoPartidoEnVivoResponse corregirMinuto(Long usuarioId, Long partidoId, Long eventoId, Integer minuto);

    EstadoPartidoEnVivoResponse enviarDescanso(Long usuarioId, Long partidoId);

    EstadoPartidoEnVivoResponse enviarSegundaParte(Long usuarioId, Long partidoId);

    /**
     * Cierra el partido y guarda el resultado en PARTIDOS (y los goles y tarjetas por
     * jugador, solo si el partido no tenía estadísticas; con los mismos avisos de tarjetas
     * que al guardar las estadísticas).
     */
    EstadoPartidoEnVivoResponse enviarFinalPartido(Long usuarioId, Long partidoId);

    /**
     * Anula el ÚLTIMO evento no anulado del partido, solo durante los 2 minutos
     * siguientes a su fecha. El inicio no se puede deshacer.
     */
    EstadoPartidoEnVivoResponse deshacer(Long usuarioId, Long partidoId, Long eventoId);
}
