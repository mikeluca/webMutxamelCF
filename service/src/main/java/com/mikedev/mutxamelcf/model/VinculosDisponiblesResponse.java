package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Lo que se puede elegir al invitar a alguien desde la app: las personas del club que
 * todavía no tienen cuenta y los roles que no salen de un vínculo.
 */
public record VinculosDisponiblesResponse(
        List<Jugador> jugadores,
        List<Familiar> familiares,
        List<CuerpoTecnico> cuerpoTecnico,
        List<Rol> rolesAdicionales) {

    public record Jugador(Long id, String nombre, String equipo) {
    }

    /** jugadores: los nombres de los jugadores de los que es familiar. */
    public record Familiar(Long id, String nombre, List<String> jugadores) {
    }

    public record CuerpoTecnico(Long id, String nombre, String equipo) {
    }

    public record Rol(String codigo, String nombre) {
    }
}
