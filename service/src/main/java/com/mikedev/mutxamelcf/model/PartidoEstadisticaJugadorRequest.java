package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PartidoEstadisticaJugadorRequest {

    @NotNull(message = "El jugador es obligatorio")
    private Long jugadorId;

    @NotNull(message = "Los goles son obligatorios")
    @Min(value = 0, message = "Los goles no pueden ser negativos")
    private Integer goles;

    @NotNull(message = "Las asistencias son obligatorias")
    @Min(value = 0, message = "Las asistencias no pueden ser negativas")
    private Integer asistencias;

    @NotNull(message = "Las tarjetas amarillas son obligatorias")
    @Min(value = 0, message = "Las tarjetas amarillas no pueden ser negativas")
    private Integer tarjetasAmarillas;

    private boolean tarjetaRoja;

    public PartidoEstadisticaJugadorRequest() {
    }

    public PartidoEstadisticaJugadorRequest(
            Long jugadorId,
            Integer goles,
            Integer asistencias,
            Integer tarjetasAmarillas,
            boolean tarjetaRoja) {

        this.jugadorId = jugadorId;
        this.goles = goles;
        this.asistencias = asistencias;
        this.tarjetasAmarillas = tarjetasAmarillas;
        this.tarjetaRoja = tarjetaRoja;
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }

    public Integer getGoles() {
        return goles;
    }

    public void setGoles(Integer goles) {
        this.goles = goles;
    }

    public Integer getAsistencias() {
        return asistencias;
    }

    public void setAsistencias(Integer asistencias) {
        this.asistencias = asistencias;
    }

    public Integer getTarjetasAmarillas() {
        return tarjetasAmarillas;
    }

    public void setTarjetasAmarillas(Integer tarjetasAmarillas) {
        this.tarjetasAmarillas = tarjetasAmarillas;
    }

    public boolean isTarjetaRoja() {
        return tarjetaRoja;
    }

    public void setTarjetaRoja(boolean tarjetaRoja) {
        this.tarjetaRoja = tarjetaRoja;
    }
}
