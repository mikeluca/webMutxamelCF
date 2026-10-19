package com.mikedev.mutxamelcf.dao;

import java.time.LocalDate;

import com.mikedev.mutxamelcf.model.AsistenciaEntrenamientosJugador;
import com.mikedev.mutxamelcf.model.EstadisticasPartidosJugador;
import com.mikedev.mutxamelcf.model.RespuestasConvocatoriaJugador;

/**
 * Agregados (SUM/COUNT) para el resumen de temporada de la ficha de un jugador.
 * Todas las fechas son inclusivas.
 */
public interface ResumenTemporadaDao {

    /**
     * Convocatorias del jugador en partidos YA jugados (de un día anterior a
     * {@code hoy}, o de hoy con resultado) y no cancelados, y sus goles,
     * asistencias y tarjetas en los partidos no cancelados del rango.
     */
    EstadisticasPartidosJugador estadisticasPartidos(Long jugadorId, LocalDate desde, LocalDate hasta, LocalDate hoy);

    /** Asistencia a entrenamientos con fecha en el rango. */
    AsistenciaEntrenamientosJugador asistenciaEntrenamientos(Long jugadorId, LocalDate desde, LocalDate hasta);

    /** Respuestas a las convocatorias de partidos no cancelados del rango. */
    RespuestasConvocatoriaJugador respuestasConvocatoria(Long jugadorId, LocalDate desde, LocalDate hasta);
}
