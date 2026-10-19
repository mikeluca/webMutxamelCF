package com.mikedev.mutxamelcf.model;

/** Una fila de la tabla pública. Solo el nombre del equipo: los escudos de los rivales no son nuestros. */
public record ClasificacionFilaPublicDTO(
        int posicion,
        String equipo,
        boolean esNuestro,
        int puntos,
        int pj,
        int pg,
        int pe,
        int pp,
        int gf,
        int gc) {
}
