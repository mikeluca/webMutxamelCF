package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Tarjetas de un equipo. Si el equipo no registra estadísticas de jugador
 * (no es F11), {@code jugadores} va vacío y {@code admiteEstadisticas} es false.
 */
public record TarjetasEquipoResponse(
        boolean admiteEstadisticas,
        List<TarjetasJugadorResponse> jugadores) {
}
