package com.mikedev.mutxamelcf.model;

public class EntrenamientoAsistenciaRequest {

    private Long jugadorId;
    private String estado;

    public EntrenamientoAsistenciaRequest() {
    }

    public EntrenamientoAsistenciaRequest(Long jugadorId, String estado) {
        this.jugadorId = jugadorId;
        this.estado = estado;
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}