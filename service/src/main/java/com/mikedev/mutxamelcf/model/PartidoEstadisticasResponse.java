package com.mikedev.mutxamelcf.model;

import java.util.List;

public class PartidoEstadisticasResponse {

    private Long partidoId;
    private Integer golesFavor;
    private Integer golesContra;
    private String resultado;
    private List<PartidoEstadisticaJugadorResponse> jugadores;
    private boolean admiteEstadisticas;

    public PartidoEstadisticasResponse() {
    }

    public PartidoEstadisticasResponse(
            Long partidoId,
            Integer golesFavor,
            Integer golesContra,
            String resultado,
            List<PartidoEstadisticaJugadorResponse> jugadores,
            boolean admiteEstadisticas) {

        this.partidoId = partidoId;
        this.golesFavor = golesFavor;
        this.golesContra = golesContra;
        this.resultado = resultado;
        this.jugadores = jugadores;
        this.admiteEstadisticas = admiteEstadisticas;
    }

    public Long getPartidoId() {
        return partidoId;
    }

    public void setPartidoId(Long partidoId) {
        this.partidoId = partidoId;
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

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
    }

    public List<PartidoEstadisticaJugadorResponse> getJugadores() {
        return jugadores;
    }

    public void setJugadores(List<PartidoEstadisticaJugadorResponse> jugadores) {
        this.jugadores = jugadores;
    }

    public boolean isAdmiteEstadisticas() {
        return admiteEstadisticas;
    }

    public void setAdmiteEstadisticas(boolean admiteEstadisticas) {
        this.admiteEstadisticas = admiteEstadisticas;
    }
}
