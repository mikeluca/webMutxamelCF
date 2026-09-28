package com.mikedev.mutxamelcf.model;

import java.sql.Timestamp;

public class HorarioEntrenamiento {

    private Long id;
    private Long equipoId;
    private Integer diaSemana;
    private String hora;
    private String lugar;
    private boolean activo;
    private Long usuarioActualizoId;
    private Timestamp fechaActualizacion;

    public HorarioEntrenamiento() {
        super();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEquipoId() {
        return equipoId;
    }

    public void setEquipoId(Long equipoId) {
        this.equipoId = equipoId;
    }

    public Integer getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(Integer diaSemana) {
        this.diaSemana = diaSemana;
    }

    public String getHora() {
        return hora;
    }

    public void setHora(String hora) {
        this.hora = hora;
    }

    public String getLugar() {
        return lugar;
    }

    public void setLugar(String lugar) {
        this.lugar = lugar;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public Long getUsuarioActualizoId() {
        return usuarioActualizoId;
    }

    public void setUsuarioActualizoId(Long usuarioActualizoId) {
        this.usuarioActualizoId = usuarioActualizoId;
    }

    public Timestamp getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(Timestamp fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
