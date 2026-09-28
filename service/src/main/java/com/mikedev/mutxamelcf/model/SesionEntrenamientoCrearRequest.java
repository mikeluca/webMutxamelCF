package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Alta de una sesión de entrenamiento suelta, no ligada a ningún
 * horario recurrente (HORARIO_ID queda NULL).
 */
public class SesionEntrenamientoCrearRequest {

    @NotNull(message = "El equipo es obligatorio")
    private Long equipoId;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    private String hora;

    private String lugar;

    public SesionEntrenamientoCrearRequest() {
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
}
