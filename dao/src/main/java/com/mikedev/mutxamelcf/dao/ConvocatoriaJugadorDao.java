package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;

public interface ConvocatoriaJugadorDao {

    /** Inserta la fila; si es un invitado (equipoOrigenId no nulo) guarda también los datos de la invitación. */
    ConvocatoriaJugador guardar(
            ConvocatoriaJugador convocatoriaJugador);

    /**
     * Los convocados efectivos: los jugadores del propio equipo y los invitados AUTORIZADOS. Es lo que ven la
     * familia, la retransmisión y las estadísticas; los invitados pendientes o rechazados no están aquí (para
     * gestionarlos, {@link #obtenerTodasPorConvocatoria(Long)}).
     */
    List<ConvocatoriaJugador> obtenerPorConvocatoria(
            Long convocatoriaId);

    /** Todas las filas de la convocatoria: propios e invitados en cualquier estado. */
    List<ConvocatoriaJugador> obtenerTodasPorConvocatoria(
            Long convocatoriaId);

    ConvocatoriaJugador obtenerPorId(
            Long id);

    /** La fila de ese jugador en la convocatoria, sea del propio equipo o invitado en cualquier estado. */
    ConvocatoriaJugador obtenerPorConvocatoriaYJugador(
            Long convocatoriaId,
            Long jugadorId);

    /** Guarda los campos de la invitación (estado, fechas, coordinador, motivo...) de una fila existente. */
    void actualizarInvitacion(
            ConvocatoriaJugador convocatoriaJugador);

    void eliminarPorConvocatoria(
            Long convocatoriaId);

    /** Borra solo la fila de ese jugador; devuelve las filas borradas. */
    int eliminarPorConvocatoriaYJugador(
            Long convocatoriaId,
            Long jugadorId);

    boolean existePorJugador(
            Long jugadorId);
}
