package com.mikedev.mutxamelcf.model;

/**
 * Resultado interno de crear/reenviar una invitación de la app móvil.
 *
 * Lleva el token de activación EN CLARO: solo debe usarse en el mismo
 * request, en el módulo mvc, para enviar el email de invitación. Nunca
 * debe serializarse ni devolverse en una respuesta HTTP al cliente.
 */
public class InvitacionUsuarioApp {

    private int usuarioAppId;
    private String email;
    private String nombrePersona;
    private String tokenActivacion;

    public InvitacionUsuarioApp() {
    }

    public InvitacionUsuarioApp(
            int usuarioAppId,
            String email,
            String nombrePersona,
            String tokenActivacion) {

        this.usuarioAppId = usuarioAppId;
        this.email = email;
        this.nombrePersona = nombrePersona;
        this.tokenActivacion = tokenActivacion;
    }

    public int getUsuarioAppId() {
        return usuarioAppId;
    }

    public void setUsuarioAppId(int usuarioAppId) {
        this.usuarioAppId = usuarioAppId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNombrePersona() {
        return nombrePersona;
    }

    public void setNombrePersona(String nombrePersona) {
        this.nombrePersona = nombrePersona;
    }

    public String getTokenActivacion() {
        return tokenActivacion;
    }

    public void setTokenActivacion(String tokenActivacion) {
        this.tokenActivacion = tokenActivacion;
    }
}
