package com.mikedev.mutxamelcf.model;

/**
 * Un jugador vinculado a un familiar, con su equipo y categoría actuales (el listado de familiares del panel). Un
 * jugador de baja no tiene equipo ni categoría.
 */
public record JugadorDeFamiliar(Long familiarId, Long jugadorId, String nombre, String apellidos, String equipo,
        String categoria, boolean deBaja) {

    public String nombreCompleto() {
        return ((nombre == null ? "" : nombre) + " " + (apellidos == null ? "" : apellidos)).trim();
    }
}
