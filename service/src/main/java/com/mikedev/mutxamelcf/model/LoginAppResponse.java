package com.mikedev.mutxamelcf.model;

import java.util.List;

public class LoginAppResponse {

    private String token;
    private int usuarioId;
    private String email;
    private List<String> roles;

    public LoginAppResponse() {
    }

    public LoginAppResponse(
            String token,
            int usuarioId,
            String email,
            List<String> roles) {

        this.token = token;
        this.usuarioId = usuarioId;
        this.email = email;
        this.roles = roles;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(int usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}