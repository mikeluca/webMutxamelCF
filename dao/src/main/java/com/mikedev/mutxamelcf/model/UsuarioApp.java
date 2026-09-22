package com.mikedev.mutxamelcf.model;

import java.sql.Timestamp;

public class UsuarioApp {

    private int id;

    private String email;

    private String passwordHash;

    private boolean activo;

    private Timestamp fechaAlta;

    private Timestamp fechaActivacion;

    private Timestamp fechaUltimoAcceso;

    private String tokenActivacion;

    private Timestamp fechaExpiracionToken;

    private int intentosActivacion;

    public UsuarioApp() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public Timestamp getFechaAlta() {
        return fechaAlta;
    }

    public void setFechaAlta(Timestamp fechaAlta) {
        this.fechaAlta = fechaAlta;
    }

    public Timestamp getFechaActivacion() {
        return fechaActivacion;
    }

    public void setFechaActivacion(Timestamp fechaActivacion) {
        this.fechaActivacion = fechaActivacion;
    }

    public Timestamp getFechaUltimoAcceso() {
        return fechaUltimoAcceso;
    }

    public void setFechaUltimoAcceso(Timestamp fechaUltimoAcceso) {
        this.fechaUltimoAcceso = fechaUltimoAcceso;
    }

    public String getTokenActivacion() {
        return tokenActivacion;
    }

    public void setTokenActivacion(String tokenActivacion) {
        this.tokenActivacion = tokenActivacion;
    }

    public Timestamp getFechaExpiracionToken() {
        return fechaExpiracionToken;
    }

    public void setFechaExpiracionToken(Timestamp fechaExpiracionToken) {
        this.fechaExpiracionToken = fechaExpiracionToken;
    }

    public int getIntentosActivacion() {
        return intentosActivacion;
    }

    public void setIntentosActivacion(int intentosActivacion) {
        this.intentosActivacion = intentosActivacion;
    }
}