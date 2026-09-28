package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.NotBlank;

public class SesionCancelarRequest {

    @NotBlank(message = "El motivo de la cancelación es obligatorio")
    private String motivo;

    public SesionCancelarRequest() {
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
