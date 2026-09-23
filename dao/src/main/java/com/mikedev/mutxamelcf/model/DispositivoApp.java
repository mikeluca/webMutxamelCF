package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

public class DispositivoApp {

    private Long id;
    private Long usuarioAppId;
    private String tokenFcm;
    private String plataforma;
    private Integer activo;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaUltimoAcceso;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUsuarioAppId() {
        return usuarioAppId;
    }

    public void setUsuarioAppId(Long usuarioAppId) {
        this.usuarioAppId = usuarioAppId;
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

    public Integer getActivo() {
        return activo;
    }

    public void setActivo(Integer activo) {
        this.activo = activo;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public LocalDateTime getFechaUltimoAcceso() {
        return fechaUltimoAcceso;
    }

    public void setFechaUltimoAcceso(LocalDateTime fechaUltimoAcceso) {
        this.fechaUltimoAcceso = fechaUltimoAcceso;
    }
}