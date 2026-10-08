package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.NotBlank;

public class EliminarCuentaAppRequest {

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    public EliminarCuentaAppRequest() {
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
