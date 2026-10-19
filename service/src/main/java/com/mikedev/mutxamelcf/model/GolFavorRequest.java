package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.Size;

/**
 * Autor de un gol a favor del Mutxamel CF: un jugador de la lista de
 * convocados ({@code jugadorId}) o, solo para "Otro" y "p.p." (gol en propia
 * puerta del rival), un texto de hasta 60 caracteres ({@code autorTexto}).
 * Se indica exactamente una de las dos cosas.
 */
public class GolFavorRequest {

    private Long jugadorId;

    @Size(max = 60, message = "El nombre del autor del gol es demasiado largo (máximo 60 caracteres)")
    private String autorTexto;

    public GolFavorRequest() {
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }

    public String getAutorTexto() {
        return autorTexto;
    }

    public void setAutorTexto(String autorTexto) {
        this.autorTexto = autorTexto;
    }
}
