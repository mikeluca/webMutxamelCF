package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.util.List;

/** Jugadores con muchas faltas o mal comportamiento en un periodo (solo coordinación). */
public record SeguimientoJugadoresResponse(
        String periodo, LocalDate desde, LocalDate hasta, List<Jugador> jugadores) {

    public static final String MOTIVO_FALTAS = "FALTAS";
    public static final String MOTIVO_COMPORTAMIENTO = "COMPORTAMIENTO";
    public static final String MOTIVO_AMBOS = "AMBOS";

    public record Jugador(
            Long jugadorId, String nombre, String equipo, int faltas, int faltasJustificadas,
            int malComportamiento, int retrasos, LocalDate ultimaIncidencia, String motivo) {
    }

    /** Detalle de un jugador: sus incidencias en el periodo. */
    public record Detalle(
            String periodo, LocalDate desde, LocalDate hasta, Jugador jugador, List<Incidencia> incidencias) {
    }

    public record Incidencia(LocalDate fecha, String estado) {
    }
}
