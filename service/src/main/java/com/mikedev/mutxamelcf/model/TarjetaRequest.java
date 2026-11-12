package com.mikedev.mutxamelcf.model;

/** Tarjeta en el directo: {@code color} es AMARILLA o ROJA; el minuto (0-150) es opcional. */
public class TarjetaRequest {

    private Long jugadorId;
    private String color;
    private Integer minuto;

    public TarjetaRequest() {
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getMinuto() {
        return minuto;
    }

    public void setMinuto(Integer minuto) {
        this.minuto = minuto;
    }
}
