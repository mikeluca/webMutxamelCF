package com.mikedev.mutxamelcf.model;

/**
 * Una fila de la tabla pública.
 *
 * @param escudoUrl ruta relativa a la URL base del escudo del club ({@code /api/public/escudos-rivales/{id}?v=N}); null si
 *                  no tiene y siempre en la fila nuestra y en las de nuestro club (la app pone nuestro escudo con
 *                  {@code esNuestro})
 */
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
        int gc,
        String escudoUrl) {
}
