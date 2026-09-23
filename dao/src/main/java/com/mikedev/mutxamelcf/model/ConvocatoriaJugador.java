package com.mikedev.mutxamelcf.model;

public class ConvocatoriaJugador {

    private Long id;
    private Long convocatoriaId;
    private Long jugadorId;

    public ConvocatoriaJugador() {
    }

    public ConvocatoriaJugador(
            Long id,
            Long convocatoriaId,
            Long jugadorId) {
        this.id = id;
        this.convocatoriaId = convocatoriaId;
        this.jugadorId = jugadorId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConvocatoriaId() {
        return convocatoriaId;
    }

    public void setConvocatoriaId(Long convocatoriaId) {
        this.convocatoriaId = convocatoriaId;
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }
}