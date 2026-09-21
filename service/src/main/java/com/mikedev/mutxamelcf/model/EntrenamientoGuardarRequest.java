package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class EntrenamientoGuardarRequest {

    @NotNull(message = "El equipo es obligatorio")
    private Long equipoId;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    @NotEmpty(message = "Debe existir al menos una asistencia")
    @Valid
    private List<EntrenamientoAsistenciaRequest> asistencias;

    public EntrenamientoGuardarRequest() {
    }

    public EntrenamientoGuardarRequest(
            Long equipoId,
            LocalDate fecha,
            List<EntrenamientoAsistenciaRequest> asistencias) {

        this.equipoId = equipoId;
        this.fecha = fecha;
        this.asistencias = asistencias;
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

    public List<EntrenamientoAsistenciaRequest> getAsistencias() {
        return asistencias;
    }

    public void setAsistencias(
            List<EntrenamientoAsistenciaRequest> asistencias) {

        this.asistencias = asistencias;
    }
}