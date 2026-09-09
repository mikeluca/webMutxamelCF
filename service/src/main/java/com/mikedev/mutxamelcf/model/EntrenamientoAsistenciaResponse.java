package com.mikedev.mutxamelcf.model;

public class EntrenamientoAsistenciaResponse {

    private Long jugadorId;
    private String jugador;
    private String estado;

    public EntrenamientoAsistenciaResponse() {
    }

    public EntrenamientoAsistenciaResponse(
            Long jugadorId,
            String jugador,
            String estado) {

        this.jugadorId = jugadorId;
        this.jugador = jugador;
        this.estado = estado;
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

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}