package com.mikedev.mutxamelcf.model;

/** Un equipo del que el equipo de un partido puede invitar jugadores. */
public record EquipoInvitableResponse(Long equipoId, String nombre, String categoria, String grupo) {
}
