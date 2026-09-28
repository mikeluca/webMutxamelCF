package com.mikedev.mutxamelcf.model;

public class PartidoEstadisticaJugadorResponse {

    private Long jugadorId;
    private String jugador;
    private Integer goles;
    private Integer asistencias;
    private Integer tarjetasAmarillas;
    private boolean tarjetaRoja;

    public PartidoEstadisticaJugadorResponse() {
    }

    public PartidoEstadisticaJugadorResponse(
            Long jugadorId,
            String jugador,
            Integer goles,
            Integer asistencias,
            Integer tarjetasAmarillas,
            boolean tarjetaRoja) {

        this.jugadorId = jugadorId;
        this.jugador = jugador;
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

    public String getJugador() {
        return jugador;
    }

    public void setJugador(String jugador) {
        this.jugador = jugador;
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
