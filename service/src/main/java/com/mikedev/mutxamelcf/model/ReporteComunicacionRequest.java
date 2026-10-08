package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.Size;

public class ReporteComunicacionRequest {

    @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
    private String motivo;

    public ReporteComunicacionRequest() {
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
