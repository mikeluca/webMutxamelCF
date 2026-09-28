package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.NotNull;

public class JustificacionFaltaRequest {

    @NotNull(message = "El jugador es obligatorio")
    private Long jugadorId;

    private String motivo;

    public JustificacionFaltaRequest() {
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
