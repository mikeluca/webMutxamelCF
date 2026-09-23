package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Entrenamiento {

    private Long id;
    private Long equipoId;
    private LocalDate fecha;
    private Long usuarioEntrenadorId;
    private LocalDateTime fechaCreacion;

    public Entrenamiento() {
    }

    public Entrenamiento(
            Long id,
            Long equipoId,
            LocalDate fecha,
            Long usuarioEntrenadorId,
            LocalDateTime fechaCreacion) {
        this.id = id;
        this.equipoId = equipoId;
        this.fecha = fecha;
        this.usuarioEntrenadorId = usuarioEntrenadorId;
        this.fechaCreacion = fechaCreacion;
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

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public Long getUsuarioEntrenadorId() {
        return usuarioEntrenadorId;
    }

    public void setUsuarioEntrenadorId(Long usuarioEntrenadorId) {
        this.usuarioEntrenadorId = usuarioEntrenadorId;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}