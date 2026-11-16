package com.mikedev.mutxamelcf.model;

/** Los goles de un jugador en un partido, según las estadísticas guardadas (PARTIDO_ESTADISTICAS_JUGADOR). */
public record GolesJugador(JugadorBasico jugador, int goles) {
}
