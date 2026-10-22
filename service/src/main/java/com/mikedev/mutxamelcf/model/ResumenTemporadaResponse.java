package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

/**
 * Resumen de la temporada activa de un jugador (ficha de la app). Sin
 * temporada activa, {@code temporada} es null y todos los contadores son 0.
 */
public record ResumenTemporadaResponse(
        TemporadaResumen temporada,
        PartidosResumen partidos,
        EntrenamientosResumen entrenamientos,
        boolean admiteEstadisticas) {

    public record TemporadaResumen(String nombre, LocalDate inicio, LocalDate fin) {
    }

    /**
     * convocado: convocatorias en partidos ya jugados y no cancelados.
     * ausenciasJustificadas: partidos de la temporada con una ausencia justificada.
     */
    public record PartidosResumen(int convocado, int goles, int asistencias, int amarillas, int rojas,
            int ausenciasJustificadas) {
    }

    /** porcentaje: asistidos / total * 100 redondeado, o null si total = 0. */
    public record EntrenamientosResumen(int total, int asistidos, int faltas, int justificadas, Integer porcentaje) {
    }
}
