package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PartidoGuardarRequest {

    @NotNull(message = "El equipo es obligatorio")
    private Long equipoId;

    @NotBlank(message = "El rival es obligatorio")
    private String rival;

    private LocalDate dia;

    private String hora;

    private String campo;

    private String resultado;

    public PartidoGuardarRequest() {
    }

    public PartidoGuardarRequest(
            Long equipoId,
            String rival,
            LocalDate dia,
            String hora,
            String campo,
            String resultado) {

        this.equipoId = equipoId;
        this.rival = rival;
        this.dia = dia;
        this.hora = hora;
        this.campo = campo;
        this.resultado = resultado;
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

    public LocalDate getDia() {
        return dia;
    }

    public void setDia(LocalDate dia) {
        this.dia = dia;
    }

    public String getHora() {
        return hora;
    }

    public void setHora(String hora) {
        this.hora = hora;
    }

    public String getCampo() {
        return campo;
    }

    public void setCampo(String campo) {
        this.campo = campo;
    }

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
    }
}
