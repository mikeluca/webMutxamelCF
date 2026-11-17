package com.mikedev.mutxamelcf.model;

/** El dorsal ya lo tiene otro jugador activo del mismo equipo. El mensaje se muestra tal cual a quien edita. */
public class DorsalRepetidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DorsalRepetidoException(String message) {
        super(message);
    }
}
