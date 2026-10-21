package com.mikedev.mutxamelcf.util;

/**
 * Reglas de la contraseña de la app, las mismas al activar la cuenta y al
 * restablecerla. Los DTO de entrada llevan además @Size con este mínimo.
 */
public final class ValidadorPassword {

    public static final int LONGITUD_MINIMA = 8;

    private ValidadorPassword() {
    }

    /** IllegalArgumentException si la contraseña no cumple las reglas. */
    public static void validar(String password) {

        if (password == null || password.length() < LONGITUD_MINIMA) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres");
        }
    }
}
