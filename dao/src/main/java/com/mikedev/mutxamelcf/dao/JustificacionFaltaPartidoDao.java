package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.JustificacionFaltaPartido;

public interface JustificacionFaltaPartidoDao {

    /** Inserta la justificación (FECHA_CREACION la pone quien llama). */
    JustificacionFaltaPartido crear(JustificacionFaltaPartido justificacion);

    /** Cambia motivo, autor y fecha de una justificación existente. */
    void actualizar(JustificacionFaltaPartido justificacion);

    JustificacionFaltaPartido obtenerPorPartidoYJugador(Long partidoId, Long jugadorId);

    List<JustificacionFaltaPartido> obtenerPorPartido(Long partidoId);

    /** Las justificaciones del jugador en partidos con fecha entre las dos (ambas incluidas), en una sola consulta. */
    List<JustificacionFaltaPartido> obtenerPorJugadorYRango(Long jugadorId, java.time.LocalDate desde,
            java.time.LocalDate hasta);

    /** Borra la justificación; devuelve las filas borradas. */
    int eliminar(Long partidoId, Long jugadorId);
}
