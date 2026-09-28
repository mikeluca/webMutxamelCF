package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

public class JustificacionFaltaResponse {

    private Long id;
    private Long sesionId;
    private Long jugadorId;
    private String jugador;
    private Long usuarioAppId;
    private String motivo;
    private LocalDateTime fechaCreacion;

    public JustificacionFaltaResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSesionId() {
        return sesionId;
    }

    public void setSesionId(Long sesionId) {
        this.sesionId = sesionId;
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }

    public String getJugador() {
        return jugador;
    }

    public void setJugador(String jugador) {
        this.jugador = jugador;
    }

    public Long getUsuarioAppId() {
        return usuarioAppId;
    }

    public void setUsuarioAppId(Long usuarioAppId) {
        this.usuarioAppId = usuarioAppId;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
