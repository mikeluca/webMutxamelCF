package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.NotBlank;

public class DispositivoAppRequest {

    @NotBlank(message = "El token FCM es obligatorio")
    private String tokenFcm;

    @NotBlank(message = "La plataforma es obligatoria")
    private String plataforma;

    public String getTokenFcm() {
        return tokenFcm;
    }

    public void setTokenFcm(String tokenFcm) {
        this.tokenFcm = tokenFcm;
    }

    public String getPlataforma() {
        return plataforma;
    }

    public void setPlataforma(String plataforma) {
        this.plataforma = plataforma;
    }
}