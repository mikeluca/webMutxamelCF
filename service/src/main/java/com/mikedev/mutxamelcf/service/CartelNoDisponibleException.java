package com.mikedev.mutxamelcf.service;

/** El cartel del próximo partido no se puede generar: no hay partido o ya ha empezado (el motivo es el código). */
public class CartelNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String motivo;

    public CartelNoDisponibleException(String motivo) {
        super("El cartel no está disponible: " + motivo);
        this.motivo = motivo;
    }

    /** SIN_PARTIDO o YA_EMPEZADO. */
    public String motivo() {
        return motivo;
    }
}
