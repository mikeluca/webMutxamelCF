package com.mikedev.mutxamelcf.model;

/** Agregados de un jugador en los partidos de un rango: convocatorias y totales de su ficha. */
public record EstadisticasPartidosJugador(int convocado, int goles, int asistencias, int amarillas, int rojas) {
}
