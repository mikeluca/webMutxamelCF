package com.mikedev.mutxamelcf.model;

/** Lo mínimo de un jugador para elegirlo (sin foto): id, dorsal y nombre. */
public record JugadorBasico(Long id, Integer dorsal, String nombre, String apellidos) {

    public String nombreCompleto() {
        return ((nombre == null ? "" : nombre) + " " + (apellidos == null ? "" : apellidos)).trim();
    }
}
