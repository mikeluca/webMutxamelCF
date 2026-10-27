package com.mikedev.mutxamelcf.model;

/** Una temporada en el historial de un jugador (ficha del panel). */
public record HistorialJugadorDTO(String temporada, String equipo, String categoria, Integer dorsal, boolean baja) {
}
