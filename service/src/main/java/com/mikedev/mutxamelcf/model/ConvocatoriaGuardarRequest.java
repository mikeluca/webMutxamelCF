package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class ConvocatoriaGuardarRequest {

    @NotNull(message = "El equipo es obligatorio")
    private Long equipoId;

    @NotBlank(message = "El rival es obligatorio")
    private String rival;

    private String campo;

    @NotNull(message = "La fecha del partido es obligatoria")
    private LocalDate fechaPartido;

    @NotBlank(message = "La hora del partido es obligatoria")
    private String horaPartido;

    @NotBlank(message = "La hora de convocatoria es obligatoria")
    private String horaConvocatoria;

    @NotBlank(message = "El lugar de convocatoria es obligatorio")
    private String lugarConvocatoria;

    @NotEmpty(message = "Debes seleccionar al menos un jugador")
    private List<Long> jugadoresIds;

    public ConvocatoriaGuardarRequest() {
    }

    public Long getEquipoId() {
        return equipoId;
    }

    public void setEquipoId(Long equipoId) {
        this.equipoId = equipoId;
    }

    public String getRival() {
        return rival;
    }

    public void setRival(String rival) {
        this.rival = rival;
    }

    public String getCampo() {
        return campo;
    }

    public void setCampo(String campo) {
        this.campo = campo;
    }

    public LocalDate getFechaPartido() {
        return fechaPartido;
    }

    public void setFechaPartido(LocalDate fechaPartido) {
        this.fechaPartido = fechaPartido;
    }

    public String getHoraPartido() {
        return horaPartido;
    }

    public void setHoraPartido(String horaPartido) {
        this.horaPartido = horaPartido;
    }

    public String getHoraConvocatoria() {
        return horaConvocatoria;
    }

    public void setHoraConvocatoria(String horaConvocatoria) {
        this.horaConvocatoria = horaConvocatoria;
    }

    public String getLugarConvocatoria() {
        return lugarConvocatoria;
    }

    public void setLugarConvocatoria(String lugarConvocatoria) {
        this.lugarConvocatoria = lugarConvocatoria;
    }

    public List<Long> getJugadoresIds() {
        return jugadoresIds;
    }

    public void setJugadoresIds(List<Long> jugadoresIds) {
        this.jugadoresIds = jugadoresIds;
    }
}