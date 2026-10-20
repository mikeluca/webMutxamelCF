package com.mikedev.mutxamelcf.model;

/**
 * Totales de un jugador del equipo en los partidos no cancelados de un rango:
 * goles, asistencias y tarjetas, y en cuántos partidos ya jugados estuvo
 * convocado.
 */
public record EstadisticasJugadorEquipo(
        Long jugadorId,
        String nombre,
        Integer dorsal,
        int goles,
        int asistencias,
        int amarillas,
        int rojas,
        int partidos) {
}
