package com.mikedev.mutxamelcf.model;

public class EntrenamientoAsistencia {

    private Long id;
    private Long entrenamientoId;
    private Long jugadorId;
    private String estado;

    public EntrenamientoAsistencia() {
    }

    public EntrenamientoAsistencia(
            Long id,
            Long entrenamientoId,
            Long jugadorId,
            String estado) {
        this.id = id;
        this.entrenamientoId = entrenamientoId;
        this.jugadorId = jugadorId;
        this.estado = estado;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEntrenamientoId() {
        return entrenamientoId;
    }

    public void setEntrenamientoId(Long entrenamientoId) {
        this.entrenamientoId = entrenamientoId;
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}