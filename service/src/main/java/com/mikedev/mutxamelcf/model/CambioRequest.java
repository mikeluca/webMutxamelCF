package com.mikedev.mutxamelcf.model;

/** Cambio en el directo: sale {@code jugadorSaleId}, entra {@code jugadorEntraId}, en el minuto indicado (0-150). */
public class CambioRequest {

    private Long jugadorSaleId;
    private Long jugadorEntraId;
    private Integer minuto;

    public CambioRequest() {
    }

    public Long getJugadorSaleId() {
        return jugadorSaleId;
    }

    public void setJugadorSaleId(Long jugadorSaleId) {
        this.jugadorSaleId = jugadorSaleId;
    }

    public Long getJugadorEntraId() {
        return jugadorEntraId;
    }

    public void setJugadorEntraId(Long jugadorEntraId) {
        this.jugadorEntraId = jugadorEntraId;
    }

    public Integer getMinuto() {
        return minuto;
    }

    public void setMinuto(Integer minuto) {
        this.minuto = minuto;
    }
}
