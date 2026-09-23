package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Texto libre del once inicial y los suplentes, tal cual los escribe
 * quien retransmite el partido, para el aviso de "Alineación".
 */
public class AlineacionRequest {

    @NotBlank(message = "El once inicial es obligatorio")
    private String onceInicial;

    @NotBlank(message = "Los suplentes son obligatorios")
    private String suplentes;

    public AlineacionRequest() {
    }

    public String getOnceInicial() {
        return onceInicial;
    }

    public void setOnceInicial(String onceInicial) {
        this.onceInicial = onceInicial;
    }

    public String getSuplentes() {
        return suplentes;
    }

    public void setSuplentes(String suplentes) {
        this.suplentes = suplentes;
    }
}
