package com.mikedev.mutxamelcf.model;

import java.util.List;

public class UsuarioAppMeResponse {

    private int usuarioId;
    private String email;
    private List<String> roles;

    public UsuarioAppMeResponse() {
    }

    public UsuarioAppMeResponse(
            int usuarioId,
            String email,
            List<String> roles) {

        this.usuarioId = usuarioId;
        this.email = email;
        this.roles = roles;
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