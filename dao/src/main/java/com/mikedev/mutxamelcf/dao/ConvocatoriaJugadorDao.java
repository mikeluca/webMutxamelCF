package com.mikedev.mutxamelcf.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;

public interface ConvocatoriaJugadorDao {

    ConvocatoriaJugador guardar(
            ConvocatoriaJugador convocatoriaJugador);

    List<ConvocatoriaJugador> obtenerPorConvocatoria(
            Long convocatoriaId);

    ConvocatoriaJugador obtenerPorConvocatoriaYJugador(
            Long convocatoriaId,
            Long jugadorId);

    void eliminarPorConvocatoria(
            Long convocatoriaId);

    boolean existePorJugador(
            Long jugadorId);

    /**
     * Guarda la respuesta del jugador a la convocatoria (null en respuesta = sin
     * responder). Devuelve las filas modificadas (0 si el jugador no está en ella).
     */
    int guardarRespuesta(
            Long convocatoriaId,
            Long jugadorId,
            String respuesta,
            String motivo,
            LocalDateTime fechaRespuesta,
            Long usuarioAppId);

    /**
     * Convocatorias en las que está el jugador cuyo partido cae entre las dos
     * fechas (ambas incluidas), por fecha del partido y luego por ID.
     */
    List<ConvocatoriaJugador> obtenerPorJugadorYRango(
            Long jugadorId,
            LocalDate desde,
            LocalDate hasta);

    /**
     * Pone a NULL la respuesta (RESPUESTA, MOTIVO, FECHA_RESPUESTA y
     * USUARIO_APP_RESPUESTA_ID) de todos los jugadores de la convocatoria.
     * Devuelve las filas modificadas.
     */
    int limpiarRespuestasPorConvocatoria(Long convocatoriaId);
}
