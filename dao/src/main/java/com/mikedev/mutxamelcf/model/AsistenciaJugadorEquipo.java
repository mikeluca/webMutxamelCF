package com.mikedev.mutxamelcf.model;

/** Asistencia de un jugador del equipo a los entrenamientos de un rango: asistidos (PRESENTE, RETRASO o MAL_COMPORTAMIENTO) de total. */
public record AsistenciaJugadorEquipo(Long jugadorId, String nombre, Integer dorsal, int total, int asistidos) {
}
