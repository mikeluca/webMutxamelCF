package com.mikedev.mutxamelcf.model;

/**
 * Un jugador activo reducido a lo que hace falta para buscarlo al revisar una solicitud de familia (sin foto, año de
 * nacimiento ni dorsal). La categoría es la del equipo (EQUIPO.CATEGORIA) y, si no tiene equipo, la de la ficha.
 */
public record JugadorCandidato(Long id, String nombre, String apellidos, Long equipoId, String equipo,
        String categoria) {
}
