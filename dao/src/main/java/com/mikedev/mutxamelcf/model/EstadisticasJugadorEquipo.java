package com.mikedev.mutxamelcf.model;

/**
 * Totales de un jugador del equipo en los partidos no cancelados de un rango:
 * goles, asistencias y tarjetas, y en cuántos partidos ya jugados estuvo
 * convocado.
 *
 * @param invitado es de otro equipo y ha jugado aquí como invitado autorizado: solo cuenta lo que hizo con este
 *                 equipo (docs/convocatoria-invitados.md, §3)
 */
public record EstadisticasJugadorEquipo(
        Long jugadorId,
        String nombre,
        Integer dorsal,
        int goles,
        int asistencias,
        int amarillas,
        int rojas,
        int partidos,
        boolean invitado) {

    public EstadisticasJugadorEquipo(
            Long jugadorId, String nombre, Integer dorsal, int goles, int asistencias, int amarillas, int rojas,
            int partidos) {
        this(jugadorId, nombre, dorsal, goles, asistencias, amarillas, rojas, partidos, false);
    }
}
