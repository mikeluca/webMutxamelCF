package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

/**
 * Un evento no anulado del directo con lo justo para pintar la portada: el tipo, si es un gol su autor (el jugador,
 * con su nombre de retransmisión, o el texto libre / "p.p.") y la fecha (hora de Madrid) para el reloj en directo.
 *
 * @param jugador el autor si es un jugador de la base de datos; null si el evento no lo tiene
 */
public record EventoDirectoResumen(String tipo, String autorTexto, JugadorBasico jugador, LocalDateTime fecha) {

    public EventoDirectoResumen(String tipo, String autorTexto, JugadorBasico jugador) {
        this(tipo, autorTexto, jugador, null);
    }
}
