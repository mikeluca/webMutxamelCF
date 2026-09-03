package com.mikedev.mutxamelcf.model;

public class ActivarCuentaAppRequest {

    private String token;
    private String password;

    public ActivarCuentaAppRequest() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}