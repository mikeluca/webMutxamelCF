package com.mikedev.mutxamelcf.model;

public class ConvocatoriaJugadorResponse {

    private Long jugadorId;
    private String jugador;

    public ConvocatoriaJugadorResponse() {
    }

    public ConvocatoriaJugadorResponse(
            Long jugadorId,
            String jugador) {

        this.jugadorId = jugadorId;
        this.jugador = jugador;
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }

    public String getJugador() {
        return jugador;
    }

    public void setJugador(String jugador) {
        this.jugador = jugador;
    }
}