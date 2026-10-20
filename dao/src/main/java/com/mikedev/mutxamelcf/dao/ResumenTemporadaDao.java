package com.mikedev.mutxamelcf.dao;

import java.time.LocalDate;
import java.util.List;

import com.mikedev.mutxamelcf.model.AsistenciaEntrenamientosJugador;
import com.mikedev.mutxamelcf.model.AsistenciaJugadorEquipo;
import com.mikedev.mutxamelcf.model.EntrenamientoDelDiaDeJugador;
import com.mikedev.mutxamelcf.model.EstadisticasJugadorEquipo;
import com.mikedev.mutxamelcf.model.PartidosEquipo;
import com.mikedev.mutxamelcf.model.EntrenamientoDelDia;
import com.mikedev.mutxamelcf.model.EstadisticasPartidosJugador;

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

    /**
     * Partidos no cancelados del rango y con fecha hasta {@code hoy} (incluida),
     * como el recuento de convocados, en los que el jugador tiene una ausencia justificada.
     */
    int ausenciasJustificadas(Long jugadorId, LocalDate desde, LocalDate hasta, LocalDate hoy);

    /**
     * Asistencia del jugador a los entrenamientos de un día concreto, cada uno
     * con la hora de su sesión.
     */
    List<EntrenamientoDelDia> entrenamientosDelDia(Long jugadorId, LocalDate fecha);

    /**
     * Por cada jugador del equipo (también sin datos): goles, asistencias,
     * amarillas y rojas en los partidos no cancelados del rango y en cuántos
     * partidos ya jugados (de un día anterior a {@code hoy}, o de hoy con
     * resultado) estuvo convocado.
     */
    List<EstadisticasJugadorEquipo> estadisticasJugadoresDelEquipo(
            Long equipoId, LocalDate desde, LocalDate hasta, LocalDate hoy);

    /**
     * Por cada jugador del equipo (también sin datos): entrenamientos del equipo
     * con fecha en el rango y a cuántos asistió.
     */
    List<AsistenciaJugadorEquipo> asistenciaEntrenamientosDelEquipo(Long equipoId, LocalDate desde, LocalDate hasta);

    /** Cuántos entrenamientos del equipo hay en el rango; con {@code soloRevisados}, solo los de lista revisada. */
    int contarEntrenamientosDelEquipo(Long equipoId, LocalDate desde, LocalDate hasta, boolean soloRevisados);

    /** La asistencia de los jugadores del equipo a los entrenamientos de un día, con la hora de cada sesión. */
    List<EntrenamientoDelDiaDeJugador> entrenamientosDelDiaDelEquipo(Long equipoId, LocalDate fecha);

    /** Partidos no cancelados del rango con resultado (GOLES_FAVOR y GOLES_CONTRA informados). */
    PartidosEquipo partidosDelEquipo(Long equipoId, LocalDate desde, LocalDate hasta);
}
