package com.mikedev.mutxamelcf.model;

public class NotificacionesNoLeidasResponse {

    private int cantidad;

    public NotificacionesNoLeidasResponse() {
    }

    public NotificacionesNoLeidasResponse(int cantidad) {
        this.cantidad = cantidad;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }
}