package com.mikedev.mutxamelcf.model;

public class PartidoEstadisticaJugador {

    private Long id;
    private Long partidoId;
    private Long jugadorId;
    private Integer goles;
    private Integer asistencias;
    private Integer tarjetasAmarillas;
    private boolean tarjetaRoja;

    public PartidoEstadisticaJugador() {
    }

    public PartidoEstadisticaJugador(
            Long id,
            Long partidoId,
            Long jugadorId,
            Integer goles,
            Integer asistencias,
            Integer tarjetasAmarillas,
            boolean tarjetaRoja) {

        this.id = id;
        this.partidoId = partidoId;
        this.jugadorId = jugadorId;
        this.goles = goles;
        this.asistencias = asistencias;
        this.tarjetasAmarillas = tarjetasAmarillas;
        this.tarjetaRoja = tarjetaRoja;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPartidoId() {
        return partidoId;
    }

    public void setPartidoId(Long partidoId) {
        this.partidoId = partidoId;
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
