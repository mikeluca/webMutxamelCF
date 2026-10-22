package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DispositivoAppRequest {

    @NotBlank(message = "El token FCM es obligatorio")
    private String tokenFcm;

    @NotBlank(message = "La plataforma es obligatoria")
    private String plataforma;

    /** Versión instalada de la app (p. ej. "1.0.10"); opcional: las apps antiguas no la envían. */
    @Size(max = 20, message = "La versión de la app no puede superar los 20 caracteres")
    private String versionApp;

    public String getVersionApp() {
        return versionApp;
    }

    public void setVersionApp(String versionApp) {
        this.versionApp = versionApp;
    }

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