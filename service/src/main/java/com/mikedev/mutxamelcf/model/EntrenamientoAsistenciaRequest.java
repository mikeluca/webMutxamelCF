package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class EntrenamientoAsistenciaRequest {

    @NotNull(message = "El jugador es obligatorio")
    private Long jugadorId;

    @NotBlank(message = "El estado es obligatorio")
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