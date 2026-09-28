package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

public class SesionEntrenamientoResponse {

    private Long id;
    private Long equipoId;
    private String equipo;
    private Long horarioId;
    private LocalDate fecha;
    private String hora;
    private String lugar;
    private String estado;

    /*
     * Solo se rellenan cuando la consulta se hizo indicando un
     * jugadorId concreto (vista jugador/familiar): indican si ESE
     * jugador ya ha justificado su falta a esta sesión. En el resto de
     * casos (vista de gestión del entrenador) quedan a null/false.
     */
    private boolean justificado;
    private String motivoJustificacion;

    public SesionEntrenamientoResponse() {
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

    public String getEquipo() {
        return equipo;
    }

    public void setEquipo(String equipo) {
        this.equipo = equipo;
    }

    public Long getHorarioId() {
        return horarioId;
    }

    public void setHorarioId(Long horarioId) {
        this.horarioId = horarioId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
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

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public boolean isJustificado() {
        return justificado;
    }

    public void setJustificado(boolean justificado) {
        this.justificado = justificado;
    }

    public String getMotivoJustificacion() {
        return motivoJustificacion;
    }

    public void setMotivoJustificacion(String motivoJustificacion) {
        this.motivoJustificacion = motivoJustificacion;
    }
}
