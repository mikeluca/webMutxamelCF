package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class HorarioEntrenamientoCrearRequest {

    @NotNull(message = "El equipo es obligatorio")
    private Long equipoId;

    @NotNull(message = "El día de la semana es obligatorio")
    @Min(value = 1, message = "El día de la semana debe estar entre 1 (lunes) y 7 (domingo)")
    @Max(value = 7, message = "El día de la semana debe estar entre 1 (lunes) y 7 (domingo)")
    private Integer diaSemana;

    @NotBlank(message = "La hora es obligatoria")
    private String hora;

    private String lugar;

    public HorarioEntrenamientoCrearRequest() {
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
}
