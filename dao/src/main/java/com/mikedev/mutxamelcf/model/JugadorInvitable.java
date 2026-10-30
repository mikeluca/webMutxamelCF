package com.mikedev.mutxamelcf.model;

/** Un jugador activo de un equipo, candidato a invitado de otro. */
public record JugadorInvitable(Long id, String nombre, String apellidos, Integer dorsal) {
}
