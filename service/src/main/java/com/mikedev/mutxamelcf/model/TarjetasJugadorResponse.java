package com.mikedev.mutxamelcf.model;

/**
 * Tarjetas acumuladas de un jugador en la temporada activa.
 *
 * @param estado OK, A_UNA (a una amarilla de la posible sanción) o SANCION_POSIBLE
 */
public record TarjetasJugadorResponse(
        Long jugadorId,
        String nombre,
        Integer dorsal,
        int amarillas,
        int rojas,
        String estado) {

    public static final String OK = "OK";
    public static final String A_UNA = "A_UNA";
    public static final String SANCION_POSIBLE = "SANCION_POSIBLE";
}
