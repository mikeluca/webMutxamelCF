package com.mikedev.mutxamelcf.model;

/** Partidos con resultado de un equipo: jugados, ganados, empatados, perdidos y goles. */
public record PartidosEquipo(int jugados, int ganados, int empatados, int perdidos, int golesFavor, int golesContra) {
}
