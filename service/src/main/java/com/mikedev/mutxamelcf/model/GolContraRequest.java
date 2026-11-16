package com.mikedev.mutxamelcf.model;

/**
 * Gol del rival: {@code minuto} (0-150) y {@code dorsal} (0-99) del autor son opcionales; el rango lo valida el
 * servicio.
 */
public class GolContraRequest {

    private Integer minuto;
    private Integer dorsal;

    public GolContraRequest() {
    }

    public Integer getMinuto() {
        return minuto;
    }

    public void setMinuto(Integer minuto) {
        this.minuto = minuto;
    }

    public Integer getDorsal() {
        return dorsal;
    }

    public void setDorsal(Integer dorsal) {
        this.dorsal = dorsal;
    }
}
