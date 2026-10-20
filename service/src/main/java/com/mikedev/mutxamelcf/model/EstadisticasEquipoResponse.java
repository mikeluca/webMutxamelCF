package com.mikedev.mutxamelcf.model;

import java.util.List;

/** Estadísticas de un equipo en la temporada activa (vista del staff). */
public record EstadisticasEquipoResponse(
        List<Goleador> goleadores,
        List<Asistente> asistentes,
        Entrenamientos entrenamientos,
        Partidos partidos) {

    /** partidos: en cuántos partidos ya jugados estuvo convocado. */
    public record Goleador(Long jugadorId, String nombre, Integer dorsal, int goles, int asistencias, int partidos) {
    }

    public record Asistente(Long jugadorId, String nombre, Integer dorsal, int asistencias, int goles, int partidos) {
    }

    /**
     * sesiones: entrenamientos pasados; sesionesRevisadas: los que tienen la lista
     * revisada; sesionesSinRevisar: el resto. asistenciaMedia y porJugador solo
     * cuentan los de lista revisada (null si todavía no hay ninguno).
     */
    public record Entrenamientos(
            int sesiones,
            int sesionesRevisadas,
            int sesionesSinRevisar,
            Integer asistenciaMedia,
            List<AsistenciaJugador> porJugador) {
    }

    /** porcentaje: asistidos / total * 100 redondeado, o null si total = 0. */
    public record AsistenciaJugador(Long jugadorId, String nombre, int asistidos, int total, Integer porcentaje) {
    }

    public record Partidos(int jugados, int ganados, int empatados, int perdidos, int golesFavor, int golesContra) {
    }
}
