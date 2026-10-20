package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Jugadores con muchas faltas o mal comportamiento en un periodo (solo coordinación).
 * {@code dias} es el valor de seguimiento.dias (la longitud del periodo "30d").
 */
public record SeguimientoJugadoresResponse(
        String periodo, int dias, LocalDate desde, LocalDate hasta, List<Jugador> jugadores) {

    public static final String MOTIVO_FALTAS = "FALTAS";
    public static final String MOTIVO_COMPORTAMIENTO = "COMPORTAMIENTO";
    public static final String MOTIVO_AMBOS = "AMBOS";

    public record Jugador(
            Long jugadorId, String nombre, String equipo, int faltas, int faltasJustificadas,
            int malComportamiento, int retrasos, LocalDate ultimaIncidencia, String motivo) {
    }

    /** Detalle de un jugador: sus incidencias en el periodo. */
    public record Detalle(
            String periodo, int dias, LocalDate desde, LocalDate hasta, Jugador jugador, List<Incidencia> incidencias) {
    }

    public record Incidencia(LocalDate fecha, String estado) {
    }
}
