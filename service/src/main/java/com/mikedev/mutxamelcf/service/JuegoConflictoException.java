package com.mikedev.mutxamelcf.service;

/** Conflicto (409) del juego con un código para que la app sepa qué pedir: ALIAS_REQUERIDO o ALIAS_EN_USO. */
public class JuegoConflictoException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public static final String ALIAS_REQUERIDO = "ALIAS_REQUERIDO";
    public static final String ALIAS_EN_USO = "ALIAS_EN_USO";

    private final String codigo;

    public JuegoConflictoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
