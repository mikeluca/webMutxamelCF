package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class EntrenamientoResponse {

    private Long id;
    private Long equipoId;
    private String equipo;
    private LocalDate fecha;
    private Long usuarioEntrenadorId;
    private List<EntrenamientoAsistenciaResponse> asistencias;

    /** El staff ya ha guardado la asistencia de este entrenamiento, y cuándo. */
    private boolean listaRevisada;
    private LocalDateTime fechaRevision;

    public boolean isListaRevisada() {
        return listaRevisada;
    }

    public void setListaRevisada(boolean listaRevisada) {
        this.listaRevisada = listaRevisada;
    }

    public LocalDateTime getFechaRevision() {
        return fechaRevision;
    }

    public void setFechaRevision(LocalDateTime fechaRevision) {
        this.fechaRevision = fechaRevision;
    }

    public EntrenamientoResponse() {
    }

    public EntrenamientoResponse(
            Long id,
            Long equipoId,
            String equipo,
            LocalDate fecha,
            Long usuarioEntrenadorId,
            List<EntrenamientoAsistenciaResponse> asistencias) {

        this.id = id;
        this.equipoId = equipoId;
        this.equipo = equipo;
        this.fecha = fecha;
        this.usuarioEntrenadorId = usuarioEntrenadorId;
        this.asistencias = asistencias;
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

    public List<EntrenamientoAsistenciaResponse> getAsistencias() {
        return asistencias;
    }

    public void setAsistencias(
            List<EntrenamientoAsistenciaResponse> asistencias) {

        this.asistencias = asistencias;
    }
}