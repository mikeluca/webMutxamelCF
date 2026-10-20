package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

/** Incidencias de un jugador en entrenamientos de un periodo. */
public record SeguimientoJugador(
        Long jugadorId,
        String nombre,
        String equipo,
        int faltas,
        int faltasJustificadas,
        int malComportamiento,
        int retrasos,
        LocalDate ultimaIncidencia) {
}
