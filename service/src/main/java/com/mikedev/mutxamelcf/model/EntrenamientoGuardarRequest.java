package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.util.List;

public class EntrenamientoGuardarRequest {

    private Long equipoId;
    private LocalDate fecha;
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