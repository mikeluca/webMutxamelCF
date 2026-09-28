package com.mikedev.mutxamelcf.model;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PartidoEstadisticasGuardarRequest {

    @NotNull(message = "Los goles a favor son obligatorios")
    @Min(value = 0, message = "Los goles a favor no pueden ser negativos")
    private Integer golesFavor;

    @NotNull(message = "Los goles en contra son obligatorios")
    @Min(value = 0, message = "Los goles en contra no pueden ser negativos")
    private Integer golesContra;

    @NotNull(message = "Debe indicarse la estadística de los jugadores")
    @Valid
    private List<PartidoEstadisticaJugadorRequest> jugadores;

    public PartidoEstadisticasGuardarRequest() {
    }

    public PartidoEstadisticasGuardarRequest(
            Integer golesFavor,
            Integer golesContra,
            List<PartidoEstadisticaJugadorRequest> jugadores) {

        this.golesFavor = golesFavor;
        this.golesContra = golesContra;
        this.jugadores = jugadores;
    }

    public Integer getGolesFavor() {
        return golesFavor;
    }

    public void setGolesFavor(Integer golesFavor) {
        this.golesFavor = golesFavor;
    }

    public Integer getGolesContra() {
        return golesContra;
    }

    public void setGolesContra(Integer golesContra) {
        this.golesContra = golesContra;
    }

    public List<PartidoEstadisticaJugadorRequest> getJugadores() {
        return jugadores;
    }

    public void setJugadores(List<PartidoEstadisticaJugadorRequest> jugadores) {
        this.jugadores = jugadores;
    }
}
