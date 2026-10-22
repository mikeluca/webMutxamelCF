package com.mikedev.mutxamelcf.model;

/**
 * Minuto de un gol (0-150). En el gol en contra es todo el cuerpo (opcional); en el PATCH que
 * corrige el minuto es obligatorio. El rango lo valida el servicio.
 */
public class MinutoRequest {

    private Integer minuto;

    public MinutoRequest() {
    }

    public Integer getMinuto() {
        return minuto;
    }

    public void setMinuto(Integer minuto) {
        this.minuto = minuto;
    }
}
