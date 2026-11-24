package com.mikedev.mutxamelcf.service;

/** Demasiados envíos de puntuación de una cuenta (429). */
public class JuegoLimiteEnviosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JuegoLimiteEnviosException(String mensaje) {
        super(mensaje);
    }
}
