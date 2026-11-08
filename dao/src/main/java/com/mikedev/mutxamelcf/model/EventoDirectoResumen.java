package com.mikedev.mutxamelcf.model;

/**
 * Un evento no anulado del directo con lo justo para pintar la portada: el tipo y, si es un gol, su autor (el jugador,
 * con su nombre de retransmisión, o el texto libre / "p.p.").
 *
 * @param jugador el autor si es un jugador de la base de datos; null si el evento no lo tiene
 */
public record EventoDirectoResumen(String tipo, String autorTexto, JugadorBasico jugador) {
}
